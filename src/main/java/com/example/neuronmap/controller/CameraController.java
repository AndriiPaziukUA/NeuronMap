package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.util.CameraWorldCenter;
import com.example.neuronmap.util.GeometryUtils;
import com.example.neuronmap.view.WorkspaceView;
import javafx.animation.PauseTransition;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Owns viewport camera interaction and camera persistence. */
public final class CameraController {

    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable refreshOverlay;
    private final Runnable save;
    private final BooleanSupplier interactionActive;
    private final Predicate<Object> interactiveTarget;
    private final Consumer<String> status;
    private final Consumer<Point2D> cameraCenterWorldConsumer;
    private final double zoomFactor;

    private final PauseTransition saveDebounce =
            new PauseTransition(Duration.millis(250));

    private boolean panning;
    private double lastMouseX;
    private double lastMouseY;

    public CameraController(
            EditorState state,
            WorkspaceView workspace,
            Runnable refreshOverlay,
            Runnable save,
            BooleanSupplier interactionActive,
            Predicate<Object> interactiveTarget,
            Consumer<String> status,
            Consumer<Point2D> cameraCenterWorldConsumer,
            double zoomFactor
    ) {
        this.state = state;
        this.workspace = workspace;
        this.refreshOverlay = refreshOverlay;
        this.save = save;
        this.interactionActive = interactionActive;
        this.interactiveTarget = interactiveTarget;
        this.status = status;
        this.cameraCenterWorldConsumer = cameraCenterWorldConsumer;
        if (!Double.isFinite(zoomFactor) || zoomFactor <= 1.0) {
            throw new IllegalArgumentException("zoomFactor must be > 1");
        }
        this.zoomFactor = zoomFactor;

        saveDebounce.setOnFinished(event -> save.run());
    }

    public void install() {
        workspace.node().addEventFilter(
                MouseEvent.MOUSE_PRESSED,
                this::handlePressed
        );
        workspace.node().addEventHandler(
                MouseEvent.MOUSE_DRAGGED,
                this::handleDragged
        );
        workspace.node().addEventHandler(
                MouseEvent.MOUSE_RELEASED,
                this::handleReleased
        );
        workspace.node().addEventHandler(
                ScrollEvent.SCROLL,
                this::handleZoom
        );

        workspace.node().widthProperty().addListener(
                (observable, oldValue, newValue) -> refreshCameraCoordinates()
        );
        workspace.node().heightProperty().addListener(
                (observable, oldValue, newValue) -> refreshCameraCoordinates()
        );
    }

    public void apply() {
        workspace.setWorldTransform(
                state.zoom(),
                state.panX(),
                state.panY()
        );
        refreshOverlay.run();
        refreshCameraCoordinates();
    }

    private void handlePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (interactionActive.getAsBoolean()) {
            return;
        }
        if (interactiveTarget.test(event.getTarget())) {
            return;
        }

        panning = true;
        lastMouseX = event.getX();
        lastMouseY = event.getY();
        workspace.node().setCursor(Cursor.CLOSED_HAND);
        event.consume();
    }

    private void handleDragged(MouseEvent event) {
        if (!panning) {
            return;
        }

        double deltaX = event.getX() - lastMouseX;
        double deltaY = event.getY() - lastMouseY;

        state.setPanX(state.panX() + deltaX);
        state.setPanY(state.panY() + deltaY);

        lastMouseX = event.getX();
        lastMouseY = event.getY();

        apply();
        event.consume();
    }

    private void handleReleased(MouseEvent event) {
        if (!panning) {
            return;
        }

        panning = false;
        workspace.node().setCursor(Cursor.DEFAULT);
        scheduleSave();
    }

    private void handleZoom(ScrollEvent event) {
        if (event.getDeltaY() == 0.0) {
            return;
        }

        Point2D cursor = new Point2D(event.getX(), event.getY());
        double oldZoom = state.zoom();
        double factor = event.getDeltaY() > 0.0
                ? zoomFactor
                : 1.0 / zoomFactor;

        double newZoom = GeometryUtils.clampZoom(oldZoom * factor);
        if (Double.compare(oldZoom, newZoom) == 0) {
            return;
        }

        double worldX =
                (cursor.getX() - state.panX()) / oldZoom;
        double worldY =
                (cursor.getY() - state.panY()) / oldZoom;

        state.setZoom(newZoom);
        state.setPanX(cursor.getX() - worldX * newZoom);
        state.setPanY(cursor.getY() - worldY * newZoom);

        apply();
        scheduleSave();
        status.accept("Масштаб: " + formatZoom(newZoom));
        event.consume();
    }

    private void refreshCameraCoordinates() {
        if (cameraCenterWorldConsumer == null) {
            return;
        }

        double width = workspace.node().getWidth();
        double height = workspace.node().getHeight();

        if (width <= 0.0 || height <= 0.0) {
            return;
        }

        cameraCenterWorldConsumer.accept(
                CameraWorldCenter.calculate(
                        width,
                        height,
                        state.zoom(),
                        state.panX(),
                        state.panY()
                )
        );
    }

    private void scheduleSave() {
        saveDebounce.playFromStart();
    }

    private static String formatZoom(double zoom) {
        double percent = zoom * 100.0;
        return Math.abs(percent - Math.rint(percent)) < 0.0001
                ? String.format(java.util.Locale.ROOT, "%.0f%%", percent)
                : String.format(java.util.Locale.ROOT, "%.1f%%", percent);
    }
}
