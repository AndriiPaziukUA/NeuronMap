package com.example.neuronmap.controller;

import javafx.geometry.Point2D;

/**
 * Містить математичні обчислення, потрібні для визначення кута обертання нейрона.
 */
public final class NeuronRotationMath {

    /**
     * Повертає результат операції «нейрон обертання».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private NeuronRotationMath() {
    }

/**
 * Повертає результат операції «кут градуси».
 *
 * @param center центр області.
 *
 * @param pointer значення, що визначає відповідну операцію для цієї операції.
 *
 * @return числове значення, визначене методом.
 */
public static double pointerAngleDegrees(
            Point2D center,
            Point2D pointer
    ) {
        if (center == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("center must not be null");
        }
        if (pointer == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("pointer must not be null");
        }

        double dx = pointer.getX() - center.getX();
        double dy = pointer.getY() - center.getY();

        return normalize360(
                Math.toDegrees(Math.atan2(-dx, dy))
        );
    }

/**
 * Повертає результат операції «обертання для перетягування».
 *
 * @param initialRotationDegrees значення, що визначає обертання градуси для цієї операції.
 *
 * @param initialPointerAngleDegrees значення, що визначає кут градуси для цієї операції.
 *
 * @param currentPointerAngleDegrees значення, що визначає поточний кут градуси для цієї операції.
 *
 * @return числове значення, визначене методом.
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

/**
 * Повертає результат операції «відповідну операцію».
 *
 * @param from значення, що визначає із для цієї операції.
 *
 * @param to значення, що визначає до для цієї операції.
 *
 * @return числове значення, визначене методом.
 */
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

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param degrees кут повороту в градусах.
     *
     * @return числове значення, визначене методом.
     */
    public static double normalize360(double degrees) {
        requireFinite(degrees, "degrees");
        double normalized = degrees % 360.0;
        return normalized < 0.0
                ? normalized + 360.0
                : normalized;
    }

    /**
     * Виконує операцію «потребувати».
     *
     * @param value значення, яке потрібно передати або зберегти.
     *
     * @param name назва або текстове імʼя обʼєкта.
     */
    private static void requireFinite(
            double value,
            String name
    ) {
        if (!Double.isFinite(value)) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    name + " must be finite"
            );
        }
    }
}
