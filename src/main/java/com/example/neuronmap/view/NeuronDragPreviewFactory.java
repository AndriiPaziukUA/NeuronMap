package com.example.neuronmap.view;

import com.example.neuronmap.model.NeuronType;

/** Creates the translucent neuron shown during toolbar drag. */
public final class NeuronDragPreviewFactory {

    private NeuronDragPreviewFactory() {
    }

    public static NeuronDragPreviewView create(NeuronType type) {
        return new NeuronDragPreviewView(type);
    }
}
