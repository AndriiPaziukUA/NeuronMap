package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.coordinator.NeuronSettingsOverlayCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronContextMenuView;
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronOverlayPositioner;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Керує контекстним меню нейрона та координує відкриття його вікна налаштувань. */
public final class NeuronMenuController {

    private final NeuronService neuronService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final ConnectionController connections;
    private final SimulationController simulation;
    private final NeuronDeletionController deletionController;
    private final NeuronMenuView neuronMenuView;
    private final NeuronOverlayPositioner overlayPositioner;
    private final NeuronSettingsOverlayCoordinator settingsOverlayCoordinator;
    private final Runnable refreshVisuals;
    private final Runnable refreshConnections;
    private final Runnable refreshOverlays;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;
    /** Оновлює відкрите меню після зміни активної локалі та від'єднується під час завершення контролера. */
    private final Consumer<Locale> localizationListener = this::refreshMenuLocalization;

    private VBox menu;
    private String activeNeuronId;
    private NeuronView activeNeuronView;
    private boolean disposed;

    /**
     * Створює контролер меню, використовуючи координатор накладки, створений власником графа залежностей.
     *
     * @param neuronService служба операцій над нейронами.
     * @param state поточний стан редактора.
     * @param workspace полотно редактора.
     * @param connections контролер операцій зі зв'язками.
     * @param simulation контролер симуляції нейронів.
     * @param deletionController контролер видалення нейронів.
     * @param neuronMenuView подання оформлення кнопок контекстного меню.
     * @param refreshVisuals дія оновлення вигляду нейронів.
     * @param refreshConnections дія оновлення вигляду зв'язків.
     * @param refreshOverlays дія оновлення положення накладок.
     * @param save дія збереження поточного стану.
     * @param status дія показу повідомлення в рядку стану.
     * @param localization служба локалізації інтерфейсу.
     * @param settingsOverlayCoordinator координатор життєвого циклу накладки налаштувань.
     * @throws NullPointerException якщо будь-яка обов'язкова залежність відсутня.
     */
    public NeuronMenuController(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            ConnectionController connections,
            SimulationController simulation,
            NeuronDeletionController deletionController,
            NeuronMenuView neuronMenuView,
            Runnable refreshVisuals,
            Runnable refreshConnections,
            Runnable refreshOverlays,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization,
            NeuronSettingsOverlayCoordinator settingsOverlayCoordinator
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.deletionController = Objects.requireNonNull(deletionController, "deletionController");
        this.neuronMenuView = Objects.requireNonNull(neuronMenuView, "neuronMenuView");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.settingsOverlayCoordinator = Objects.requireNonNull(
                settingsOverlayCoordinator,
                "settingsOverlayCoordinator"
        );
        this.overlayPositioner = new NeuronOverlayPositioner(workspace);
        this.refreshVisuals = Objects.requireNonNull(refreshVisuals, "refreshVisuals");
        this.refreshConnections = Objects.requireNonNull(refreshConnections, "refreshConnections");
        this.refreshOverlays = Objects.requireNonNull(refreshOverlays, "refreshOverlays");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.localization.addListener(localizationListener);
    }

    /**
     * Перевіряє, чи видиме контекстне меню нейрона.
     *
     * @return {@code true}, якщо меню відображається; інакше {@code false}.
     */
    public boolean isVisible() {
        return menu != null && menu.isVisible();
    }

    /**
     * Повертає кореневий вузол контекстного меню для розміщення в шарі накладок.
     *
     * @return кореневий вузол меню або {@code null}, якщо меню приховане.
     */
    public VBox node() {
        return menu;
    }

    /**
     * Розміщує й показує контекстне меню біля вказаного нейрона.
     * Для внутрішніх нейронів структури приховує кнопки зміни напрямку, типу та налаштувань.
     *
     * @param neuronView візуальне подання нейрона, для якого відкривається меню.
     */
    public void show(NeuronView neuronView) {
        ensureNotDisposed();
        Objects.requireNonNull(neuronView, "neuronView");
        hide();
        String neuronId = neuronView.model().id();
        state.setSelectedNeuronForMenu(neuronId);
        activeNeuronId = neuronId;
        activeNeuronView = neuronView;

        NeuronContextMenuView menuView = new NeuronContextMenuView(
                localization,
                neuronMenuView::customizeMenuButton,
                neuronMenuView::createDirectionButton
        );
        menu = menuView.createMenu(
                connections.hasConnections(neuronId),
                neuronView.isStructureMember(),
                new NeuronContextMenuView.Actions(
                        () -> simulation.emitPulse(neuronId),
                        () -> {
                            hide();
                            connections.beginCreate(neuronId);
                        },
                        () -> showNeuronSettings(neuronId),
                        () -> {
                            hide();
                            connections.beginDelete(neuronId);
                        },
                        () -> {
                            if (neuronService.toggleType(neuronId)) {
                                refreshViewsAndSave();
                            }
                        },
                        () -> deletionController.deleteNeuron(neuronId)
                )
        );
        workspace.overlayLayer().getChildren().add(menu);
        overlayPositioner.position(neuronView, menu, null);
    }

