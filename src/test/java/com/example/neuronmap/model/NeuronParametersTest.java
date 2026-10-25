package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє незмінність, значення за замовчуванням і централізовану валідацію параметрів нейрона. */
class NeuronParametersTest {

    /** Перевіряє доступ до всіх параметрів та порівняння незмінних значень. */
    @Test
    void storesParametersAsOneValue() {
        NeuronParameters parameters = new NeuronParameters(7, 4, 3, 2);
        assertEquals(7, parameters.signalStrength());
        assertEquals(4, parameters.activationThreshold());
        assertEquals(3, parameters.activeTicks());
        assertEquals(2, parameters.delayTicks());
        assertEquals(new NeuronParameters(7, 4, 3, 2), parameters);
    }

    /** Перевіряє збереження сумісності старих конструкторів із затримкою в один такт. */
    @Test
    void defaultsDelayAndActiveTicksToOneForExistingCallers() {
        assertEquals(1, new NeuronParameters(7, 4).activeTicks());
        assertEquals(1, new NeuronParameters(7, 4).delayTicks());
        assertEquals(1, new NeuronParameters(7, 4, 3).delayTicks());
        assertEquals(1, new Neuron("n", NeuronType.EXCITATORY, 0).activeTicks());
        assertEquals(1, new Neuron("n", NeuronType.EXCITATORY, 0).delayTicks());
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

    /** Перевіряє, що затримка не може бути меншою за один такт і валідовується також сеттером нейрона. */
    @Test
    void rejectsNonPositiveDelayTicks() {
        assertThrows(IllegalArgumentException.class, () -> new NeuronParameters(1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new NeuronParameters(1, 1, 1, -1));
        Neuron neuron = new Neuron("n", NeuronType.EXCITATORY, 0);
        assertThrows(IllegalArgumentException.class, () -> neuron.setDelayTicks(0));
        assertThrows(IllegalArgumentException.class, () -> neuron.setDelayTicks(-1));
    }

    /** Перевіряє, що зміна параметра не втрачає налаштовані активні такти й затримку. */
    @Test
    void changingOneParameterPreservesOtherParameters() {
        Neuron neuron = new Neuron("n", NeuronType.EXCITATORY, 0, 3, 2, 5, 4);
        neuron.setSignalStrength(6);
        neuron.setActivationThreshold(4);
        neuron.setActiveTicks(7);
        assertEquals(4, neuron.delayTicks());
        neuron.setDelayTicks(3);
        assertEquals(6, neuron.signalStrength());
        assertEquals(4, neuron.activationThreshold());
        assertEquals(7, neuron.activeTicks());
        assertEquals(3, neuron.delayTicks());
    }
}
