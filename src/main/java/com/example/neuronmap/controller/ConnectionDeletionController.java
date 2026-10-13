package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.view.ConnectionHitTester;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Обробляє вибір і видалення зв’язку та підсвічує доступні для видалення зв’язки.
 */
final class ConnectionDeletionController {

    private final ConnectionService connectionService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final ConnectionHitTester connectionHitTester;
    private final Runnable refresh;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;

    /**
     * Створює контролер видалення зв’язків.
     *
     * @param connectionService служба операцій над зв’язками.
     * @param state стан редактора.
     * @param workspace полотно редактора.
     * @param refresh дія оновлення інтерфейсу після зміни моделі.
     * @param save дія збереження змін.
     * @param status дія показу повідомлення в рядку стану.
     * @param localization служба локалізації інтерфейсу.
     */
    ConnectionDeletionController(
            ConnectionService connectionService,
            EditorState state,
            WorkspaceView workspace,
            Runnable refresh,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.connectionHitTester = new ConnectionHitTester(workspace);
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    /**
     * Починає режим видалення зв’язку від указаного нейрона.
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
     * Очищає тимчасове підсвічування зв’язків.
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

        ConnectionView connectionView = JavaFxNodeLookup.findAncestor(
                event.getTarget(),
                ConnectionView.class
        );
        if (connectionView == null) {
            connectionView = connectionHitTester.findConnectionViewAt(
                    event.getSceneX(),
                    event.getSceneY()
            );
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
     * Перевіряє, чи має нейрон зв’язки.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return {@code true}, якщо нейрон має зв’язки; інакше {@code false}.
     */
    boolean hasConnections(String neuronId) {
        return connectionService.hasConnections(neuronId);
    }
}
