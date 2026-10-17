package com.example.neuronmap.application;

import com.example.neuronmap.model.CameraState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє, що EditorState зберігає камеру єдиним CameraState і атомарно застосовує зміни її компонентів.
 */
class EditorStateCameraStateTest {

    /**
     * Перевіряє узгодженість спільного стану камери зі зворотно сумісними методами доступу.
     */
    @Test
    void exposesOneCoherentCameraState() {
        EditorState state = new EditorState(1.5, 20.0, -10.0);

        assertEquals(new CameraState(1.5, 20.0, -10.0), state.cameraState());
        assertEquals(state.cameraState().zoom(), state.zoom());
        assertEquals(state.cameraState().panX(), state.panX());
        assertEquals(state.cameraState().panY(), state.panY());

        state.setZoom(2.0);
        state.setPanX(35.0);
        state.setPanY(40.0);
        assertEquals(new CameraState(2.0, 35.0, 40.0), state.cameraState());
    }

    /**
     * Перевіряє, що некоректна зміна не пошкоджує попередній стан камери.
     */
    @Test
    void invalidCameraUpdateLeavesPreviousStateUntouched() {
        EditorState state = new EditorState(1.0, 5.0, 8.0);
        CameraState previous = state.cameraState();

        assertThrows(IllegalArgumentException.class, () -> state.setZoom(Double.NaN));
        assertEquals(previous, state.cameraState());
        assertThrows(IllegalArgumentException.class, () -> state.setPanX(Double.POSITIVE_INFINITY));
        assertEquals(previous, state.cameraState());
    }
}
