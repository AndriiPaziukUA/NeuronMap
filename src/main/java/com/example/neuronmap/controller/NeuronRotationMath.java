package com.example.neuronmap.controller;

import javafx.geometry.Point2D;

/** Pure rotation math shared by the rotation interaction. */
public final class NeuronRotationMath {

    private NeuronRotationMath() {
    }

    /**
     * Converts a pointer position around the neuron center to the same
     * clockwise angle convention used by JavaFX Node#setRotate.
     *
     * <p>The handle starts below the rectangle, therefore the bottom direction
     * is 0 degrees, left is 90, top is 180 and right is 270.</p>
     */
    public static double pointerAngleDegrees(
            Point2D center,
            Point2D pointer
    ) {
        if (center == null) {
            throw new IllegalArgumentException("center must not be null");
        }
        if (pointer == null) {
            throw new IllegalArgumentException("pointer must not be null");
        }

        double dx = pointer.getX() - center.getX();
        double dy = pointer.getY() - center.getY();

        return normalize360(
                Math.toDegrees(Math.atan2(-dx, dy))
        );
    }

    /**
     * Applies only the pointer's rotation delta to the rotation that existed
     * when the user pressed the handle. This prevents the first drag event
     * from changing the neuron's rotation unexpectedly.
     */
    public static double rotationForDrag(
            double initialRotationDegrees,
            double initialPointerAngleDegrees,
            double currentPointerAngleDegrees
    ) {
        requireFinite(initialRotationDegrees, "initialRotationDegrees");
        requireFinite(
                initialPointerAngleDegrees,
                "initialPointerAngleDegrees"
        );
        requireFinite(
                currentPointerAngleDegrees,
                "currentPointerAngleDegrees"
        );

        double delta = shortestSignedDelta(
                initialPointerAngleDegrees,
                currentPointerAngleDegrees
        );

        return normalize360(initialRotationDegrees + delta);
    }

    /** Returns the shortest signed angle from {@code from} to {@code to}. */
    public static double shortestSignedDelta(
            double from,
            double to
    ) {
        requireFinite(from, "from");
        requireFinite(to, "to");

        double delta = normalize360(to - from);
        if (delta > 180.0) {
            delta -= 360.0;
        }
        return delta;
    }

    public static double normalize360(double degrees) {
        requireFinite(degrees, "degrees");
        double normalized = degrees % 360.0;
        return normalized < 0.0
                ? normalized + 360.0
                : normalized;
    }

    private static void requireFinite(
            double value,
            String name
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    name + " must be finite"
            );
        }
    }
}
