package com.example.neuronmap.model;

/**
 * Описує типи нейронів та вагу сигналу, яку кожен тип передає наступному нейрону.
 */
public enum NeuronType {
    EXCITATORY("Активуючий"),
    INHIBITORY("Гальмуючий");

    private final String label;

    NeuronType(String label) {
        this.label = label;
    }

    /**
     * Повертає мітку типу нейрона.
     *
     * @return мітку типу нейрона.
     */
    public String label() {
        return label;
    }
}
