package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє сумісність способу активації з наявними параметрами та межі відсоткового порога. */
class NeuronActivationModeTest {

    /** Переконується, що старі конструктори й нові нейрони за замовчуванням використовують силу сигналів. */
    @Test
    void defaultsToSignalStrengthForExistingAndNewNeurons() {
        assertEquals(NeuronActivationMode.SIGNAL_STRENGTH,
                new NeuronParameters(2, 1).activationMode());
        assertEquals(NeuronActivationMode.SIGNAL_STRENGTH,
                new NeuronParameters(2, 1, 3, 4).activationMode());
        assertEquals(NeuronActivationMode.SIGNAL_STRENGTH,
                new Neuron("n", NeuronType.EXCITATORY, 0).activationMode());
    }

    /** Переконується, що відсотковий поріг приймає обидві крайні межі, включно з нулем. */
    @Test
    void acceptsInclusivePercentageRangeForCountMode() {
        assertEquals(0, new NeuronParameters(1, 0, 1, 1, NeuronActivationMode.SIGNAL_COUNT)
                .activationThreshold());
        assertEquals(100, new NeuronParameters(1, 100, 1, 1, NeuronActivationMode.SIGNAL_COUNT)
                .activationThreshold());
    }

    /** Відхиляє відсотки поза межами 0–100, не послаблюючи додатний поріг старого режиму. */
    @Test
    void rejectsThresholdOutsideRangeForSelectedMode() {
        assertThrows(IllegalArgumentException.class,
                () -> new NeuronParameters(1, -1, 1, 1, NeuronActivationMode.SIGNAL_COUNT));
        assertThrows(IllegalArgumentException.class,
                () -> new NeuronParameters(1, 101, 1, 1, NeuronActivationMode.SIGNAL_COUNT));
        assertThrows(IllegalArgumentException.class,
                () -> new NeuronParameters(1, 0, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH));
    }

    /** Переконується, що часткова зміна параметрів не скидає режим активації нейрона. */
    @Test
    void changingNumericSettingsPreservesActivationMode() {
        Neuron neuron = new Neuron("n", NeuronType.EXCITATORY, 0,
                new NeuronParameters(2, 0, 3, 4, NeuronActivationMode.SIGNAL_COUNT));

        neuron.setSignalStrength(5);
        neuron.setActiveTicks(6);
        neuron.setDelayTicks(7);

        assertEquals(NeuronActivationMode.SIGNAL_COUNT, neuron.activationMode());
        assertEquals(0, neuron.activationThreshold());
        assertEquals(5, neuron.signalStrength());
        assertEquals(6, neuron.activeTicks());
        assertEquals(7, neuron.delayTicks());
    }

    /** Забороняє перехід до відсоткового режиму, доки поріг не буде приведено до допустимого відсотка. */
    @Test
    void rejectsModeChangeWhenCurrentThresholdIsNotValidForNewMode() {
        Neuron neuron = new Neuron("n", NeuronType.EXCITATORY, 0, 1, 120);
        assertThrows(IllegalArgumentException.class, () -> neuron.setActivationMode(NeuronActivationMode.SIGNAL_COUNT));
        assertEquals(NeuronActivationMode.SIGNAL_STRENGTH, neuron.activationMode());
    }
}
