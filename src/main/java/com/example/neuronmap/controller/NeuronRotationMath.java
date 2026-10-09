package com.example.neuronmap.controller;

import javafx.geometry.Point2D;

/**
 * Містить геометричні обчислення кутів для ручки обертання, зокрема нормалізацію кутів і найкоротшу кутову різницю.
 */
public final class NeuronRotationMath {

    private NeuronRotationMath() {
    }

/**
 * Обчислює кут у градусах між центром нейрона та положенням покажчика.
 *
 * @param center центр нейрона у координатах сцени.
 * @param pointer положення покажчика миші у координатах сцени.
 */
public static double pointerAngleDegrees(
            Point2D center,
            Point2D pointer
    ) {
        if (center == null) {

            throw new IllegalArgumentException("center must not be null");
        }
        if (pointer == null) {

            throw new IllegalArgumentException("pointer must not be null");
        }

        double dx = pointer.getX() - center.getX();
        double dy = pointer.getY() - center.getY();

        return normalize360(
                Math.toDegrees(Math.atan2(-dx, dy))
        );
    }

/**
 * Обчислює новий кут нейрона з урахуванням зміни кута покажчика під час перетягування.
 *
 * @param initialRotationDegrees кут нейрона до початку перетягування.
 * @param initialPointerAngleDegrees кут покажчика на момент початку перетягування.
 * @param currentPointerAngleDegrees поточний кут покажчика під час перетягування.
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
 * Повертає найкоротшу знакову різницю між двома кутами.
 *
 * @param from початковий кут у градусах.
 * @param to кінцевий кут у градусах.
 *
 * @return найкоротшу знакову різницю між двома кутами.
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
     * Нормалізує кут до діапазону одного повного оберту.
     *
     * @param degrees кут у градусах.
     */
    public static double normalize360(double degrees) {
        requireFinite(degrees, "degrees");
        double normalized = degrees % 360.0;
        return normalized < 0.0
                ? normalized + 360.0
                : normalized;
    }

    /**
     * Перевіряє передумову «finite» та перериває операцію, якщо вона не виконується.
     *
     * @param value значення, яке потрібно зберегти або перевірити.
     * @param name назва, яку потрібно перевірити або зберегти.
     */
    private static void requireFinite(
            double value,
            String name
    ) {
        if (!Double.isFinite(value)) {

            throw new IllegalArgumentException(
                    name + " must be finite"
            );
        }
    }
}
