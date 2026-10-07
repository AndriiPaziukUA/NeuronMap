package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.model.Connection;
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

    private final NeuronMapApplicationService application;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refresh;
    private final Runnable save;
    private final Consumer<String> status;

    ConnectionDeletionController(
            NeuronMapApplicationService application,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refresh,
            Runnable save,
            Consumer<String> status
    ) {
        this.application = Objects.requireNonNull(application, "application");
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

    boolean hasConnections(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return false;
        }

        return application.model().connections().stream()
                .anyMatch(connection ->
                        neuronId.equals(connection.sourceId())
                                || neuronId.equals(connection.targetId())
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

        NeuronView target = findNeuronView(event.getTarget());
        if (target != null) {
            String sourceId = state.deleteConnectionNeuronId();
            String targetId = target.model().id();

            if (sourceId != null && sourceId.equals(targetId)) {
                status.accept("Не можна видалити самозв'язок.");
                event.consume();
                return;
            }

            int removed = application.removeConnectionsBetween(sourceId, targetId);
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

        ConnectionView connectionView = findConnectionView(event.getTarget());
        if (connectionView == null) {
            connectionView = findConnectionViewAt(event.getSceneX(), event.getSceneY());
        }

        if (connectionView != null && connectionView.isDeleteHighlighted()) {
            if (application.removeConnection(connectionView.model().id())) {
                refresh.run();
                save.run();
                status.accept("Зв'язок видалено.");
            }
            event.consume();
            return;
        }

        // Left click on empty space intentionally does not cancel delete mode.
        event.consume();
    }

    void statusCancelMode() {
        status.accept("Режим видалення зв'язків вимкнено.");
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

    private static NeuronView findNeuronView(Object target) {
        Node node = target instanceof Node targetNode ? targetNode : null;

        while (node != null) {
            if (node instanceof NeuronView neuronView) {
                return neuronView;
            }
            node = node.getParent();
        }

        return null;
    }

    private static ConnectionView findConnectionView(Object target) {
        Node node = target instanceof Node targetNode ? targetNode : null;

        while (node != null) {
            if (node instanceof ConnectionView connectionView) {
                return connectionView;
            }
            node = node.getParent();
        }

        return null;
    }
}
