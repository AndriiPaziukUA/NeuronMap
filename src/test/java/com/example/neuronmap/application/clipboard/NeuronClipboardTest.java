package com.example.neuronmap.application.clipboard;

import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NeuronClipboardTest {

    @Test
    void keepsOnlyTheLatestCopiedItem() {
        NeuronClipboard clipboard = new NeuronClipboard();

        clipboard.copy(
                List.of(data(NeuronType.EXCITATORY, 1.0)),
                false
        );
        clipboard.copy(
                List.of(data(NeuronType.INHIBITORY, 25.0)),
                false
        );

        assertTrue(clipboard.hasContent());
        assertEquals(
                NeuronType.INHIBITORY,
                clipboard.content().items().getFirst().type()
        );
        assertEquals(
                25.0,
                clipboard.content().items().getFirst().offsetX()
        );
    }

    @Test
    void emptyCopyClearsClipboard() {
        NeuronClipboard clipboard = new NeuronClipboard();
        clipboard.copy(
                List.of(data(NeuronType.EXCITATORY, 4.0)),
                false
        );

        clipboard.copy(List.of(), false);

        assertFalse(clipboard.hasContent());
        assertNull(clipboard.content());
    }

    @Test
    void clearRemovesContent() {
        NeuronClipboard clipboard = new NeuronClipboard();
        clipboard.copy(
                List.of(data(NeuronType.EXCITATORY, 8.0)),
                true
        );

        clipboard.clear();

        assertFalse(clipboard.hasContent());
    }

    private static NeuronClipboard.NeuronData data(
            NeuronType type,
            double offsetX
    ) {
        return new NeuronClipboard.NeuronData(
                type,
                1,
                1,
                offsetX,
                0.0,
                0.0,
                false
        );
    }
}
