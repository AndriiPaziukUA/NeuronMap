package com.example.neuronmap.view;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє розрахунок положення меню та накладок.
 */
class NeuronOverlayPositionerTest {

    /**
     * Перевіряє очікувану поведінку: зберігає.
     */
    @Test
    void offsetAlongNormalKeepsExactScreenDistance() {
        Point2D anchor = new Point2D(100.0, 200.0);
        Point2D direction = new Point2D(100.0, 203.0);

        Point2D result = NeuronOverlayPositioner.offsetAlongNormal(
                anchor,
                direction,
                19.0
        );

        assertEquals(100.0, result.getX(), 1e-9);
        assertEquals(219.0, result.getY(), 1e-9);
        assertEquals(
                19.0,
                anchor.distance(result),
                1e-9
        );
    }

    /**
     * Перевіряє очікувану поведінку: слідує.
     */
    @Test
    void offsetAlongNormalFollowsRotatedNormal() {
        Point2D anchor = new Point2D(100.0, 200.0);
        Point2D direction = new Point2D(97.0, 200.0);

        Point2D result = NeuronOverlayPositioner.offsetAlongNormal(
                anchor,
                direction,
                19.0
        );

        assertEquals(81.0, result.getX(), 1e-9);
        assertEquals(200.0, result.getY(), 1e-9);
    }

    /**
     * Перевіряє очікувану поведінку: відхилений.
     */
    @Test
    void zeroNormalIsRejected() {
        Point2D anchor = new Point2D(100.0, 200.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> NeuronOverlayPositioner.offsetAlongNormal(
                        anchor,
                        anchor,
                        19.0
                )
        );
    }
}
