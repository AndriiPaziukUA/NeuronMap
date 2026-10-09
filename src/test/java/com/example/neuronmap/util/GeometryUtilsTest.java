package com.example.neuronmap.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевіряє обмеження коефіцієнта масштабування допустимим діапазоном.
 */
class GeometryUtilsTest {

    /**
     * Перевіряє обмеження масштабу допустимим діапазоном.
     */
    @Test
    void clampsZoomToSupportedRange() {
        assertEquals(
                0.25,
                GeometryUtils.clampZoom(0.01)
        );

        assertEquals(
                3.5,
                GeometryUtils.clampZoom(50.0)
        );

        assertEquals(
                1.5,
                GeometryUtils.clampZoom(1.5)
        );
    }
}
