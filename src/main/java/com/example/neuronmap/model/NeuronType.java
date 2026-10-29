package com.example.neuronmap.model;

/**
 * Описує типи нейронів, семантику їхнього сигналу та участь у його поширенні.
 */
public enum NeuronType {
    /** Звичайний тип, що передає додатний сигнал. */
    EXCITATORY("Excitatory"),
    /** Тип, що передає від'ємний сигнал. */
    INHIBITORY("Inhibitory"),
    /** Індикаторний контейнер, який групує внутрішні нейрони й сам не передає сигнал. */
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
     * Визначає, чи вважається тип активуючим у правилах моделі та візуальному відображенні.
     * Фактичну участь у поширенні сигналу визначає окремий контракт типу.
     *
     * @return {@code true} для збуджувального та структурного типів; інакше {@code false}.
     */
    public boolean isActivating() {
        return this == EXCITATORY || this == STRUCTURE;
    }

    /**
     * Перевіряє, чи бере тип участь у фактичному поширенні сигналів між нейронами.
     * Структура залишається активуючим індикаторним типом для оформлення, але не є джерелом або адресатом сигналу.
     *
     * @return {@code true} для звичайних збуджувальних і гальмівних нейронів; {@code false} для структури.
     */
    public boolean participatesInSignalPropagation() {
        return this != STRUCTURE;
    }
}
