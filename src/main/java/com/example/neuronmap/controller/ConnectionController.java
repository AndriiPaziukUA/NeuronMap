package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.Node;
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
        this(
                connectionService,
                state,
                workspace,
                neuronViews,
                refresh,
                save,
                status,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    public ConnectionController(
            ConnectionService connectionService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refresh,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        Objects.requireNonNull(connectionService, "connectionService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        LocalizationService sharedLocalization =
                Objects.requireNonNull(localization, "localization");

        this.deletionController = new ConnectionDeletionController(
                connectionService,
                state,
                workspace,
                neuronViews,
                refresh,
                save,
                status,
                sharedLocalization
        );
        this.creationController = new ConnectionCreationController(
                connectionService,
                state,
                workspace,
                neuronViews,
                refresh,
                save,
                status,
                deletionController::exitDelete,
                sharedLocalization
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

    public void pauseAnimations() {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView) {
                connectionView.pauseDeleteHighlight();
            }
        }
    }

    public void resumeAnimations() {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView) {
                connectionView.resumeDeleteHighlight();
            }
        }
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