    /**
     * Змінює напрямок нейрона та узгоджує його подання, зв'язки, накладки й збережений стан.
     * Якщо нейрон уже відсутній у моделі, дія завершується без побічних ефектів.
     *
     * @param neuronId ідентифікатор нейрона, напрямок якого потрібно перемкнути.
     */
    void toggleDirection(String neuronId) {
        ensureNotDisposed();
        if (neuronId == null || !neuronService.toggleDirection(neuronId)) {
            return;
        }
        refreshViewsAndSave();
        status.accept(localization.text("status.direction_changed"));
    }

    /** Оновлює нейрони, залежні подання та накладки, після чого зберігає змінену карту. */
    private void refreshViewsAndSave() {
        refreshVisuals.run();
        refreshConnections.run();
        refreshOverlays.run();
        save.run();
    }

    /** Приховує контекстне меню й очищує тимчасовий стан поточного нейрона. */
    public void hide() {
        settingsOverlayCoordinator.hide();
        if (menu != null) {
            workspace.overlayLayer().getChildren().remove(menu);
            menu = null;
        }
        activeNeuronId = null;
        activeNeuronView = null;
        state.setSelectedNeuronForMenu(null);
    }

    /** Остаточно звільняє слухача локалізації, координатор накладки та відкрите контекстне меню. */
    public void dispose() {
        if (disposed) {
            return;
        }
        localization.removeListener(localizationListener);
        hide();
        settingsOverlayCoordinator.dispose();
        disposed = true;
    }

    /**
     * Забороняє використання контролера після звільнення його ресурсів.
     *
     * @throws IllegalStateException якщо контролер остаточно звільнено.
     */
    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Neuron menu controller is disposed");
        }
    }

    /**
     * Перераховує положення меню після зміни позиції нейрона або камери.
     *
     * @param view візуальне подання нейрона, біля якого може бути відкрите меню.
     */
    public void refreshPosition(NeuronView view) {
        if (disposed || !isVisible() || activeNeuronId == null || view == null) {
            return;
        }
        if (!activeNeuronId.equals(view.model().id())) {
            return;
        }
        overlayPositioner.position(view, menu, null);
    }

    /**
     * Перебудовує відкрите меню після зміни локалі, щоб підказки й доступні назви відповідали новій мові.
     * Приховане меню не чіпає: наступний показ і так створить його з актуальними перекладами.
     *
     * @param ignoredLocale нова локаль, яку застосувала спільна служба локалізації.
     */
    private void refreshMenuLocalization(Locale ignoredLocale) {
        if (disposed || !isVisible() || activeNeuronView == null) {
            return;
        }
        show(activeNeuronView);
    }

    /**
     * Відкриває накладку налаштувань і застосовує параметри та назву після підтвердження.
     * Запам'ятовує подання нейрона, щоб відновити його контекстне меню після звичайного закриття накладки.
     *
     * @param neuronId ідентифікатор нейрона, налаштування якого потрібно змінити.
     */
    private void showNeuronSettings(String neuronId) {
        ensureNotDisposed();
        NeuronView neuronView = activeNeuronView;
        neuronService.find(neuronId).ifPresent(neuron -> {
            hide();
            settingsOverlayCoordinator.showWithName(
                    neuron,
                    (parameters, name) -> {
                        if (neuronService.updateSettings(neuronId, parameters, name)) {
                            save.run();
                            status.accept(localization.text("neuron.settings.saved"));
                        }
                    },
                    () -> restoreMenuAfterSettingsClosed(neuronId, neuronView)
            );
        });
    }

    /**
     * Відновлює контекстне меню лише для нейрона, який досі належить активній карті.
     * Пропускає відновлення після завершення контролера або видалення нейрона.
     *
     * @param neuronId ідентифікатор нейрона, для якого відкривали налаштування.
     * @param neuronView попереднє графічне подання нейрона.
     */
    private void restoreMenuAfterSettingsClosed(String neuronId, NeuronView neuronView) {
        if (disposed
                || neuronView == null
                || !workspace.nodeLayer().getChildren().contains(neuronView)
                || neuronService.find(neuronId).isEmpty()) {
            return;
        }
        show(neuronView);
    }
}
