package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.NeuronContextMenuView;
import com.example.neuronmap.view.NeuronOverlayPositioner;
import com.example.neuronmap.view.NeuronSettingsDialog;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Керує діями контекстного меню нейрона, його видимістю та розташуванням біля нейрона.
 */
public final class NeuronMenuController {

    private final NeuronService neuronService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final ConnectionController connections;
    private final SimulationController simulation;
    private final NeuronDeletionController deletionController;
    private final NeuronMenuCustomizer menuCustomizer;
    private final NeuronContextMenuView menuView;
    private final NeuronOverlayPositioner overlayPositioner;
    private final Runnable refreshVisuals;
    private final Runnable refreshConnections;
    private final Runnable refreshOverlays;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;

    private VBox menu;
    private String activeNeuronId;

    /**
     * Створює контролер контекстного меню з локалізацією українською мовою за замовчуванням.
     *
     * @param neuronService служба операцій над нейронами.
     * @param state поточний стан редактора.
     * @param workspace полотно редактора.
     * @param connections контролер операцій зі зв’язками.
     * @param simulation контролер симуляції нейронів.
     * @param deletionController контролер видалення нейронів.
     * @param menuCustomizer компонент додаткового оформлення кнопок меню.
     * @param refreshVisuals дія оновлення вигляду нейронів.
     * @param refreshConnections дія оновлення вигляду зв’язків.
     * @param refreshOverlays дія оновлення положення накладок.
     * @param save дія збереження поточного стану.
     * @param status дія показу повідомлення в рядку стану.
     */
    public NeuronMenuController(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            ConnectionController connections,
            SimulationController simulation,
            NeuronDeletionController deletionController,
            NeuronMenuCustomizer menuCustomizer,
            Runnable refreshVisuals,
            Runnable refreshConnections,
            Runnable refreshOverlays,
            Runnable save,
            Consumer<String> status
    ) {
        this(
                neuronService,
                state,
                workspace,
                connections,
                simulation,
                deletionController,
                menuCustomizer,
                refreshVisuals,
                refreshConnections,
                refreshOverlays,
                save,
                status,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Створює контролер меню та приймає залежності для обробки дій користувача.
     *
     * @param neuronService служба операцій над нейронами.
     * @param state поточний стан редактора.
     * @param workspace полотно редактора.
     * @param connections контролер операцій зі зв’язками.
     * @param simulation контролер симуляції нейронів.
     * @param deletionController контролер видалення нейронів.
     * @param menuCustomizer компонент додаткового оформлення кнопок меню.
     * @param refreshVisuals дія оновлення вигляду нейронів.
     * @param refreshConnections дія оновлення вигляду зв’язків.
     * @param refreshOverlays дія оновлення положення накладок.
     * @param save дія збереження поточного стану.
     * @param status дія показу повідомлення в рядку стану.
     * @param localization служба локалізації інтерфейсу.
     */
    public NeuronMenuController(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            ConnectionController connections,
            SimulationController simulation,
            NeuronDeletionController deletionController,
            NeuronMenuCustomizer menuCustomizer,
            Runnable refreshVisuals,
            Runnable refreshConnections,
            Runnable refreshOverlays,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.deletionController = Objects.requireNonNull(deletionController, "deletionController");
        this.menuCustomizer = Objects.requireNonNull(menuCustomizer, "menuCustomizer");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.menuView = new NeuronContextMenuView(
                this.localization,
                this.menuCustomizer::customizeMenuButton,
                this.menuCustomizer::createDirectionButton
        );
        this.overlayPositioner = new NeuronOverlayPositioner(workspace);
        this.refreshVisuals = Objects.requireNonNull(refreshVisuals, "refreshVisuals");
        this.refreshConnections = Objects.requireNonNull(refreshConnections, "refreshConnections");
        this.refreshOverlays = Objects.requireNonNull(refreshOverlays, "refreshOverlays");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
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
     *
     * @param neuronView візуальне подання нейрона, для якого відкривається меню.
     */
    public void show(NeuronView neuronView) {
        Objects.requireNonNull(neuronView, "neuronView");
        hide();

        String neuronId = neuronView.model().id();
        state.setSelectedNeuronForMenu(neuronId);
        activeNeuronId = neuronId;

        menu = menuView.createMenu(
                connections.hasConnections(neuronId),
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
                            neuronService.toggleType(neuronId);
                            refreshVisuals.run();
                            refreshConnections.run();
                            refreshOverlays.run();
                            save.run();
                        },
                        () -> deletionController.deleteNeuron(neuronId)
                )
        );

        workspace.overlayLayer().getChildren().add(menu);
        overlayPositioner.position(neuronView, menu, null);
    }

    /**
     * Приховує контекстне меню нейрона й очищує ідентифікатор активного меню.
     */
    public void hide() {
        if (menu != null) {
            workspace.overlayLayer().getChildren().remove(menu);
            menu = null;
        }
        activeNeuronId = null;
        state.setSelectedNeuronForMenu(null);
    }

    /**
     * Перераховує положення меню після зміни позиції нейрона або камери.
     *
     * @param view візуальне подання нейрона, біля якого може бути відкрите меню.
     */
    public void refreshPosition(NeuronView view) {
        if (!isVisible() || activeNeuronId == null || view == null) {
            return;
        }
        if (!activeNeuronId.equals(view.model().id())) {
            return;
        }
        overlayPositioner.position(view, menu, null);
    }

    /**
     * Відкриває налаштування нейрона та зберігає введені значення після підтвердження.
     *
     * @param neuronId ідентифікатор нейрона, налаштування якого потрібно змінити.
     */
    private void showNeuronSettings(String neuronId) {
        var neuron = neuronService.find(neuronId);
        if (neuron == null) {
            return;
        }

        NeuronSettingsDialog.show(
                workspace.node().getScene() == null
                        ? null
                        : workspace.node().getScene().getWindow(),
                neuron,
                localization
        ).ifPresent(values -> {
            neuronService.updateSettings(
                    neuronId,
                    values.signalStrength(),
                    values.activationThreshold()
            );
            save.run();
            status.accept(localization.text("neuron.settings.saved"));
        });
    }
}
