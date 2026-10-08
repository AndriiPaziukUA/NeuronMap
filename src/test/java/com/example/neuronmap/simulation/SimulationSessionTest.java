package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Set;

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
                a.id()
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
        assertTrue(session.isFinished());
    }

    @Test
    void cyclicSignalContinuesBeyondPreviousTickLimit() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron b = model.createNeuron(NeuronType.EXCITATORY, 100, 0);

        assertTrue(model.createConnection(a.id(), b.id()));
        assertTrue(model.createConnection(b.id(), a.id()));

        SimulationSession session = SimulationSession.manual(model, a.id());

        for (int i = 0; i < 600; i++) {
            SimulationStep step = session.nextStep();
            assertNotNull(step);
            assertEquals(BigInteger.valueOf(i), step.tick());
            assertFalse(step.activatedNeuronIds().isEmpty());
            assertFalse(session.isFinished());
        }

        assertEquals(BigInteger.valueOf(600), session.tick());
        assertTrue(session.hasPendingWork());
    }

    @Test
    void manualStartsQueuedBetweenTicksAreActivatedTogether() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron b = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        Neuron c = model.createNeuron(NeuronType.EXCITATORY, 300, 0);
        Neuron d = model.createNeuron(NeuronType.EXCITATORY, 400, 0);

        assertTrue(model.createConnection(a.id(), b.id()));
        assertTrue(model.createConnection(b.id(), a.id()));
        assertTrue(model.createConnection(c.id(), d.id()));
        assertTrue(model.createConnection(d.id(), c.id()));

        SimulationSession session = SimulationSession.manual(model, a.id());

        SimulationStep first = session.nextStep();
        assertEquals(BigInteger.ZERO, first.tick());
        assertEquals(Set.of(a.id()), first.activatedNeuronIds());

        assertTrue(session.queueManualStart(c.id()));

        SimulationStep second = session.nextStep();
        assertEquals(BigInteger.ONE, second.tick());
        assertEquals(
                Set.of(b.id(), c.id()),
                second.activatedNeuronIds()
        );
        assertEquals(1, second.nextInputSums().get(a.id()));
        assertEquals(1, second.nextInputSums().get(d.id()));
    }

    @Test
    void queueingNewSourceDoesNotResetGlobalTickAfterSessionBecameIdle() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron b = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        Neuron c = model.createNeuron(NeuronType.EXCITATORY, 300, 0);

        assertTrue(model.createConnection(a.id(), b.id()));

        SimulationSession session = SimulationSession.manual(model, a.id());

        assertEquals(BigInteger.ZERO, session.nextStep().tick());
        SimulationStep last = session.nextStep();
        assertEquals(BigInteger.ONE, last.tick());
        assertTrue(session.isFinished());

        assertTrue(session.queueManualStart(c.id()));
        assertFalse(session.isFinished());

        SimulationStep resumed = session.nextStep();
        assertEquals(BigInteger.TWO, resumed.tick());
        assertEquals(Set.of(c.id()), resumed.activatedNeuronIds());
    }

    @Test
    void neuronActivatedByManualStartAndInputEmitsOnlyOnce() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron b = model.createNeuron(NeuronType.EXCITATORY, 100, 0);

        assertTrue(model.createConnection(a.id(), b.id()));
        assertTrue(model.createConnection(b.id(), a.id()));

        SimulationSession session = SimulationSession.manual(model, a.id());
        session.nextStep();

        assertTrue(session.queueManualStart(b.id()));
        SimulationStep step = session.nextStep();

        assertEquals(Set.of(b.id()), step.activatedNeuronIds());
        assertEquals(1, step.nextInputSums().get(a.id()));
    }
}
