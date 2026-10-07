package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.Cursor;

import java.util.Objects;
import java.util.function.Consumer;

/** Owns editor undo/redo orchestration and resets transient UI state afterwards. */
public final class EditorHistoryCoordinator {

    private final HistoryService historyService;
    private final SimulationController simulationController;
    private final ConnectionController connectionController;
    private final NeuronInteractionController neuronController;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable removeDragPreview;
    private final MapPresentationCoordinator presentation;
    private final Runnable save;
    private final Consumer<String> status;

    public EditorHistoryCoordinator(
            HistoryService historyService,
            SimulationController simulationController,
            ConnectionController connectionController,
            NeuronInteractionController neuronController,
            EditorState state,
            WorkspaceView workspace,
            Runnable removeDragPreview,
            MapPresentationCoordinator presentation,
            Runnable save,
            Consumer<String> status
    ) {
        this.historyService = Objects.requireNonNull(historyService, "historyService");
        this.simulationController = Objects.requireNonNull(simulationController, "simulationController");
        this.connectionController = Objects.requireNonNull(connectionController, "connectionController");
        this.neuronController = Objects.requireNonNull(neuronController, "neuronController");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.removeDragPreview = Objects.requireNonNull(removeDragPreview, "removeDragPreview");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
    }

    public boolean undo() {
        simulationController.stop();
        if (!historyService.undo()) {
            return false;
        }
        resetEditorAfterHistoryChange();
        presentation.synchronizeViewsWithModel();
        save.run();
        status.accept("Зміни скасовано.");
        return true;
    }

    public boolean redo() {
        simulationController.stop();
        if (!historyService.redo()) {
            return false;
        }
        resetEditorAfterHistoryChange();
        presentation.synchronizeViewsWithModel();
        save.run();
        status.accept("Зміни повторено.");
        return true;
    }

    private void resetEditorAfterHistoryChange() {
        removeDragPreview.run();
        connectionController.cancelCreate();
        connectionController.exitDelete();
        neuronController.hideMenu();
        state.resetToIdle();
        state.clearSelection();
        workspace.node().setCursor(Cursor.DEFAULT);
    }
}
