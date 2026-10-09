package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє збереження та зміну напрямку нейрона.
 */
class NeuronPresentationDirectionTest {

    /**
     * Перевіряє очікувану поведінку: напрямок до.
     */
    @Test
    void directionDefaultsToNormal() {
        Neuron neuron = new Neuron(
                "n1",
                NeuronType.EXCITATORY,
                0
        );

        NeuronPresentation presentation =
                new NeuronPresentation(
                        neuron,
                        10,
                        20,
                        0
                );

        assertFalse(
                presentation.directionReversed()
        );
    }

    /**
     * Перевіряє очікувану поведінку: напрямок і.
     */
    @Test
    void directionCanBeToggledAndStored() {
        Neuron neuron = new Neuron(
                "n1",
                NeuronType.EXCITATORY,
                0
        );

        NeuronPresentation presentation =
                new NeuronPresentation(
                        neuron,
                        10,
                        20,
                        0
                );

        presentation.toggleDirection();
        assertTrue(presentation.directionReversed());

        presentation.setDirectionReversed(false);
        assertFalse(presentation.directionReversed());
    }
}
