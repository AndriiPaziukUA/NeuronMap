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
 * Обробляє вибір і видалення зв’язку, знаходячи його видиму геометрію під вказівником та підсвічуючи доступні зв’язки.
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
     * Створює екземпляр ConnectionDeletionController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param connectionService служба операцій над зв’язками.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param workspace полотно редактора.
     * @param neuronViews мапа візуальних подань нейронів за їхніми ідентифікаторами.
     * @param refresh callback для оновлення інтерфейсу після зміни моделі.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
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
     * Створює екземпляр ConnectionDeletionController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param connectionService служба операцій над зв’язками.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param workspace полотно редактора.
     * @param neuronViews мапа візуальних подань нейронів за їхніми ідентифікаторами.
     * @param refresh callback для оновлення інтерфейсу після зміни моделі.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
     * @param localization служба локалізації інтерфейсу.
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
     * Починає операцію delete та готує стан взаємодії.
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
     * Завершує вибір зв’язку для видалення та очищує тимчасове підсвічування.
     */
    void exitDelete() {
        state.resetToIdle();
        clearDeleteHighlights();
        workspace.node().setCursor(Cursor.DEFAULT);
    }

    /**
     * Підсвічує зв’язки поточного початкового нейрона, які можна видалити.
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
     * Очищає delete highlights від тимчасових або застарілих значень.
     */
    void clearDeleteHighlights() {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView) {
                connectionView.stopDeleteHighlight();
            }
        }
    }

    /**
     * Знаходить зв’язок під курсором і видаляє його, якщо він належить поточному джерелу.
     *
     * @param event подія інтерфейсу, яку потрібно обробити.
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
     * Перевіряє, чи є connections у поточному стані.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    boolean hasConnections(String neuronId) {
        return connectionService.hasConnections(neuronId);
    }

    /**
     * Знаходить видиме подання зв’язку в точці сцени, по якій клацнув користувач.
     *
     * @param sceneX горизонтальна координата точки у системі координат сцени JavaFX.
     * @param sceneY вертикальна координата точки у системі координат сцени JavaFX.
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
     * Перевіряє, чи містить видима форма вузла вказану точку сцени.
     *
     * @param node вузол JavaFX, який потрібно перевірити або змінити.
     * @param scenePoint точка в координатах сцени JavaFX.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
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
