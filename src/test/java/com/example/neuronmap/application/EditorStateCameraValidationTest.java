package com.example.neuronmap.application;

import com.example.neuronmap.model.CameraState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє, що стан редактора застосовує спільний контракт камери атомарно.
 */
final class EditorStateCameraValidationTest {

    /**
     * Перевіряє, що конструктор редактора не приймає некоректні масштаб або координати камери.
     */
    @Test
    void rejectsInvalidCameraValuesDuringConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new EditorState(Double.NaN, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EditorState(Double.POSITIVE_INFINITY, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EditorState(0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EditorState(CameraState.MAX_ZOOM + 1.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EditorState(1.0, Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EditorState(1.0, 0.0, Double.NEGATIVE_INFINITY));
    }

    /**
     * Перевіряє, що помилка встановлення масштабу або зміщення не змінює попередній коректний стан редактора.
     */
    @Test
    void keepsPreviousCameraStateWhenSetterRejectsValue() {
        EditorState state = new EditorState(1.0, 12.0, 34.0);

        assertThrows(IllegalArgumentException.class, () -> state.setZoom(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> state.setZoom(CameraState.MAX_ZOOM + 1.0));
        assertThrows(IllegalArgumentException.class, () -> state.setPanX(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> state.setPanY(Double.NaN));

        assertEquals(1.0, state.zoom());
        assertEquals(12.0, state.panX());
        assertEquals(34.0, state.panY());
    }
}
