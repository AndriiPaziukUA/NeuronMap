package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Objects;

/** Координує створення й видалення зв’язків на полотні та оновлення їхнього візуального стану. */
public final class ConnectionController {

    private final EditorState state;
    private final WorkspaceView workspace;
    private final ConnectionCreationController creationController;
    private final ConnectionDeletionController deletionController;
    private final EventHandler<MouseEvent> mouseMovedHandler = this::handleMouseMoved;
    private final EventHandler<MouseEvent> canvasClickHandler = this::handleCanvasClick;

    private boolean installed;
    private boolean disposed;

    /**
     * Створює фасад керування зв’язками з уже складеними спеціалізованими контролерами.
     * Створення підконтролерів залишається відповідальністю composition root.
     *
     * @param state спільний стан редактора.
     * @param workspace полотно редактора.
     * @param creationController контролер створення зв’язків.
     * @param deletionController контролер видалення зв’язків.
     * @throws NullPointerException якщо обов’язкова залежність відсутня.
     */
    ConnectionController(
            EditorState state,
            WorkspaceView workspace,
            ConnectionCreationController creationController,
            ConnectionDeletionController deletionController
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.creationController = Objects.requireNonNull(creationController, "creationController");
        this.deletionController = Objects.requireNonNull(deletionController, "deletionController");
    }

    /**
     * Під’єднує обробники полотна; повторна установка не створює дубльованих реєстрацій.
     *
     * @throws IllegalStateException якщо контролер уже звільнив свої ресурси.
     */
    public void install() {
        ensureNotDisposed();
        if (installed) {
            return;
        }
        workspace.node().addEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        workspace.node().addEventFilter(MouseEvent.MOUSE_CLICKED, canvasClickHandler);
        installed = true;
    }

    /** Знімає фільтри, скасовує створення зв’язку та завершує режим видалення; повторний виклик безпечний. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (installed) {
            workspace.node().removeEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
            workspace.node().removeEventFilter(MouseEvent.MOUSE_CLICKED, canvasClickHandler);
            installed = false;
        }
        creationController.cancelCreate();
        deletionController.exitDelete();
        deletionController.clearDeleteHighlights();
    }

    /** Вмикає режим створення зв’язку від указаного нейрона. */
    public void beginCreate(String sourceNeuronId) {
        ensureNotDisposed();
        creationController.beginCreate(sourceNeuronId);
    }

    /** Скасовує незавершене створення зв’язку та прибирає його попередній перегляд. */
    public void cancelCreate() {
        creationController.cancelCreate();
    }

    /** Вмикає режим вибору зв’язку для видалення від указаного нейрона. */
    public void beginDelete(String sourceNeuronId) {
        ensureNotDisposed();
        deletionController.beginDelete(sourceNeuronId);
    }

    /** Перевіряє, чи має нейрон зв’язки, доступні для дії в поточному режимі. */
    public boolean hasConnections(String neuronId) {
        return deletionController.hasConnections(neuronId);
    }

    /** Завершує режим видалення зв’язку та прибирає його підсвічування. */
    public void exitDelete() {
        deletionController.exitDelete();
    }

    /** Оновлює підсвічування зв’язків, доступних для видалення. */
    public void refreshDeleteHighlights() {
        deletionController.refreshDeleteHighlights();
    }

    /** Прибирає підсвічування всіх зв’язків, установлене режимом видалення. */
    public void clearDeleteHighlights() {
        deletionController.clearDeleteHighlights();
    }

    /** Перераховує кінцеві координати тимчасового зв’язку після панорамування або масштабування камери. */
    public void refreshPreviewAfterCameraChange() {
        creationController.refreshPreviewAfterCameraChange();
    }

    /** Призупиняє анімації імпульсів на зв’язках. */
    public void pauseAnimations() {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView) {
                connectionView.pauseDeleteHighlight();
            }
        }
    }

    /** Відновлює анімації імпульсів на зв’язках. */
    public void resumeAnimations() {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView) {
                connectionView.resumeDeleteHighlight();
            }
        }
    }

    /** Передає рух миші контролеру попереднього перегляду лише протягом активного життєвого циклу. */
    private void handleMouseMoved(MouseEvent event) {
        if (!disposed) {
            creationController.handleMouseMoved(event);
        }
    }

    /** Обробляє клацання полотна відповідно до активного режиму редактора. */
    private void handleCanvasClick(MouseEvent event) {
        if (disposed) {
            return;
        }
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

    /** Забороняє повторно активувати контролер після звільнення його реєстрацій. */
    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Connection controller is disposed");
        }
    }
}
