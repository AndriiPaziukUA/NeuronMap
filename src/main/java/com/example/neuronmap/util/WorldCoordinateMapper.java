package com.example.neuronmap.util;

import javafx.geometry.Point2D;

import java.util.Objects;

/**
 * Перетворює координати локальної області перегляду на координати світу з урахуванням стану камери.
 */
public final class WorldCoordinateMapper {

    private WorldCoordinateMapper() {
    }

    /**
     * Перетворює точку області перегляду на координати світу.
     *
     * @param viewportPoint точка в локальних координатах області перегляду.
     * @param zoom поточний коефіцієнт масштабування камери.
     * @param panX горизонтальне зміщення камери.
     * @param panY вертикальне зміщення камери.
     * @return точка в координатах світу.
     */
    public static Point2D viewportToWorld(
            Point2D viewportPoint,
            double zoom,
            double panX,
            double panY
    ) {
        Objects.requireNonNull(viewportPoint, "viewportPoint");
        if (!Double.isFinite(zoom) || zoom <= 0.0) {
            throw new IllegalArgumentException("zoom must be finite and positive");
        }

        return new Point2D(
                (viewportPoint.getX() - panX) / zoom,
                (viewportPoint.getY() - panY) / zoom
        );
    }
}
