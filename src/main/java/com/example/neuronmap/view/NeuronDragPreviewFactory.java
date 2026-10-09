package com.example.neuronmap.view;

import com.example.neuronmap.model.NeuronType;

/**
 * Створює візуальне попереднє подання нейрона для перетягування з панелі інструментів.
 */
public final class NeuronDragPreviewFactory {

    private NeuronDragPreviewFactory() {
    }

    /**
     * Створює  з переданих параметрів.
     *
     * @param type тип нейрона або елемента.
     */
    public static NeuronDragPreviewView create(NeuronType type) {

        return new NeuronDragPreviewView(type);
    }
}
