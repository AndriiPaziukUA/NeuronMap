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

/**
 * Обробляє взаємодію користувача зі звʼязками між нейронами.
 */
public final class ConnectionController {

    private final EditorState state;
    private final WorkspaceView workspace;
    private final ConnectionCreationController creationController;
    private final ConnectionDeletionController deletionController;

    /**
     * Повертає результат операції «звʼязок».
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
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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

    /**
     * Повертає результат операції «звʼязок».
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
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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

    /**
     * Виконує операцію «відповідну операцію».
     */
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

    /**
     * Запускає або планує дію, повʼязану з «створити».
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     */
    public void beginCreate(String sourceNeuronId) {
        creationController.beginCreate(sourceNeuronId);
    }

    /**
     * Завершує або скасовує дію, повʼязану з «створити».
     */
    public void cancelCreate() {
        creationController.cancelCreate();
    }

    /**
     * Запускає або планує дію, повʼязану з «видалити».
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     */
    public void beginDelete(String sourceNeuronId) {
        deletionController.beginDelete(sourceNeuronId);
    }

    /**
     * Перевіряє, чи виконується умова «звʼязки».
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean hasConnections(String neuronId) {
        return deletionController.hasConnections(neuronId);
    }

    /**
     * Виконує операцію «видалити».
     */
    public void exitDelete() {
        deletionController.exitDelete();
    }

    /**
     * Обробляє «видалити».
     */
    public void refreshDeleteHighlights() {
        deletionController.refreshDeleteHighlights();
    }

    /**
     * Видаляє або скидає дані, повʼязані з «видалити».
     */
    public void clearDeleteHighlights() {
        deletionController.clearDeleteHighlights();
    }

    /**
     * Обробляє «попередній перегляд після камера змінити».
     */
    public void refreshPreviewAfterCameraChange() {
        creationController.refreshPreviewAfterCameraChange();
    }

    /**
     * Виконує операцію «анімації».
     */
    public void pauseAnimations() {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView) {
                connectionView.pauseDeleteHighlight();
            }
        }
    }

    /**
     * Виконує операцію «анімації».
     */
    public void resumeAnimations() {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView) {
                connectionView.resumeDeleteHighlight();
            }
        }
    }

    /**
     * Обробляє «відповідну операцію».
     *
     * @param event подія інтерфейсу.
     */
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
