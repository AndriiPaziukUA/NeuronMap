package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє незмінність і централізовану валідацію параметрів нейрона.
 */
class NeuronParametersTest {

    /**
     * Перевіряє, що обидва параметри доступні з одного значення та порівнюються за значенням.
     */
    @Test
    void storesParametersAsOneValue() {
        NeuronParameters parameters = new NeuronParameters(7, 4);

        assertEquals(7, parameters.signalStrength());
        assertEquals(4, parameters.activationThreshold());
        assertEquals(new NeuronParameters(7, 4), parameters);
    }

    /**
     * Перевіряє відхилення нульової та від'ємної сили сигналу.
     */
    @Test
    void rejectsNonPositiveSignalStrength() {
        assertThrows(IllegalArgumentException.class, () -> new NeuronParameters(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new NeuronParameters(-1, 1));
    }

    /**
     * Перевіряє відхилення нульового та від'ємного порога активації.
     */
    @Test
    void rejectsNonPositiveActivationThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new NeuronParameters(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new NeuronParameters(1, -1));
    }
}
