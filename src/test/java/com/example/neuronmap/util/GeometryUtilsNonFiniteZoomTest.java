package com.example.neuronmap.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє, що обмеження масштабу нормалізує скінченні числа, але не приховує нечислові значення.
 */
final class GeometryUtilsNonFiniteZoomTest {

    /**
     * Перевіряє відхилення NaN та обох нескінченностей до застосування математичного обмеження.
     */
    @Test
    void rejectsNonFiniteZoomValues() {
        assertThrows(IllegalArgumentException.class, () -> GeometryUtils.clampZoom(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> GeometryUtils.clampZoom(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> GeometryUtils.clampZoom(Double.NEGATIVE_INFINITY));
    }

    /**
     * Перевіряє, що скінченний масштаб за межами діапазону й надалі обмежується допустимими межами.
     */
    @Test
    void clampsFiniteZoomValuesAndPreservesSupportedValues() {
        assertEquals(0.25, GeometryUtils.clampZoom(-10.0));
        assertEquals(3.5, GeometryUtils.clampZoom(10.0));
        assertEquals(1.5, GeometryUtils.clampZoom(1.5));
    }
}
