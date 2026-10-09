package com.example.neuronmap.util;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє координату світу в центрі видимої області та валідацію параметрів камери.
 */
class CameraWorldCenterTest {

    /**
     * Перевіряє перетворення центру видимої області на координату світової системи.
     */
    @Test
    void calculatesWorldPointAtViewportCenter() {
        Point2D result = CameraWorldCenter.calculate(
                1200.0,
                700.0,
                2.0,
                100.0,
                -50.0
        );

        assertEquals(250.0, result.getX());
        assertEquals(200.0, result.getY());
    }

    /**
     * Перевіряє відхилення некоректних параметрів камери.
     */
    @Test
    void rejectsInvalidCameraValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CameraWorldCenter.calculate(
                        0.0, 700.0, 1.0, 0.0, 0.0
                )
        );
    }
}
