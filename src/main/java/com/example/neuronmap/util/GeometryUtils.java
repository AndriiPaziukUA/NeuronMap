package com.example.neuronmap.util;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.BoundingBox;

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
     * Обчислює найменші межі, що охоплюють обидві передані області.
     *
     * @param a значення «a», яке використовується в цьому методі.
     * @param b значення «b», яке використовується в цьому методі.
     */
    public static Bounds union(
            Bounds a,
            Bounds b
    ) {
        double minX = Math.min(a.getMinX(), b.getMinX());
        double minY = Math.min(a.getMinY(), b.getMinY());
        double maxX = Math.max(a.getMaxX(), b.getMaxX());
        double maxY = Math.max(a.getMaxY(), b.getMaxY());

        return new BoundingBox(
                minX,
                minY,
                maxX - minX,
                maxY - minY
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
