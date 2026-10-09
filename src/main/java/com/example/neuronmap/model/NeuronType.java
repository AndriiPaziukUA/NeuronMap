package com.example.neuronmap.model;

/**
 * Визначає тип нейрона та знак сигналу, який він створює.
 */
public enum NeuronType {
    EXCITATORY("Активуючий", 1),
    INHIBITORY("Гальмуючий", -1);

    private final String label;
    private final int signalWeight;

    /**
     * Створює обʼєкт NeuronType та ініціалізує його початковий стан.
     *
     * @param label значення, що визначає підпис для цієї операції.
     *
     * @param signalWeight значення, що визначає сигнал вага для цієї операції.
     */
    NeuronType(String label, int signalWeight) {
        this.label = label;
        this.signalWeight = signalWeight;
    }

    /**
     * Повертає результат операції «підпис».
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    public String label() {
        return label;
    }

    /**
     * Повертає результат операції «сигнал вага».
     *
     * @return числове значення, визначене методом.
     */
    public int signalWeight() {
        return signalWeight;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronType toggled() {
        return this == EXCITATORY ? INHIBITORY : EXCITATORY;
    }
}
