package com.example.neuronmap.util;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє перетворення координат курсора з області перегляду у світову систему. */
class ViewportWorldCoordinatesTest {

    /** Перевіряє перетворення позиції курсора за ненульового зміщення та масштабу. */
    @Test
    void calculatesWorldPointFromViewportPosition() {
        Point2D result = ViewportWorldCoordinates.calculate(
                350.0,
                200.0,
                2.0,
                50.0,
                -20.0
        );

        assertEquals(150.0, result.getX());
        assertEquals(110.0, result.getY());
    }

    /** Перевіряє перетворення, коли панорамування містить від'ємні координати. */
    @Test
    void calculatesWorldPointWithNegativePan() {
        Point2D result = ViewportWorldCoordinates.calculate(
                25.0,
                40.0,
                0.5,
                -15.0,
                -10.0
        );

        assertEquals(80.0, result.getX());
        assertEquals(100.0, result.getY());
    }

    /** Перевіряє відхилення некоректного масштабу й нескінченних координат. */
    @Test
    void rejectsInvalidTransformValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ViewportWorldCoordinates.calculate(10.0, 20.0, 0.0, 0.0, 0.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ViewportWorldCoordinates.calculate(Double.NaN, 20.0, 1.0, 0.0, 0.0)
        );
    }
}
