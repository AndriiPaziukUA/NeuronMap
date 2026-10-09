package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.NeuronDragPreviewFactory;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.transform.Scale;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Обробляє перетягування інструментів редактора та їхнє розміщення на карті.
 */
public final class NeuronToolDragController {

    private final EditorState state;
    private final WorkspaceView workspace;
    private final BiConsumer<NeuronType, Point2D> dropConsumer;

    private Node dragPreview;
    private NeuronType dragPreviewType;
    private Scale dragPreviewScale;

    /**
     * Повертає результат операції «нейрон перетягування».
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param dropConsumer значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Виконує операцію «відповідну операцію».
     */
    public void install() {
        workspace.node().addEventHandler(DragEvent.DRAG_OVER, this::handleDragOver);
        workspace.node().addEventHandler(DragEvent.DRAG_EXITED, event -> clearPreview());
        workspace.node().addEventHandler(DragEvent.DRAG_DROPPED, this::handleDrop);
    }

    /**
     * Задає або оновлює значення, повʼязані з «панель інструментів».
     *
     * @param excitatoryButton значення, що визначає кнопка для цієї операції.
     *
     * @param inhibitoryButton значення, що визначає кнопка для цієї операції.
     */
    public void configureToolbarButtons(Button excitatoryButton, Button inhibitoryButton) {
        configureDragSource(excitatoryButton, NeuronType.EXCITATORY);
        configureDragSource(inhibitoryButton, NeuronType.INHIBITORY);
    }

    /**
     * Видаляє або скидає дані, повʼязані з «попередній перегляд».
     */
    public void clearPreview() {
        if (dragPreview != null) {
            workspace.overlayLayer().getChildren().remove(dragPreview);
        }
        dragPreview = null;
        dragPreviewType = null;
        dragPreviewScale = null;
    }

    /**
     * Задає або оновлює значення, повʼязані з «перетягування джерело».
     *
     * @param button значення, що визначає кнопка для цієї операції.
     *
     * @param type тип обʼєкта.
     */
    private void configureDragSource(Button button, NeuronType type) {
        if (button == null || type == null) {
            return;
        }

        button.setOnDragDetected(event -> {
            Dragboard dragboard = button.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString(type.name());
            dragboard.setContent(content);

            clearPreview();
            dragPreviewType = type;
            button.setCursor(Cursor.CLOSED_HAND);
            event.consume();
        });

        button.setOnMouseReleased(event -> button.setCursor(Cursor.HAND));
        button.addEventHandler(DragEvent.DRAG_DONE, event -> {
            clearPreview();
            button.setCursor(Cursor.HAND);
            event.consume();
        });
    }

    /**
     * Обробляє «перетягування».
     *
     * @param event подія інтерфейсу.
     */
    private void handleDragOver(DragEvent event) {
        NeuronType type = dragNeuronType(event.getDragboard());
        if (type == null) {
            return;
        }

        event.acceptTransferModes(TransferMode.COPY);
        updateDragPreview(type, event.getSceneX(), event.getSceneY());
        event.consume();
    }

    /**
     * Обробляє «відповідну операцію».
     *
     * @param event подія інтерфейсу.
     */
    private void handleDrop(DragEvent event) {
        boolean success = false;
        try {
            NeuronType type = dragNeuronType(event.getDragboard());
            if (type != null) {
                Point2D viewportPoint = workspace.node().sceneToLocal(
                        event.getSceneX(),
                        event.getSceneY()
                );
                Point2D worldPoint = screenToWorld(viewportPoint);
                dropConsumer.accept(type, worldPoint);
                success = true;
            }
            event.setDropCompleted(success);
        } finally {
            clearPreview();
        }
        event.consume();
    }

    /**
     * Задає або оновлює значення, повʼязані з «перетягування попередній перегляд».
     *
     * @param type тип обʼєкта.
     *
     * @param sceneX значення, що визначає сцена для цієї операції.
     *
     * @param sceneY значення, що визначає сцена для цієї операції.
     */
    private void updateDragPreview(NeuronType type, double sceneX, double sceneY) {
        if (dragPreview == null || dragPreviewType != type) {
            clearPreview();
            dragPreview = NeuronDragPreviewFactory.create(type);
            dragPreviewType = type;
            dragPreviewScale = new Scale(
                    state.zoom(),
                    state.zoom(),
                    NeuronView.WIDTH / 2.0,
                    NeuronView.HEIGHT / 2.0
            );
            dragPreview.getTransforms().add(dragPreviewScale);
            workspace.overlayLayer().getChildren().add(dragPreview);
        }

        Point2D cursor = workspace.overlayLayer().sceneToLocal(sceneX, sceneY);
        dragPreviewScale.setX(state.zoom());
        dragPreviewScale.setY(state.zoom());
        dragPreview.relocate(
                cursor.getX() - NeuronView.WIDTH / 2.0,
                cursor.getY() - NeuronView.HEIGHT / 2.0
        );
        dragPreview.toFront();
    }

    /**
     * Повертає результат операції «до карта».
     *
     * @param screenPoint значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private Point2D screenToWorld(Point2D screenPoint) {
        return new Point2D(
                (screenPoint.getX() - state.panX()) / state.zoom(),
                (screenPoint.getY() - state.panY()) / state.zoom()
        );
    }

    /**
     * Переміщує обʼєкт «нейрон тип» відповідно до переданого зміщення.
     *
     * @param dragboard значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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
}
