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
 * Координує створення й видалення зв’язків на полотні та оновлення їхнього візуального стану.
 */
public final class ConnectionController {

    private final EditorState state;
    private final WorkspaceView workspace;
    private final ConnectionCreationController creationController;
    private final ConnectionDeletionController deletionController;

    /**
     * Створює екземпляр ConnectionController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param connectionService служба операцій над зв’язками.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param workspace полотно редактора.
     * @param neuronViews мапа візуальних подань нейронів за їхніми ідентифікаторами.
     * @param refresh callback для оновлення інтерфейсу після зміни моделі.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
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
     * Створює екземпляр ConnectionController та зберігає передані залежності, потрібні для його роботи.
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
     * Під’єднує обробники полотна для створення, скасування та видалення зв’язків.
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
     * Вмикає режим створення зв’язку від указаного нейрона.
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     */
    public void beginCreate(String sourceNeuronId) {
        creationController.beginCreate(sourceNeuronId);
    }

    /**
     * Скасовує незавершене створення зв’язку та прибирає його попередній перегляд.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public void cancelCreate() {
        creationController.cancelCreate();
    }

    /**
     * Вмикає режим вибору зв’язку для видалення від указаного нейрона.
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     */
    public void beginDelete(String sourceNeuronId) {
        deletionController.beginDelete(sourceNeuronId);
    }

    /**
     * Перевіряє, чи має нейрон зв’язки, доступні для дії в поточному режимі.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean hasConnections(String neuronId) {
        return deletionController.hasConnections(neuronId);
    }

    /**
     * Завершує режим видалення зв’язку та прибирає його підсвічування.
     */
    public void exitDelete() {
        deletionController.exitDelete();
    }

    /**
     * Оновлює підсвічування зв’язків, доступних для видалення.
     */
    public void refreshDeleteHighlights() {
        deletionController.refreshDeleteHighlights();
    }

    /**
     * Прибирає підсвічування всіх зв’язків, установлене режимом видалення.
     */
    public void clearDeleteHighlights() {
        deletionController.clearDeleteHighlights();
    }

    /**
     * Перераховує кінцеві координати тимчасового зв’язку після панорамування або масштабування камери.
     */
    public void refreshPreviewAfterCameraChange() {
        creationController.refreshPreviewAfterCameraChange();
    }

    /**
     * Призупиняє анімації імпульсів на зв’язках.
     */
    public void pauseAnimations() {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView) {
                connectionView.pauseDeleteHighlight();
            }
        }
    }

    /**
     * Відновлює анімації імпульсів на зв’язках.
     */
    public void resumeAnimations() {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView) {
                connectionView.resumeDeleteHighlight();
            }
        }
    }

    /**
     * Передає клацання полотном активному обробнику створення або видалення зв’язку.
     *
     * @param event подія інтерфейсу, яку потрібно обробити.
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
