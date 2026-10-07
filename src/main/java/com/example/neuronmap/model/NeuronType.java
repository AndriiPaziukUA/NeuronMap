package com.example.neuronmap.model;

public enum NeuronType {
    EXCITATORY("Активуючий", 1),
    INHIBITORY("Гальмуючий", -1);

    private final String label;
    private final int signalWeight;

    NeuronType(String label, int signalWeight) {
        this.label = label;
        this.signalWeight = signalWeight;
    }

    public String label() {
        return label;
    }

    public int signalWeight() {
        return signalWeight;
    }

    public NeuronType toggled() {
        return this == EXCITATORY ? INHIBITORY : EXCITATORY;
    }
}
