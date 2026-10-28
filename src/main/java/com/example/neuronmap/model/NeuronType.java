package com.example.neuronmap.model;

/**
 * Описує типи нейронів та знак сигналу, який кожен тип передає наступному нейрону.
 */
public enum NeuronType {
    /** Звичайний тип, що передає додатний сигнал. */
    EXCITATORY("Excitatory"),
    /** Тип, що передає від'ємний сигнал. */
    INHIBITORY("Inhibitory"),
    /** Структурний тип із власним оформленням, який передає додатний сигнал. */
    STRUCTURE("Structure");

    private final String label;

    /**
     * Створює тип нейрона з базовою текстовою міткою.
     *
     * @param label англомовна мітка типу для загальних випадків відображення.
     */
    NeuronType(String label) {
        this.label = label;
    }

    /**
     * Повертає мітку типу нейрона.
     *
     * @return мітка типу нейрона.
     */
    public String label() {
        return label;
    }

    /**
     * Перевіряє, чи передає тип нейрона додатний сигнал і вважається активуючим.
     *
     * @return {@code true} для збуджувального та структурного типів; інакше {@code false}.
     */
    public boolean isActivating() {
        return this == EXCITATORY || this == STRUCTURE;
    }
}
