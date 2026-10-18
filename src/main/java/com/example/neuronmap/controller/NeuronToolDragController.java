package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.util.WorldCoordinateMapper;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronDragPreviewFactory;
import com.example.neuronmap.view.neuron.NeuronVisualGeometry;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.transform.Scale;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Обробляє перетягування інструментів створення з панелі на полотно та очищає їхні реєстрації під час закриття. */
public final class NeuronToolDragController {

    private final EditorState state;
    private final WorkspaceView workspace;
    private final BiConsumer<NeuronType, Point2D> dropConsumer;
    private final EventHandler<DragEvent> dragOverHandler = this::handleDragOver;
    private final EventHandler<DragEvent> dragExitedHandler = event -> clearPreview();
    private final EventHandler<DragEvent> droppedHandler = this::handleDrop;
    private final Map<Button, DragSourceRegistration> dragSources = new IdentityHashMap<>();

    private Node dragPreview;
    private NeuronType dragPreviewType;
    private Scale dragPreviewScale;
    private boolean installed;
    private boolean disposed;

    /**
     * Створює контролер перетягування інструментів нейронів.
     *
     * @param state стан редактора, що містить параметри камери.
     * @param workspace полотно редактора.
     * @param dropConsumer callback, який отримує тип нейрона та координату центра у світових координатах.
     */
    public NeuronToolDragController(
            EditorState state,
            WorkspaceView workspace,
            BiConsumer<NeuronType, Point2D> dropConsumer
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.dropConsumer = Objects.requireNonNull(dropConsumer, "dropConsumer");
    }

    /**
     * Реєструє обробники перетягування полотна; повторна установка не дублює підписки.
     *
     * @throws IllegalStateException якщо контролер уже звільнив свої ресурси.
     */
    public void install() {
        ensureNotDisposed();
        if (installed) {
            return;
        }
        workspace.node().addEventHandler(DragEvent.DRAG_OVER, dragOverHandler);
        workspace.node().addEventHandler(DragEvent.DRAG_EXITED, dragExitedHandler);
        workspace.node().addEventHandler(DragEvent.DRAG_DROPPED, droppedHandler);
        installed = true;
    }

    /** Налаштовує кнопки типів нейронів як джерела перетягування. */
    public void configureToolbarButtons(Button excitatoryButton, Button inhibitoryButton) {
        ensureNotDisposed();
        configureDragSource(excitatoryButton, NeuronType.EXCITATORY);
        configureDragSource(inhibitoryButton, NeuronType.INHIBITORY);
    }

    /** Прибирає попереднє зображення нейрона з полотна та очищує дані його масштабування. */
    public void clearPreview() {
        if (dragPreview != null) {
            workspace.overlayLayer().getChildren().remove(dragPreview);
        }
        dragPreview = null;
        dragPreviewType = null;
        dragPreviewScale = null;
    }

    /** Від'єднує обробники полотна й усіх налаштованих кнопок та відновлює їхні початкові курсори. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (installed) {
            workspace.node().removeEventHandler(DragEvent.DRAG_OVER, dragOverHandler);
            workspace.node().removeEventHandler(DragEvent.DRAG_EXITED, dragExitedHandler);
            workspace.node().removeEventHandler(DragEvent.DRAG_DROPPED, droppedHandler);
            installed = false;
        }
        dragSources.forEach((button, registration) -> {
            button.removeEventHandler(MouseEvent.DRAG_DETECTED, registration.dragDetectedHandler());
            button.removeEventHandler(MouseEvent.MOUSE_RELEASED, registration.mouseReleasedHandler());
            button.removeEventHandler(DragEvent.DRAG_DONE, registration.dragDoneHandler());
            Cursor currentCursor = button.getCursor();
            if (currentCursor == Cursor.HAND || currentCursor == Cursor.CLOSED_HAND) {
                button.setCursor(registration.originalCursor());
            }
        });
        dragSources.clear();
        clearPreview();
    }

    /** Налаштовує кнопку для передавання типу нейрона під час перетягування без перезапису чужих обробників. */
    private void configureDragSource(Button button, NeuronType type) {
        if (button == null || type == null || dragSources.containsKey(button)) {
            return;
        }
        EventHandler<MouseEvent> detectedHandler = event -> {
            if (disposed) {
                return;
            }
            Dragboard dragboard = button.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString(type.name());
            dragboard.setContent(content);
            clearPreview();
            dragPreviewType = type;
            button.setCursor(Cursor.CLOSED_HAND);
            event.consume();
        };
        EventHandler<MouseEvent> releasedHandler = event -> {
            if (!disposed) {
                button.setCursor(Cursor.HAND);
            }
        };
        EventHandler<DragEvent> doneHandler = event -> {
            clearPreview();
            if (!disposed) {
                button.setCursor(Cursor.HAND);
            }
            event.consume();
        };
        DragSourceRegistration registration = new DragSourceRegistration(
                detectedHandler, releasedHandler, doneHandler, button.getCursor());
        button.addEventHandler(MouseEvent.DRAG_DETECTED, detectedHandler);
        button.addEventHandler(MouseEvent.MOUSE_RELEASED, releasedHandler);
        button.addEventHandler(DragEvent.DRAG_DONE, doneHandler);
        dragSources.put(button, registration);
    }

