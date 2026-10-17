package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.neuron.NeuronContextMenuView;
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronOverlayPositioner;
import com.example.neuronmap.view.neuron.NeuronSettingsDialog;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.editor.WorkspaceView;
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
    private final NeuronMenuView neuronMenuView;
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
     * Створює контролер меню та приймає залежності для обробки дій користувача.
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
            LocalizationService localization
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.deletionController = Objects.requireNonNull(deletionController, "deletionController");
        this.neuronMenuView = Objects.requireNonNull(neuronMenuView, "neuronMenuView");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.menuView = new NeuronContextMenuView(
                this.localization,
                this.neuronMenuView::customizeMenuButton,
                this.neuronMenuView::createDirectionButton
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
                            refreshViewsAndSave();
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
        if (neuronId == null || !neuronService.toggleDirection(neuronId)) {
            return;
        }
        refreshViewsAndSave();
        status.accept(localization.text("status.direction_changed"));
    }

    /**
     * Оновлює нейрони, залежні подання та накладки, після чого зберігає змінену карту.
     */
    private void refreshViewsAndSave() {
        refreshVisuals.run();
        refreshConnections.run();
        refreshOverlays.run();
        save.run();
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
     * Відкриває налаштування нейрона та застосовує нові параметри одним узгодженим значенням.
     *
     * @param neuronId ідентифікатор нейрона, налаштування якого потрібно змінити.
     */
    private void showNeuronSettings(String neuronId) {
        neuronService.find(neuronId).ifPresent(neuron ->
                NeuronSettingsDialog.show(
                        workspace.node().getScene() == null
                                ? null
                                : workspace.node().getScene().getWindow(),
                        neuron,
                        localization
                ).ifPresent(parameters -> {
                    neuronService.updateSettings(neuronId, parameters);
                    save.run();
                    status.accept(localization.text("neuron.settings.saved"));
                })
        );
    }
}
