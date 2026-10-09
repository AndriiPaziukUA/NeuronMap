package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє координати, обертання та інші дані представлення нейрона.
 */
final class NeuronPresentationTest {

    /**
     * Перевіряє очікувану поведінку: зберігає положення обертання і напрямок.
     */
    @Test
    void storesPositionRotationAndDirection() {
        Neuron neuron = new Neuron(
                "neuron-1",
                NeuronType.EXCITATORY,
                0
        );

        NeuronPresentation presentation =
                new NeuronPresentation(
                        neuron,
                        100.0,
                        80.0,
                        15.0
                );

        assertEquals(100.0, presentation.x(), 0.0001);
        assertEquals(80.0, presentation.y(), 0.0001);
        assertEquals(15.0, presentation.rotationDegrees(), 0.0001);
        assertFalse(presentation.directionReversed());
    }

    /**
     * Перевіряє очікувану поведінку: рухається за.
     */
    @Test
    void movesByDelta() {
        Neuron neuron = new Neuron(
                "neuron-2",
                NeuronType.EXCITATORY,
                0
        );

        NeuronPresentation presentation =
                new NeuronPresentation(
                        neuron,
                        100.0,
                        80.0,
                        0.0
                );

        presentation.moveBy(25.0, -10.0);

        assertEquals(125.0, presentation.x(), 0.0001);
        assertEquals(70.0, presentation.y(), 0.0001);
    }

    /**
     * Перевіряє очікувану поведінку: задає обертання градуси.
     */
    @Test
    void setsRotationDegrees() {
        Neuron neuron = new Neuron(
                "neuron-3",
                NeuronType.EXCITATORY,
                0
        );

        NeuronPresentation presentation =
                new NeuronPresentation(
                        neuron,
                        100.0,
                        80.0,
                        0.0
                );

        presentation.setRotationDegrees(90.0);

        assertEquals(
                90.0,
                presentation.rotationDegrees(),
                0.0001
        );
    }

    /**
     * Перевіряє очікувану поведінку: перемикає напрямок.
     */
    @Test
    void togglesDirection() {
        Neuron neuron = new Neuron(
                "neuron-4",
                NeuronType.EXCITATORY,
                0
        );

        NeuronPresentation presentation =
                new NeuronPresentation(
                        neuron,
                        100.0,
                        80.0,
                        0.0
                );

        assertFalse(presentation.directionReversed());

        presentation.toggleDirection();

        assertTrue(presentation.directionReversed());

        presentation.toggleDirection();

        assertFalse(presentation.directionReversed());
    }
}
