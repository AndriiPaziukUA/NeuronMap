package com.example.neuronmap.util;

import javafx.geometry.Point2D;

/**
 * Обчислює координати центра видимої області карти з урахуванням стану камери.
 */
public final class CameraWorldCenter {

    /**
     * Повертає результат операції «камера карта центр».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private CameraWorldCenter() {
    }

    /**
     * Обчислює «потрібні дані».
     *
     * @param viewportWidth значення, що визначає видима область ширина для цієї операції.
     *
     * @param viewportHeight значення, що визначає видима область висота для цієї операції.
     *
     * @param zoom значення, що визначає масштаб для цієї операції.
     *
     * @param panX значення, що визначає відповідну операцію для цієї операції.
     *
     * @param panY значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Виконує операцію «потребувати додатний».
     *
     * @param value значення, яке потрібно передати або зберегти.
     *
     * @param name назва або текстове імʼя обʼєкта.
     */
    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    name + " must be positive and finite"
            );
        }
    }
}
