package com.example.neuronmap.model;

/**
 * Описує типи нейронів та вагу сигналу, яку кожен тип передає наступному нейрону.
 */
public enum NeuronType {
    EXCITATORY("Активуючий", 1),
    INHIBITORY("Гальмуючий", -1);

    private final String label;
    private final int signalWeight;

    NeuronType(String label, int signalWeight) {
        this.label = label;
        this.signalWeight = signalWeight;
    }

    /**
     * Повертає мітку типу нейрона.
     *
     * @return мітку типу нейрона.
     */
    public String label() {
        return label;
    }

    /**
     * Повертає вагу сигналу, яку передає нейрон.
     *
     * @return вагу сигналу, яку передає нейрон.
     */
    public int signalWeight() {
        return signalWeight;
    }

    /**
     * Повертає альтернативний тип нейрона: збуджувальний для гальмівного й навпаки.
     *
     * @return альтернативний тип нейрона: збуджувальний для гальмівного й навпаки.
     */
    public NeuronType toggled() {
        return this == EXCITATORY ? INHIBITORY : EXCITATORY;
    }
}
