package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.MainView;
import com.example.neuronmap.view.NeuronDragPreviewFactory;
import com.example.neuronmap.view.NeuronView;
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

/** Handles drag-and-drop creation of neurons from the toolbar. */
public final class NeuronToolDragController {

    private final MainView view;
    private final EditorState state;
    private final BiConsumer<NeuronType, Point2D> addNeuronAtWorldCenter;

    private Node dragPreview;
    private NeuronType dragPreviewType;
    private Scale dragPreviewScale;

    public NeuronToolDragController(
            MainView view,
            EditorState state,
            BiConsumer<NeuronType, Point2D> addNeuronAtWorldCenter
    ) {
        this.view = Objects.requireNonNull(view, "view");
        this.state = Objects.requireNonNull(state, "state");
        this.addNeuronAtWorldCenter = Objects.requireNonNull(
                addNeuronAtWorldCenter,
                "addNeuronAtWorldCenter"
        );
    }

    public void install() {
        configureDragSource(
                view.toolbar().addExcitatoryButton(),
                NeuronType.EXCITATORY
        );
        configureDragSource(
                view.toolbar().addInhibitoryButton(),
                NeuronType.INHIBITORY
        );
        installDropHandlers();
    }

    public void clearDragPreview() {
        removeDragPreview();
    }

    private void configureDragSource(Button button, NeuronType type) {
        button.setOnDragDetected(event -> {
            Dragboard dragboard =
                    button.startDragAndDrop(TransferMode.COPY);

            ClipboardContent content = new ClipboardContent();
            content.putString(type.name());
            dragboard.setContent(content);

            dragPreviewType = type;
            removeDragPreview();
            button.setCursor(Cursor.CLOSED_HAND);
            event.consume();
        });

        button.setOnMouseReleased(
                event -> button.setCursor(Cursor.HAND)
        );

        button.addEventHandler(
                DragEvent.DRAG_DONE,
                event -> {
                    removeDragPreview();
                    dragPreviewType = null;
                    button.setCursor(Cursor.HAND);
                    event.consume();
                }
        );
    }

    private void installDropHandlers() {
        view.workspace().node().addEventHandler(
                DragEvent.DRAG_OVER,
                event -> {
                    NeuronType type = dragNeuronType(event.getDragboard());
                    if (type == null) {
                        return;
                    }

                    event.acceptTransferModes(TransferMode.COPY);
                    updateDragPreview(
                            type,
                            event.getSceneX(),
                            event.getSceneY()
                    );
                    event.consume();
                }
        );

        view.workspace().node().addEventHandler(
                DragEvent.DRAG_EXITED,
                event -> removeDragPreview()
        );

        view.workspace().node().addEventHandler(
                DragEvent.DRAG_DROPPED,
                event -> {
                    boolean success = false;

                    try {
                        NeuronType type = dragNeuronType(
                                event.getDragboard()
                        );

                        if (type != null) {
                            Point2D viewportPoint =
                                    view.workspace().node().sceneToLocal(
                                            event.getSceneX(),
                                            event.getSceneY()
                                    );
                            Point2D worldPoint = screenToWorld(
                                    viewportPoint.getX(),
                                    viewportPoint.getY()
                            );
                            addNeuronAtWorldCenter.accept(type, worldPoint);
                            success = true;
                        }

                        event.setDropCompleted(success);
                    } finally {
                        removeDragPreview();
                        dragPreviewType = null;
                    }

                    event.consume();
                }
        );
    }

    private void updateDragPreview(
            NeuronType type,
            double sceneX,
            double sceneY
    ) {
        if (dragPreview == null || dragPreviewType != type) {
            removeDragPreview();

            dragPreview = NeuronDragPreviewFactory.create(type);
            dragPreviewType = type;
            dragPreviewScale = new Scale(
                    state.zoom(),
                    state.zoom(),
                    NeuronView.WIDTH / 2.0,
                    NeuronView.HEIGHT / 2.0
            );

            dragPreview.getTransforms().add(dragPreviewScale);
            view.workspace().overlayLayer()
                    .getChildren()
                    .add(dragPreview);
        }

        Point2D cursor =
                view.workspace().overlayLayer().sceneToLocal(
                        sceneX,
                        sceneY
                );

        dragPreviewScale.setX(state.zoom());
        dragPreviewScale.setY(state.zoom());

        dragPreview.relocate(
                cursor.getX() - NeuronView.WIDTH / 2.0,
                cursor.getY() - NeuronView.HEIGHT / 2.0
        );
        dragPreview.toFront();
    }

    private NeuronType dragNeuronType(Dragboard dragboard) {
        if (dragboard == null || !dragboard.hasString()) {
            return null;
        }

        try {
            return NeuronType.valueOf(dragboard.getString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void removeDragPreview() {
        if (dragPreview != null) {
            view.workspace().overlayLayer()
                    .getChildren()
                    .remove(dragPreview);
        }

        dragPreview = null;
        dragPreviewType = null;
        dragPreviewScale = null;
    }

    private Point2D screenToWorld(double screenX, double screenY) {
        return new Point2D(
                (screenX - state.panX()) / state.zoom(),
                (screenY - state.panY()) / state.zoom()
        );
    }
}
