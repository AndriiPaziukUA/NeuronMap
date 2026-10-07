package com.example.neuronmap.application;

import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditorStateTest {

    @Test
    void specialModesDoNotLoseCameraState() {
        EditorState state =
                new EditorState(1.75, -120.0, 80.0);

        state.enterCreateConnectionMode("a");

        assertEquals(1.75, state.zoom());
        assertEquals(-120.0, state.panX());
        assertEquals(80.0, state.panY());
        assertEquals(
                "a",
                state.connectionSourceId()
        );
    }

    @Test
    void selectionIsIndependentFromInteractionMode() {
        EditorState state =
                new EditorState(1.0, 0.0, 0.0);

        state.selectOnly("a");
        state.toggleSelection("b");
        state.enterDeleteConnectionMode("a");

        assertEquals(
                java.util.Set.of("a", "b"),
                state.selectedNeuronIds()
        );
        assertEquals(
                "a",
                state.deleteConnectionNeuronId()
        );
    }

    @Test
    void resetToIdleClearsTransientModeAndMenuSelection() {
        EditorState state =
                new EditorState(1.0, 0.0, 0.0);

        state.selectOnly("a");
        state.setSelectedNeuronForMenu("a");
        state.enterAddNeuronMode(
                NeuronType.EXCITATORY
        );

        state.resetToIdle();

        assertTrue(state.isIdle());
        assertNull(state.pendingNeuronType());
        assertNull(state.selectedNeuronForMenu());
        assertEquals(
                java.util.Set.of("a"),
                state.selectedNeuronIds()
        );
    }
}
