package com.example.neuronmap.view;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

/**
 * Single owner of all overlay coordinates for neuron controls.
 *
 * <p>Neuron controls live in the unscaled overlay layer. Their positions are
 * therefore calculated in scene coordinates from the neuron's real transform
 * and converted exactly once into the overlay parent's coordinates.</p>
 */
public final class NeuronOverlayPositioner {

    public static final double MENU_GAP = 8.0;
    public static final double HANDLE_GAP = 8.0;

    private static final double NORMAL_SAMPLE = 1.0;

    private final WorkspaceView workspace;

    public NeuronOverlayPositioner(WorkspaceView workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException(
                    "workspace must not be null"
            );
        }
        this.workspace = workspace;
    }

    /**
     * Positions all visible overlays for one neuron.
     */
    public void position(
            NeuronView neuronView,
            VBox menu,
            RotationHandleView handle
    ) {
        if (neuronView == null) {
            return;
        }

        Pane overlay = workspace.overlayLayer();

        Point2D center = toParent(
                neuronView,
                NeuronView.WIDTH / 2.0,
                NeuronView.HEIGHT / 2.0,
                overlay
        );

        if (handle != null && handle.isVisible()) {
            Point2D handleCenter = handleCenterInParent(
                    neuronView,
                    overlay
            );

            handle.placeCenterAt(handleCenter);
            handle.setVisualRotation(neuronView.getRotate());
        }

        if (menu != null && menu.isVisible()) {
            positionMenu(
                    menu,
                    center,
                    rectangleTopYInParent(neuronView, overlay)
            );
        }
    }

    /**
     * Positions a handle in an arbitrary parent coordinate space.
     * This is the only public handle-positioning entry point.
     */
    public static void positionRotationHandleInParent(
            NeuronView neuronView,
            RotationHandleView handle,
            Node coordinateSpace
    ) {
        if (neuronView == null
                || handle == null
                || coordinateSpace == null) {
            return;
        }

        Point2D sceneHandleCenter = handleCenterInScene(neuronView);
        Point2D parentCenter = coordinateSpace.sceneToLocal(
                sceneHandleCenter
        );

        handle.placeCenterAt(parentCenter);
        handle.setVisualRotation(neuronView.getRotate());
    }

    /**
     * Exact neuron center in the supplied coordinate space.
     */
    public Point2D rectangleCenterIn(
            NeuronView neuronView,
            Node coordinateSpace
    ) {
        if (neuronView == null) {
            throw new IllegalArgumentException(
                    "neuronView must not be null"
            );
        }
        if (coordinateSpace == null) {
            throw new IllegalArgumentException(
                    "coordinateSpace must not be null"
            );
        }

        return toParent(
                neuronView,
                NeuronView.WIDTH / 2.0,
                NeuronView.HEIGHT / 2.0,
                coordinateSpace
        );
    }

    /**
     * Rotation-handle center in the supplied coordinate space.
     *
     * <p>The anchor is the actual midpoint of the neuron's bottom edge in
     * scene coordinates. Only the outward normal is normalized; the final
     * offset from that edge is therefore a true screen-pixel distance. This
     * keeps the visible gap between the neuron and the fixed-size handle
     * independent of workspace zoom.</p>
     */
    public Point2D handleCenterInParent(
            NeuronView neuronView,
            Node coordinateSpace
    ) {
        if (neuronView == null) {
            throw new IllegalArgumentException(
                    "neuronView must not be null"
            );
        }
        if (coordinateSpace == null) {
            throw new IllegalArgumentException(
                    "coordinateSpace must not be null"
            );
        }

        return coordinateSpace.sceneToLocal(
                handleCenterInScene(neuronView)
        );
    }

    /**
     * Pure geometry helper used by unit tests.
     */
    static Point2D offsetAlongNormal(
            Point2D anchor,
            Point2D directionPoint,
            double distance
    ) {
        if (anchor == null) {
            throw new IllegalArgumentException(
                    "anchor must not be null"
            );
        }
        if (directionPoint == null) {
            throw new IllegalArgumentException(
                    "directionPoint must not be null"
            );
        }
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException(
                    "distance must be finite and non-negative"
            );
        }

        double dx = directionPoint.getX() - anchor.getX();
        double dy = directionPoint.getY() - anchor.getY();
        double length = Math.hypot(dx, dy);

        if (length == 0.0) {
            throw new IllegalArgumentException(
                    "anchor and directionPoint must differ"
            );
        }

        return new Point2D(
                anchor.getX() + dx / length * distance,
                anchor.getY() + dy / length * distance
        );
    }

    private static Point2D handleCenterInScene(
            NeuronView neuronView
    ) {
        Point2D bottomCenter = neuronView.localToScene(
                NeuronView.WIDTH / 2.0,
                NeuronView.HEIGHT
        );

        Point2D normalSample = neuronView.localToScene(
                NeuronView.WIDTH / 2.0,
                NeuronView.HEIGHT + NORMAL_SAMPLE
        );

        /*
         * The selected neuron draws a 2.5 px outline centered on the body
         * edge. The handle must leave the requested visual gap outside that
         * outline, even when the outline is not currently visible.
         *
         * NORMAL_SAMPLE is one local/world unit, so its scene-space length is
         * exactly the current uniform workspace scale. Converting the
         * selection-stroke half-width through that scale keeps the reserved
         * outline space correct at every zoom level.
         */
        double sceneUnitsPerLocalUnit =
                bottomCenter.distance(normalSample) / NORMAL_SAMPLE;
        double reservedSelectionStroke =
                NeuronView.SELECTED_STROKE_WIDTH / 2.0
                        * sceneUnitsPerLocalUnit;

        double centerOffset =
                HANDLE_GAP
                        + RotationHandleView.SIZE / 2.0
                        + reservedSelectionStroke;

        return offsetAlongNormal(
                bottomCenter,
                normalSample,
                centerOffset
        );
    }

    private Point2D toParent(
            NeuronView neuronView,
            double localX,
            double localY,
            Node coordinateSpace
    ) {
        Point2D scenePoint = neuronView.localToScene(
                localX,
                localY
        );
        return coordinateSpace.sceneToLocal(scenePoint);
    }

    private void positionMenu(
            VBox menu,
            Point2D rectangleCenter,
            double rectangleTopY
    ) {
        menu.applyCss();
        menu.autosize();

        double width = menu.getWidth();
        double height = menu.getHeight();

        if (width <= 0.0) {
            width = menu.prefWidth(-1);
        }
        if (height <= 0.0) {
            height = menu.prefHeight(-1);
        }

        menu.setManaged(false);
        menu.resize(width, height);
        menu.relocate(
                rectangleCenter.getX() - width / 2.0,
                rectangleTopY - height - MENU_GAP
        );
    }

    private double rectangleTopYInParent(
            NeuronView neuronView,
            Pane overlay
    ) {
        double topLeft = toParent(
                neuronView,
                0.0,
                0.0,
                overlay
        ).getY();
        double topRight = toParent(
                neuronView,
                NeuronView.WIDTH,
                0.0,
                overlay
        ).getY();
        double bottomLeft = toParent(
                neuronView,
                0.0,
                NeuronView.HEIGHT,
                overlay
        ).getY();
        double bottomRight = toParent(
                neuronView,
                NeuronView.WIDTH,
                NeuronView.HEIGHT,
                overlay
        ).getY();

        return Math.min(
                Math.min(topLeft, topRight),
                Math.min(bottomLeft, bottomRight)
        );
    }
}
