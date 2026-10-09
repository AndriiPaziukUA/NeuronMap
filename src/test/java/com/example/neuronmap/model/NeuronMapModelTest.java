package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевіряє правила цілісності карти, створення звʼязків і видалення обʼєктів.
 */
class NeuronMapModelTest {

    /**
     * Перевіряє очікувану поведінку: створити нейрон створює представлення.
     */
    @Test
    void createNeuronCreatesSeparatePresentation() {
        NeuronMapModel model = new NeuronMapModel();

        Neuron neuron = model.createNeuron(
                NeuronType.EXCITATORY,
                120.0,
                80.0,
                37.5
        );

        NeuronPresentation presentation =
                model.presentation(neuron.id());

        assertNotNull(presentation);
        assertSame(neuron, presentation.neuron());
        assertEquals(120.0, presentation.x());
        assertEquals(80.0, presentation.y());
        assertEquals(37.5, presentation.rotationDegrees());
        assertEquals(0, neuron.activation());
    }

    /**
     * Перевіряє очікувану поведінку: звʼязки і.
     */
    @Test
    void connectionsAreDirectedUniqueAndCannotBeSelfLoops() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(
                NeuronType.EXCITATORY,
                0,
                0
        );
        Neuron b = model.createNeuron(
                NeuronType.INHIBITORY,
                100,
                0
        );

        assertTrue(model.createConnection(a.id(), b.id()));
        assertFalse(model.createConnection(a.id(), b.id()));
        assertFalse(model.createConnection(a.id(), a.id()));
        assertFalse(model.hasConnection(b.id(), a.id()));
        assertEquals(1, model.connections().size());
    }

    /**
     * Перевіряє очікувану поведінку: звʼязок відхиляє.
     */
    @Test
    void lowLevelConnectionImportAlsoRejectsSelfLoops() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron neuron = model.createNeuron(
                NeuronType.EXCITATORY,
                0,
                0
        );

        model.addConnection(
                "self-loop",
                neuron.id(),
                neuron.id()
        );

        assertEquals(0, model.connections().size());
    }

    /**
     * Перевіряє очікувану поведінку: видаляє.
     */
    @Test
    void removesContactInBothDirections() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(
                NeuronType.EXCITATORY,
                0,
                0
        );
        Neuron b = model.createNeuron(
                NeuronType.EXCITATORY,
                100,
                0
        );
        Neuron c = model.createNeuron(
                NeuronType.EXCITATORY,
                200,
                0
        );

        assertTrue(model.createConnection(a.id(), b.id()));
        assertTrue(model.createConnection(b.id(), a.id()));
        assertTrue(model.createConnection(a.id(), c.id()));

        assertEquals(
                2,
                model.removeConnectionsBetween(a.id(), b.id())
        );

        assertFalse(model.hasConnection(a.id(), b.id()));
        assertFalse(model.hasConnection(b.id(), a.id()));
        assertTrue(model.hasConnection(a.id(), c.id()));
    }

    /**
     * Перевіряє очікувану поведінку: нейрон видаляє представлення звʼязки і група.
     */
    @Test
    void deletingNeuronRemovesPresentationConnectionsAndItsGroupMembership() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(
                NeuronType.EXCITATORY,
                0,
                0
        );
        Neuron b = model.createNeuron(
                NeuronType.INHIBITORY,
                100,
                0
        );
        Neuron c = model.createNeuron(
                NeuronType.EXCITATORY,
                200,
                0
        );

        model.createConnection(a.id(), b.id());
        model.createConnection(b.id(), c.id());
        model.createConnection(c.id(), b.id());
        model.createGroup(
                Set.of(a.id(), b.id(), c.id())
        );

        model.removeNeuron(b.id());

        assertNull(model.neuron(b.id()));
        assertNull(model.presentation(b.id()));
        assertTrue(model.connections().isEmpty());

        NeuronGroup remainingGroup =
                model.groupContaining(a.id());

        assertNotNull(remainingGroup);
        assertEquals(
                Set.of(a.id(), c.id()),
                remainingGroup.memberIds()
        );
        assertNull(model.groupContaining(b.id()));
    }
}
