package com.example.neuronmap.util;

import javafx.geometry.Point2D;

/**
 * Перетворює позицію вказівника у видимій області полотна на координати світової системи.
 */
public final class ViewportWorldCoordinates {

    private ViewportWorldCoordinates() {
    }

    /**
     * Перетворює координати точки області перегляду на світові з урахуванням масштабу й панорамування.
     *
     * @param viewportX горизонтальна координата точки у видимій області полотна.
     * @param viewportY вертикальна координата точки у видимій області полотна.
     * @param zoom поточний коефіцієнт масштабування.
     * @param panX поточне горизонтальне зміщення полотна.
     * @param panY поточне вертикальне зміщення полотна.
     * @return координати відповідної точки у світовій системі.
     * @throws IllegalArgumentException якщо координати або параметри трансформації не є скінченними,
     *                                  або коефіцієнт масштабування не є додатним.
     */
    public static Point2D calculate(
            double viewportX,
            double viewportY,
            double zoom,
            double panX,
            double panY
    ) {
        if (!Double.isFinite(viewportX) || !Double.isFinite(viewportY)) {
            throw new IllegalArgumentException("viewport coordinates must be finite");
        }
        if (!Double.isFinite(zoom) || zoom <= 0.0) {
            throw new IllegalArgumentException("zoom must be positive and finite");
        }
        if (!Double.isFinite(panX) || !Double.isFinite(panY)) {
            throw new IllegalArgumentException("pan coordinates must be finite");
        }

        return new Point2D(
                (viewportX - panX) / zoom,
                (viewportY - panY) / zoom
        );
    }
}
