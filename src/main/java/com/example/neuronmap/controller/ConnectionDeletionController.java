package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Shape;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Owns only the delete-connection mode and its highlights. */
final class ConnectionDeletionController {

    private final ConnectionService connectionService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refresh;
    private final Runnable save;
    private final Consumer<String> status;

    ConnectionDeletionController(
            ConnectionService connectionService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refresh,
            Runnable save,
            Consumer<String> status
    ) {
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = Objects.requireNonNull(neuronViews, "neuronViews");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
    }

    void beginDelete(String sourceNeuronId) {
        if (!hasConnections(sourceNeuronId)) {
            exitDelete();
            status.accept("У цього нейрона немає зв'язків для видалення.");
            return;
        }

        state.enterDeleteConnectionMode(sourceNeuronId);
        refreshDeleteHighlights();
        workspace.node().setCursor(Cursor.CROSSHAIR);
        status.accept(
                "Клацни по нейрону або по лінії зв'язку, який треба видалити. "
                        + "Права кнопка миші скасовує режим."
        );
    }

    void exitDelete() {
        state.resetToIdle();
        clearDeleteHighlights();
        workspace.node().setCursor(Cursor.DEFAULT);
    }

    void refreshDeleteHighlights() {
        String sourceId = state.deleteConnectionNeuronId();

        if (state.mode() == EditorState.Mode.DELETE_CONNECTION
                && !hasConnections(sourceId)) {
            exitDelete();
            return;
        }

        for (Node node : workspace.edgeLayer().getChildren()) {
            if (!(node instanceof ConnectionView connectionView)) {
                continue;
            }

            Connection connection = connectionView.model();
            boolean highlighted = sourceId != null
                    && (connection.sourceId().equals(sourceId)
                    || connection.targetId().equals(sourceId));

            connectionView.setDeleteHighlight(highlighted);
        }
    }

    void clearDeleteHighlights() {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView) {
                connectionView.stopDeleteHighlight();
            }
        }
    }

    void handleDeleteClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        NeuronView target = JavaFxNodeLookup.findAncestor(event.getTarget(), NeuronView.class);
        if (target != null) {
            String sourceId = state.deleteConnectionNeuronId();
            String targetId = target.model().id();

            if (sourceId != null && sourceId.equals(targetId)) {
                status.accept("Не можна видалити самозв'язок.");
                event.consume();
                return;
            }

            int removed = connectionService.removeBetween(sourceId, targetId);
            if (removed > 0) {
                refresh.run();
                save.run();
                status.accept("Контакт між нейронами розірвано.");
            } else {
                status.accept("Між цими нейронами немає зв'язку.");
            }

            event.consume();
            return;
        }

        ConnectionView connectionView = JavaFxNodeLookup.findAncestor(event.getTarget(), ConnectionView.class);
        if (connectionView == null) {
            connectionView = findConnectionViewAt(event.getSceneX(), event.getSceneY());
        }

        if (connectionView != null && connectionView.isDeleteHighlighted()) {
            if (connectionService.remove(connectionView.model().id())) {
                refresh.run();
                save.run();
                status.accept("Зв'язок видалено.");
            }
            event.consume();
            return;
        }

        event.consume();
    }

    boolean hasConnections(String neuronId) {
        return connectionService.hasConnections(neuronId);
    }

    private ConnectionView findConnectionViewAt(double sceneX, double sceneY) {
        Point2D scenePoint = new Point2D(sceneX, sceneY);

        for (int index = workspace.edgeLayer().getChildren().size() - 1;
             index >= 0;
             index--) {
            Node node = workspace.edgeLayer().getChildren().get(index);
            if (!(node instanceof ConnectionView connectionView)) {
                continue;
            }

            if (containsVisibleShape(connectionView, scenePoint)) {
                return connectionView;
            }
        }

        return null;
    }

    private static boolean containsVisibleShape(Node node, Point2D scenePoint) {
        if (!node.isVisible()) {
            return false;
        }

        if (node instanceof Shape shape) {
            Point2D localPoint = shape.sceneToLocal(scenePoint);
            if (shape.contains(localPoint)) {
                return true;
            }
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsVisibleShape(child, scenePoint)) {
                    return true;
                }
            }
        }

        return false;
    }

}
