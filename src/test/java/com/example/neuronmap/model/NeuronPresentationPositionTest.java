package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Перевіряє, що подання нейрона зберігає координати як один MapPosition під час переміщення.
 */
class NeuronPresentationPositionTest {

    /**
     * Перевіряє, що встановлення позиції замінює value object, а переміщення створює нову позицію.
     */
    @Test
    void positionChangesAsSingleValueWhenMoved() {
        Neuron neuron = new Neuron("n1", NeuronType.EXCITATORY, 0);
        NeuronPresentation presentation = new NeuronPresentation(neuron, 10.0, 20.0, 0.0);
        MapPosition original = presentation.position();

        presentation.setPosition(new MapPosition(50.0, 70.0));
        assertNotSame(original, presentation.position());
        assertEquals(new MapPosition(50.0, 70.0), presentation.position());

        presentation.moveBy(-5.0, 12.0);
        assertEquals(new MapPosition(45.0, 82.0), presentation.position());
        assertEquals(45.0, presentation.x());
        assertEquals(82.0, presentation.y());
    }
}