    /** Оновлює ціль перетягування та дозволяє відпускання, якщо тип нейрона розпізнано. */
    private void handleDragOver(DragEvent event) {
        if (disposed) {
            return;
        }
        NeuronType type = dragNeuronType(event.getDragboard());
        if (type == null) {
            return;
        }
        event.acceptTransferModes(TransferMode.COPY);
        updateDragPreview(type, event.getSceneX(), event.getSceneY());
        event.consume();
    }

    /** Створює нейрон у точці полотна, на яку відпустили інструмент. */
    private void handleDrop(DragEvent event) {
        if (disposed) {
            event.setDropCompleted(false);
            return;
        }
        boolean success = false;
        try {
            NeuronType type = dragNeuronType(event.getDragboard());
            if (type != null) {
                Point2D viewportPoint = workspace.node().sceneToLocal(event.getSceneX(), event.getSceneY());
                Point2D worldPoint = WorldCoordinateMapper.viewportToWorld(
                        viewportPoint, state.zoom(), state.panX(), state.panY());
                dropConsumer.accept(type, worldPoint);
                success = true;
            }
            event.setDropCompleted(success);
        } finally {
            clearPreview();
        }
        event.consume();
    }

    /** Розташовує попередній перегляд нейрона під курсором під час перетягування інструмента. */
    private void updateDragPreview(NeuronType type, double sceneX, double sceneY) {
        if (dragPreview == null || dragPreviewType != type) {
            clearPreview();
            dragPreview = NeuronDragPreviewFactory.create(type);
            dragPreviewType = type;
            dragPreviewScale = new Scale(
                    state.zoom(), state.zoom(),
                    NeuronVisualGeometry.WIDTH / 2.0, NeuronVisualGeometry.HEIGHT / 2.0);
            dragPreview.getTransforms().add(dragPreviewScale);
            workspace.overlayLayer().getChildren().add(dragPreview);
        }
        Point2D cursor = workspace.overlayLayer().sceneToLocal(sceneX, sceneY);
        dragPreviewScale.setX(state.zoom());
        dragPreviewScale.setY(state.zoom());
        Point2D previewTopLeft = NeuronVisualGeometry.topLeftFromCenter(cursor);
        dragPreview.relocate(previewTopLeft.getX(), previewTopLeft.getY());
        dragPreview.toFront();
    }

    /** Розпізнає тип нейрона в буфері перетягування, не поширюючи помилки некоректного введення. */
    private static NeuronType dragNeuronType(Dragboard dragboard) {
        if (dragboard == null || !dragboard.hasString()) {
            return null;
        }
        try {
            return NeuronType.valueOf(dragboard.getString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Забороняє повторне налаштування контролера після його звільнення. */
    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Neuron tool drag controller is disposed");
        }
    }

    /** Зберігає точні посилання на підписки конкретної кнопки та її курсор до підключення контролера. */
    private record DragSourceRegistration(
            EventHandler<MouseEvent> dragDetectedHandler,
            EventHandler<MouseEvent> mouseReleasedHandler,
            EventHandler<DragEvent> dragDoneHandler,
            Cursor originalCursor
    ) { }
}
