package com.example.neuronmap.util;

import com.example.neuronmap.model.CameraState;
import javafx.geometry.Point2D;

/**
 * Містить геометричні допоміжні операції для меж, напрямків і масштабування камери.
 */
public final class GeometryUtils {

    private GeometryUtils() {
    }

    /**
     * Обмежує скінченний коефіцієнт масштабування діапазоном, визначеним контрактом стану камери.
     * Некоректні нечислові та нескінченні значення не нормалізуються, а відхиляються.
     *
     * @param zoom коефіцієнт масштабування.
     * @return масштаб у межах допустимого діапазону.
     * @throws IllegalArgumentException якщо значення не є скінченним.
     */
    public static double clampZoom(double zoom) {
        if (!Double.isFinite(zoom)) {
            throw new IllegalArgumentException("zoom must be finite");
        }
        return Math.max(CameraState.MIN_ZOOM, Math.min(CameraState.MAX_ZOOM, zoom));
    }

    /**
     * Повертає одиничний вектор, перпендикулярний до заданого напрямку.
     *
     * @param direction вектор напрямку, для якого обчислюють перпендикуляр.
     * @return одиничний вектор, перпендикулярний до заданого напрямку, або стандартний вектор для нульового напрямку.
     */
    public static Point2D perpendicularUnitVector(Point2D direction) {
        double length = direction.magnitude();
        if (length == 0.0) {
            return new Point2D(0, 1);
        }
        return new Point2D(-direction.getY() / length, direction.getX() / length);
    }
}
