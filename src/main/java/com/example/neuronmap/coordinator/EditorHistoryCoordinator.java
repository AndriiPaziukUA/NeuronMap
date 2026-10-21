package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.scene.Cursor;

import java.util.Objects;
import java.util.function.Consumer;

/** Пов’язує undo/redo зі скиданням тимчасових станів і синхронізацією подань редактора. */
public final class EditorHistoryCoordinator {

    private final HistoryService historyService;
    private final SimulationController simulationController;
    private final ConnectionController connectionController;
    private final NeuronInteractionController neuronController;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable synchronizeNeuronToolPlacement;
    private final MapPresentationCoordinator presentation;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;

    /**
     * Створює координатор історії та зберігає залежності, потрібні для узгодження undo/redo з редактором.
     *
     * @param historyService служба скасування й повторення змін.
     * @param simulationController контролер симуляції.
     * @param connectionController контролер зв'язків.
     * @param neuronController контролер взаємодії з нейронами.
     * @param state стан редактора.
     * @param workspace полотно редактора.
     * @param synchronizeNeuronToolPlacement узгодження активного інструмента з поточним режимом редактора.
     * @param presentation координатор візуальних подань.
     * @param save дія збереження стану редактора.
     * @param status дія показу повідомлень стану.
     * @param localization служба локалізації інтерфейсу.
     */
    public EditorHistoryCoordinator(
            HistoryService historyService,
            SimulationController simulationController,
            ConnectionController connectionController,
            NeuronInteractionController neuronController,
            EditorState state,
            WorkspaceView workspace,
            Runnable synchronizeNeuronToolPlacement,
            MapPresentationCoordinator presentation,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.historyService = Objects.requireNonNull(historyService, "historyService");
        this.simulationController = Objects.requireNonNull(simulationController, "simulationController");
        this.connectionController = Objects.requireNonNull(connectionController, "connectionController");
        this.neuronController = Objects.requireNonNull(neuronController, "neuronController");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.synchronizeNeuronToolPlacement = Objects.requireNonNull(
                synchronizeNeuronToolPlacement, "synchronizeNeuronToolPlacement");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    /** Скасовує останню зміну карти й оновлює залежні частини інтерфейсу. */
    public boolean undo() {
        simulationController.stop();
        if (!historyService.undo()) {
            return false;
        }
        resetEditorAfterHistoryChange();
        presentation.synchronizeViewsWithModel();
        synchronizeNeuronToolPlacement.run();
        save.run();
        status.accept(localization.text("status.undo"));
        return true;
    }

    /** Повторює скасовану зміну карти й оновлює залежні частини інтерфейсу. */
    public boolean redo() {
        simulationController.stop();
        if (!historyService.redo()) {
            return false;
        }
        resetEditorAfterHistoryChange();
        presentation.synchronizeViewsWithModel();
        synchronizeNeuronToolPlacement.run();
        save.run();
        status.accept(localization.text("status.redo"));
        return true;
    }

    /**
     * Скидає тимчасову взаємодію після undo/redo, але не вимикає активний інструмент розміщення нейронів.
     */
    private void resetEditorAfterHistoryChange() {
        boolean keepNeuronPlacement = state.mode() == EditorState.Mode.ADD_NEURON;
        connectionController.cancelCreate();
        if (keepNeuronPlacement) {
            connectionController.clearDeleteHighlights();
        } else {
            connectionController.exitDelete();
            state.resetToIdle();
            workspace.node().setCursor(Cursor.DEFAULT);
        }
        neuronController.hideMenu();
        state.clearSelection();
    }
}
