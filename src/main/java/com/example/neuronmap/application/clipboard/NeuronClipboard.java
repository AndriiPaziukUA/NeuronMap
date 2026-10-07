package com.example.neuronmap.application.clipboard;

import com.example.neuronmap.model.NeuronType;

import java.util.List;

/**
 * In-memory clipboard for neuron copy/paste operations.
 *
 * <p>The clipboard belongs to the current application instance and is never
 * persisted or connected to the operating-system clipboard.</p>
 */
public final class NeuronClipboard {

    private ClipboardContent content;

    public boolean hasContent() {
        return content != null && !content.items().isEmpty();
    }

    public ClipboardContent content() {
        return content;
    }

    public void copy(List<NeuronData> items, boolean grouped) {
        if (items == null || items.isEmpty()) {
            clear();
            return;
        }

        content = new ClipboardContent(List.copyOf(items), grouped);
    }

    public void clear() {
        content = null;
    }

    public record ClipboardContent(List<NeuronData> items, boolean grouped) {
        public ClipboardContent {
            items = List.copyOf(items);
        }
    }

    public record NeuronData(
            NeuronType type,
            int signalStrength,
            int activationThreshold,
            double offsetX,
            double offsetY,
            double rotationDegrees,
            boolean directionReversed
    ) {
    }
}
