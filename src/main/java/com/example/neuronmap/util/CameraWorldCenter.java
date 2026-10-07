package com.example.neuronmap.util;

import javafx.geometry.Point2D;

/** Calculates the world coordinate at the visual center of a viewport. */
public final class CameraWorldCenter {

    private CameraWorldCenter() {
    }

    public static Point2D calculate(
            double viewportWidth,
            double viewportHeight,
            double zoom,
            double panX,
            double panY
    ) {
        requirePositive(viewportWidth, "viewportWidth");
        requirePositive(viewportHeight, "viewportHeight");
        requirePositive(zoom, "zoom");

        return new Point2D(
                (viewportWidth / 2.0 - panX) / zoom,
                (viewportHeight / 2.0 - panY) / zoom
        );
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    name + " must be positive and finite"
            );
        }
    }
}
