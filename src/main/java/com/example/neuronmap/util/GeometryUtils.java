package com.example.neuronmap.util;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.BoundingBox;

/**
 * Містить допоміжні обчислення геометрії та обмеження масштабу.
 */
public final class GeometryUtils {

    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 3.5;

    /**
     * Повертає результат операції «геометрія».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private GeometryUtils() {
    }

    /**
     * Повертає результат операції «обмежити масштаб».
     *
     * @param zoom значення, що визначає масштаб для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    public static double clampZoom(double zoom) {
        return Math.max(
                MIN_ZOOM,
                Math.min(MAX_ZOOM, zoom)
        );
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param a значення, що визначає відповідну операцію для цієї операції.
     *
     * @param b значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public static Bounds union(
            Bounds a,
            Bounds b
    ) {
        double minX = Math.min(a.getMinX(), b.getMinX());
        double minY = Math.min(a.getMinY(), b.getMinY());
        double maxX = Math.max(a.getMaxX(), b.getMaxX());
        double maxY = Math.max(a.getMaxY(), b.getMaxY());

        /**
         * Повертає результат операції «відповідну операцію».
         *
         * @param minX значення, що визначає відповідну операцію для цієї операції.
         *
         * @param minY значення, що визначає відповідну операцію для цієї операції.
         *
         * @param minX значення, що визначає відповідну операцію для цієї операції.
         *
         * @param minY значення, що визначає відповідну операцію для цієї операції.
         *
         * @return значення або обʼєкт, визначений описаною операцією.
         */
        return new BoundingBox(
                minX,
                minY,
                maxX - minX,
                maxY - minY
        );
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param direction напрямок обʼєкта.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public static Point2D perpendicularUnitVector(
            Point2D direction
    ) {
        double length = direction.magnitude();

        if (length == 0.0) {
            /**
             * Повертає результат операції «відповідну операцію».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            return new Point2D(0, 1);
        }

        return new Point2D(
                -direction.getY() / length,
                direction.getX() / length
        );
    }
}
