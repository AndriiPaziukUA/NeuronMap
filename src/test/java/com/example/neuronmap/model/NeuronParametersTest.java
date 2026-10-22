package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє незмінність, значення за замовчуванням і централізовану валідацію параметрів нейрона. */
class NeuronParametersTest {

    /** Перевіряє доступ до всіх параметрів та порівняння незмінних значень. */
    @Test
    void storesParametersAsOneValue() {
        NeuronParameters parameters = new NeuronParameters(7, 4, 3);
        assertEquals(7, parameters.signalStrength());
        assertEquals(4, parameters.activationThreshold());
        assertEquals(3, parameters.activeTicks());
        assertEquals(new NeuronParameters(7, 4, 3), parameters);
    }

    /** Перевіряє сумісність двопараметрового конструктора та типове значення тривалості. */
    @Test
    void defaultsActiveTicksToOneForExistingCallers() {
        assertEquals(1, new NeuronParameters(7, 4).activeTicks());
        assertEquals(1, new Neuron("n", NeuronType.EXCITATORY, 0).activeTicks());
    }

    /** Перевіряє відхилення нульової та від'ємної сили сигналу. */
    @Test
    void rejectsNonPositiveSignalStrength() {
        assertThrows(IllegalArgumentException.class, () -> new NeuronParameters(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new NeuronParameters(-1, 1, 1));
    }

    /** Перевіряє відхилення нульового та від'ємного порога активації. */
    @Test
    void rejectsNonPositiveActivationThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new NeuronParameters(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new NeuronParameters(1, -1, 1));
    }

    /** Перевіряє відхилення нульової та від'ємної кількості активних тактів. */
    @Test
    void rejectsNonPositiveActiveTicks() {
        assertThrows(IllegalArgumentException.class, () -> new NeuronParameters(1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new NeuronParameters(1, 1, -1));
        Neuron neuron = new Neuron("n", NeuronType.EXCITATORY, 0);
        assertThrows(IllegalArgumentException.class, () -> neuron.setActiveTicks(0));
        assertThrows(IllegalArgumentException.class, () -> neuron.setActiveTicks(-1));
    }

    /** Перевіряє, що зміна одного параметра зберігає інші параметри. */
    @Test
    void changingOneParameterPreservesActiveTicks() {
        Neuron neuron = new Neuron("n", NeuronType.EXCITATORY, 0, 3, 2, 5);
        neuron.setSignalStrength(6);
        assertEquals(5, neuron.activeTicks());
        neuron.setActivationThreshold(4);
        assertEquals(5, neuron.activeTicks());
        assertEquals(6, neuron.signalStrength());
        assertEquals(4, neuron.activationThreshold());
    }
}
