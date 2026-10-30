package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє доступність прапорця конкуренції через публічний контракт нейрона. */
final class NeuronStrongestNeuronOnlyTest {

    /** Перевіряє, що власник і його учасник читають одне спільне значення без доступу до внутрішнього сховища. */
    @Test
    void exposesSharedStrongestNeuronModeThroughNeuronApi() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        assertTrue(model.addStructureMember(structure.id(), member.id()));

        assertFalse(structure.strongestNeuronOnly());
        assertFalse(member.strongestNeuronOnly());

        model.structure(structure.id()).settings().setStrongestNeuronOnly(true);

        assertTrue(structure.strongestNeuronOnly());
        assertTrue(member.strongestNeuronOnly());

        model.structure(structure.id()).settings().setStrongestNeuronOnly(false);
        assertFalse(structure.strongestNeuronOnly());
        assertFalse(member.strongestNeuronOnly());
    }
}
