package com.example.neuronmap.util;

import javafx.geometry.Point2D;

/**
 * Обчислює координату світової системи, що опиняється в центрі видимої області камери.
 */
public final class CameraWorldCenter {

    private CameraWorldCenter() {
    }

    /**
     * Перетворює центр області перегляду на координати світової системи з урахуванням масштабу й панорамування.
     *
     * @param viewportWidth значення «viewport width», яке використовується в цьому методі.
     * @param viewportHeight значення «viewport height», яке використовується в цьому методі.
     * @param zoom коефіцієнт масштабування.
     * @param panX горизонтальне зміщення камери.
     * @param panY вертикальне зміщення камери.
     */
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

    /**
     * Перевіряє, що передане числове значення є скінченним і додатним.
     *
     * @param value значення, яке потрібно зберегти або перевірити.
     * @param name назва, яку потрібно перевірити або зберегти.
     */
    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {

            throw new IllegalArgumentException(
                    name + " must be positive and finite"
            );
        }
    }
}
