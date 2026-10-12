package com.example.neuronmap.util;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє єдине перетворення координат області перегляду на координати світу.
 */
final class WorldCoordinateMapperTest {

    private static final double EPSILON = 0.0001;

    @Test
    void returnsSameCoordinatesWhenCameraHasNoTransform() {
        Point2D worldPoint = WorldCoordinateMapper.viewportToWorld(
                new Point2D(120.0, 85.0),
                1.0,
                0.0,
                0.0
        );

        assertEquals(120.0, worldPoint.getX(), EPSILON);
        assertEquals(85.0, worldPoint.getY(), EPSILON);
    }

    @Test
    void reversesCameraPanAndZoom() {
        Point2D worldPoint = WorldCoordinateMapper.viewportToWorld(
                new Point2D(260.0, 140.0),
                2.0,
                20.0,
                40.0
        );

        assertEquals(120.0, worldPoint.getX(), EPSILON);
        assertEquals(50.0, worldPoint.getY(), EPSILON);
    }

    @Test
    void rejectsNonPositiveOrNonFiniteZoom() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WorldCoordinateMapper.viewportToWorld(
                        new Point2D(10.0, 20.0),
                        0.0,
                        0.0,
                        0.0
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> WorldCoordinateMapper.viewportToWorld(
                        new Point2D(10.0, 20.0),
                        Double.NaN,
                        0.0,
                        0.0
                )
        );
    }

    @Test
    void rejectsNullViewportPoint() {
        assertThrows(
                NullPointerException.class,
                () -> WorldCoordinateMapper.viewportToWorld(
                        null,
                        1.0,
                        0.0,
                        0.0
                )
        );
    }
}
