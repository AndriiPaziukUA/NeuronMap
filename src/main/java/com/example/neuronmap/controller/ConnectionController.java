package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Dispatches connection interactions to their dedicated UI controllers. */
public final class ConnectionController {

    private final EditorState state;
    private final WorkspaceView workspace;
    private final ConnectionCreationController creationController;
    private final ConnectionDeletionController deletionController;

    public ConnectionController(
            ConnectionService connectionService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refresh,
            Runnable save,
            Consumer<String> status
    ) {
        Objects.requireNonNull(connectionService, "connectionService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");

        this.deletionController = new ConnectionDeletionController(
                connectionService,
                state,
                workspace,
                neuronViews,
                refresh,
                save,
                status
        );
        this.creationController = new ConnectionCreationController(
                connectionService,
                state,
                workspace,
                neuronViews,
                refresh,
                save,
                status,
                deletionController::exitDelete
        );
    }

    public void install() {
        workspace.node().addEventFilter(
                MouseEvent.MOUSE_MOVED,
                creationController::handleMouseMoved
        );
        workspace.node().addEventFilter(
                MouseEvent.MOUSE_CLICKED,
                this::handleCanvasClick
        );
    }

    public void beginCreate(String sourceNeuronId) {
        creationController.beginCreate(sourceNeuronId);
    }

    public void cancelCreate() {
        creationController.cancelCreate();
    }

    public void beginDelete(String sourceNeuronId) {
        deletionController.beginDelete(sourceNeuronId);
    }

    public boolean hasConnections(String neuronId) {
        return deletionController.hasConnections(neuronId);
    }

    public void exitDelete() {
        deletionController.exitDelete();
    }

    public void refreshDeleteHighlights() {
        deletionController.refreshDeleteHighlights();
    }

    public void clearDeleteHighlights() {
        deletionController.clearDeleteHighlights();
    }

    public void refreshPreviewAfterCameraChange() {
        creationController.refreshPreviewAfterCameraChange();
    }

    private void handleCanvasClick(MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY) {
            if (state.mode() == EditorState.Mode.DELETE_CONNECTION) {
                exitDelete();
                event.consume();
            }
            return;
        }

        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        if (state.mode() == EditorState.Mode.CREATE_CONNECTION) {
            creationController.handleCreateClick(event);
            return;
        }

        if (state.mode() == EditorState.Mode.DELETE_CONNECTION) {
            deletionController.handleDeleteClick(event);
        }
    }
}
