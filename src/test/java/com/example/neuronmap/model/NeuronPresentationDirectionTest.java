package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє типовий напрямок нейрона та збереження перемкнутого напрямку.
 */
class NeuronPresentationDirectionTest {

    /**
     * Перевіряє, що напрямок нейрона за замовчуванням не розвернутий.
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
     * Перевіряє зміну напрямку та зберігання нового значення в поданні.
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
