package com.example.neuronmap.controller;

import com.example.neuronmap.util.NeuronRotationMath;
import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє математичні обчислення геометричного helper-а обертання, які використовує контролер ручки.
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
                NeuronRotationMath.rotationForDrag(90.0, 90.0, 90.0),
                1e-9
        );
        assertEquals(
                180.0,
                NeuronRotationMath.rotationForDrag(180.0, 180.0, 180.0),
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
                NeuronRotationMath.rotationForDrag(90.0, 90.0, 120.0),
                1e-9
        );
        assertEquals(
                20.0,
                NeuronRotationMath.rotationForDrag(350.0, 350.0, 20.0),
                1e-9
        );
    }

    /**
     * Перевіряє нормалізацію від’ємних кутів та найкоротшу різницю через межу повного оберту.
     */
    @Test
    void normalizesNegativeAnglesAndUsesShortestSignedDelta() {
        assertEquals(350.0, NeuronRotationMath.normalize360(-10.0), 1e-9);
        assertEquals(30.0, NeuronRotationMath.shortestSignedDelta(350.0, 20.0), 1e-9);
        assertEquals(-30.0, NeuronRotationMath.shortestSignedDelta(20.0, 350.0), 1e-9);
    }

    /**
     * Перевіряє відхилення нескінченних і невизначених значень кутів.
     */
    @Test
    void rejectsNonFiniteAngles() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NeuronRotationMath.normalize360(Double.NaN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> NeuronRotationMath.rotationForDrag(
                        0.0,
                        Double.POSITIVE_INFINITY,
                        30.0
                )
        );
    }
}
