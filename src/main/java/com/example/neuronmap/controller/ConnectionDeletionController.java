package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
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

/**
 * Обробляє видалення звʼязків із карти та оновлення повʼязаного відображення.
 */
final class ConnectionDeletionController {

    private final ConnectionService connectionService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refresh;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;

    /**
     * Створює обʼєкт ConnectionDeletionController та ініціалізує його початковий стан.
     *
     * @param connectionService значення, що визначає звʼязок служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param refresh значення, що визначає відповідну операцію для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     */
    ConnectionDeletionController(
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

    /**
     * Створює обʼєкт ConnectionDeletionController та ініціалізує його початковий стан.
     *
     * @param connectionService значення, що визначає звʼязок служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param refresh значення, що визначає відповідну операцію для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     *
     * @param localization значення, що визначає локалізація для цієї операції.
     */
    ConnectionDeletionController(
            ConnectionService connectionService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refresh,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = Objects.requireNonNull(neuronViews, "neuronViews");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    /**
     * Запускає або планує дію, повʼязану з «видалити».
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     */
    void beginDelete(String sourceNeuronId) {
        if (!hasConnections(sourceNeuronId)) {
            exitDelete();
            status.accept(localization.text("status.no_connections_to_delete"));
            return;
        }

        state.enterDeleteConnectionMode(sourceNeuronId);
        refreshDeleteHighlights();
        workspace.node().setCursor(Cursor.CROSSHAIR);
        status.accept(localization.text("status.connection_delete_hint"));
    }

    /**
     * Виконує операцію «видалити».
     */
    void exitDelete() {
        state.resetToIdle();
        clearDeleteHighlights();
        workspace.node().setCursor(Cursor.DEFAULT);
    }

    /**
     * Обробляє «видалити».
     */
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

    /**
     * Видаляє або скидає дані, повʼязані з «видалити».
     */
    void clearDeleteHighlights() {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView) {
                connectionView.stopDeleteHighlight();
            }
        }
    }

    /**
     * Обробляє «видалити».
     *
     * @param event подія інтерфейсу.
     */
    void handleDeleteClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        NeuronView target = JavaFxNodeLookup.findAncestor(event.getTarget(), NeuronView.class);
        if (target != null) {
            String sourceId = state.deleteConnectionNeuronId();
            String targetId = target.model().id();

            if (sourceId != null && sourceId.equals(targetId)) {
                status.accept(localization.text("status.self_connection_delete_forbidden"));
                event.consume();
                return;
            }

            int removed = connectionService.removeBetween(sourceId, targetId);
            if (removed > 0) {
                refresh.run();
                save.run();
                status.accept(localization.text("status.contact_broken"));
            } else {
                status.accept(localization.text("status.no_connection_between"));
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
                status.accept(localization.text("status.connection_deleted"));
            }
            event.consume();
            return;
        }

        event.consume();
    }

    /**
     * Перевіряє, чи виконується умова «звʼязки».
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    boolean hasConnections(String neuronId) {
        return connectionService.hasConnections(neuronId);
    }

    /**
     * Повертає або знаходить дані, повʼязані з «звʼязок відображення».
     *
     * @param sceneX значення, що визначає сцена для цієї операції.
     *
     * @param sceneY значення, що визначає сцена для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @param node графічний вузол JavaFX.
     *
     * @param scenePoint значення, що визначає сцена для цієї операції.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
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
