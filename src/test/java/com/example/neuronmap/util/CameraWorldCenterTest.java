package com.example.neuronmap.util;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє перетворення центра видимої області в координати карти.
 */
class CameraWorldCenterTest {

    /**
     * Перевіряє очікувану поведінку: обчислює карта видима область центр.
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
     * Перевіряє очікувану поведінку: відхиляє некоректний камера значення.
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
