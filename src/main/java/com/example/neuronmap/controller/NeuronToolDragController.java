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

/** Owns toolbar-to-workspace neuron drag and drop, including its preview. */
public final class NeuronToolDragController {

    private final EditorState state;
    private final WorkspaceView workspace;
    private final BiConsumer<NeuronType, Point2D> dropConsumer;

    private Node dragPreview;
    private NeuronType dragPreviewType;
    private Scale dragPreviewScale;

    public NeuronToolDragController(
            EditorState state,
            WorkspaceView workspace,
            BiConsumer<NeuronType, Point2D> dropConsumer
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.dropConsumer = Objects.requireNonNull(dropConsumer, "dropConsumer");
    }

    public void install() {
        workspace.node().addEventHandler(DragEvent.DRAG_OVER, this::handleDragOver);
        workspace.node().addEventHandler(DragEvent.DRAG_EXITED, event -> clearPreview());
        workspace.node().addEventHandler(DragEvent.DRAG_DROPPED, this::handleDrop);
    }

    public void configureToolbarButtons(Button excitatoryButton, Button inhibitoryButton) {
        configureDragSource(excitatoryButton, NeuronType.EXCITATORY);
        configureDragSource(inhibitoryButton, NeuronType.INHIBITORY);
    }

    public void clearPreview() {
        if (dragPreview != null) {
            workspace.overlayLayer().getChildren().remove(dragPreview);
        }
        dragPreview = null;
        dragPreviewType = null;
        dragPreviewScale = null;
    }

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

    private void handleDragOver(DragEvent event) {
        NeuronType type = dragNeuronType(event.getDragboard());
        if (type == null) {
            return;
        }

        event.acceptTransferModes(TransferMode.COPY);
        updateDragPreview(type, event.getSceneX(), event.getSceneY());
        event.consume();
    }

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

    private Point2D screenToWorld(Point2D screenPoint) {
        return new Point2D(
                (screenPoint.getX() - state.panX()) / state.zoom(),
                (screenPoint.getY() - state.panY()) / state.zoom()
        );
    }

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
