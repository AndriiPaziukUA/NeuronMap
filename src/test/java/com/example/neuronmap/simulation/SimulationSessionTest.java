package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationSessionTest {

    @Test
    void deletedPendingTargetDoesNotParticipateInLaterTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron b = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        Neuron c = model.createNeuron(NeuronType.EXCITATORY, 200, 0);

        assertTrue(model.createConnection(a.id(), b.id()));
        assertTrue(model.createConnection(b.id(), c.id()));

        SimulationSession session = SimulationSession.manual(
                model,
                a.id(),
                16
        );

        SimulationStep first = session.nextStep();
        assertTrue(first.activatedNeuronIds().contains(a.id()));
        assertEquals(1, first.nextInputSums().get(b.id()));

        model.removeNeuron(b.id());

        SimulationStep second = session.nextStep();
        assertNotNull(second);
        assertTrue(second.inputSums().containsKey(b.id()));
        assertTrue(second.activatedNeuronIds().isEmpty());
        assertTrue(second.nextInputSums().isEmpty());
    }
}
