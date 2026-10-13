package com.example.neuronmap.util;

import javafx.geometry.Point2D;

/**
 * Містить геометричні допоміжні операції для меж, напрямків і масштабування камери.
 */
public final class GeometryUtils {

    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 3.5;

    private GeometryUtils() {
    }

    /**
     * Обмежує коефіцієнт масштабування підтримуваним діапазоном.
     *
     * @param zoom коефіцієнт масштабування.
     */
    public static double clampZoom(double zoom) {
        return Math.max(
                MIN_ZOOM,
                Math.min(MAX_ZOOM, zoom)
        );
    }

    /**
     * Повертає одиничний вектор, перпендикулярний до заданого напрямку.
     *
     * @param direction вектор напрямку, для якого обчислюють перпендикуляр.
     *
     * @return одиничний вектор, перпендикулярний до заданого напрямку.
     */
    public static Point2D perpendicularUnitVector(
            Point2D direction
    ) {
        double length = direction.magnitude();

        if (length == 0.0) {

            return new Point2D(0, 1);
        }

        return new Point2D(
                -direction.getY() / length,
                direction.getX() / length
        );
    }
}
