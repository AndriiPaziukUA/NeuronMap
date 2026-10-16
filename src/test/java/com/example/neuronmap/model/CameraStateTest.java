package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє єдиний контракт допустимих значень стану камери.
 */
final class CameraStateTest {

    /**
     * Перевіряє, що камера приймає граничні та звичайні масштаби лише в межах підтримуваного діапазону.
     */
    @Test
    void acceptsZoomAtBothSupportedBounds() {
        assertDoesNotThrow(() -> new CameraState(CameraState.MIN_ZOOM, 0.0, 0.0));
        assertDoesNotThrow(() -> new CameraState(1.0, 12.5, -30.0));
        assertDoesNotThrow(() -> new CameraState(CameraState.MAX_ZOOM, 0.0, 0.0));
    }

    /**
     * Перевіряє відхилення нечислового, нескінченного, нульового, від’ємного та позадіапазонного масштабу.
     */
    @Test
    void rejectsInvalidZoomValues() {
        double[] invalidZooms = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                0.0,
                -1.0,
                CameraState.MIN_ZOOM / 2.0,
                CameraState.MAX_ZOOM + 0.01
        };

        for (double zoom : invalidZooms) {
            assertThrows(IllegalArgumentException.class, () -> new CameraState(zoom, 0.0, 0.0));
        }
    }

    /**
     * Перевіряє відхилення нескінченних і нечислових координат панорамування.
     */
    @Test
    void rejectsNonFinitePanCoordinates() {
        double[] invalidCoordinates = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double coordinate : invalidCoordinates) {
            assertThrows(IllegalArgumentException.class, () -> new CameraState(1.0, coordinate, 0.0));
            assertThrows(IllegalArgumentException.class, () -> new CameraState(1.0, 0.0, coordinate));
        }
    }
}
