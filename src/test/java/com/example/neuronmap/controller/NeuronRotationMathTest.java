package com.example.neuronmap.controller;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Перевіряє відповідність кутів обертання JavaFX та відсутність стрибків під час початку й продовження перетягування ручки.
 */
class NeuronRotationMathTest {

    private static final Point2D CENTER = new Point2D(70.0, 37.0);

    /**
     * Перевіряє відповідність напрямків ручки конвенції кутів обертання JavaFX.
     */
    @Test
    void handleDirectionsUseJavaFxRotationConvention() {
        assertEquals(
                0.0,
                NeuronRotationMath.pointerAngleDegrees(
                        CENTER,
                        new Point2D(70.0, 100.0)
                ),
                1e-9
        );
        assertEquals(
                90.0,
                NeuronRotationMath.pointerAngleDegrees(
                        CENTER,
                        new Point2D(10.0, 37.0)
                ),
                1e-9
        );
        assertEquals(
                180.0,
                NeuronRotationMath.pointerAngleDegrees(
                        CENTER,
                        new Point2D(70.0, -20.0)
                ),
                1e-9
        );
        assertEquals(
                270.0,
                NeuronRotationMath.pointerAngleDegrees(
                        CENTER,
                        new Point2D(120.0, 37.0)
                ),
                1e-9
        );
    }

    /**
     * Перевіряє відсутність стрибка або перевороту кута, якщо перша подія перетягування збігається з точкою натискання.
     */
    @Test
    void firstDragAtThePressPositionDoesNotFlipOrJump() {
        assertEquals(
                90.0,
                NeuronRotationMath.rotationForDrag(
                        90.0,
                        90.0,
                        90.0
                ),
                1e-9
        );
        assertEquals(
                180.0,
                NeuronRotationMath.rotationForDrag(
                        180.0,
                        180.0,
                        180.0
                ),
                1e-9
        );
    }

    /**
     * Перевіряє відповідність обертання зміщенню покажчика без стрибка через протилежний напрямок.
     */
    @Test
    void rotationFollowsPointerDeltaWithoutCrossingFlip() {
        assertEquals(
                120.0,
                NeuronRotationMath.rotationForDrag(
                        90.0,
                        90.0,
                        120.0
                ),
                1e-9
        );
        assertEquals(
                20.0,
                NeuronRotationMath.rotationForDrag(
                        350.0,
                        350.0,
                        20.0
                ),
                1e-9
        );
    }
}
