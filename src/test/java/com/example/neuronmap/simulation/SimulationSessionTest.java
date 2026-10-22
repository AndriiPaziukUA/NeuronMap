package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Перевіряє поширення сигналів, лічильники активності, цикли й ручні запуски. */
class SimulationSessionTest {

    /** Перевіряє, що видалений нейрон, на який уже очікує сигнал, не активується в наступному такті. */
    @Test
    void deletedPendingTargetDoesNotParticipateInLaterTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron b = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        Neuron c = model.createNeuron(NeuronType.EXCITATORY, 200, 0);
        assertTrue(model.createConnection(a.id(), b.id()));
        assertTrue(model.createConnection(b.id(), c.id()));
        SimulationSession session = SimulationSession.manual(model, a.id());

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

    /** Перевіряє продовження сигналу в циклічному графі довше за попередню межу тактів. */
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

    /** Перевіряє одночасну активацію нейронів, ручний запуск яких додано між тактами. */
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
        assertEquals(Set.of(b.id(), c.id()), second.activatedNeuronIds());
        assertEquals(1, second.nextInputSums().get(a.id()));
        assertEquals(1, second.nextInputSums().get(d.id()));
    }

    /** Перевіряє, що новий ручний запуск після простою не скидає глобальний лічильник тактів. */
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

    /** Перевіряє, що одночасне ручне й сигнальне активування нейрона не дублює вихідний сигнал. */
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

    /** Перевіряє, що перша активація одразу витрачає перший такт заданої тривалості. */
    @Test
    void emitsExactlyConfiguredNumberOfSignalsIncludingActivationTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        source.setActiveTicks(4);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());
        for (int tick = 0; tick < 4; tick++) {
            SimulationStep step = session.nextStep();
            assertNotNull(step);
            assertEquals(1, step.nextInputSums().get(target.id()), "signal on active tick " + tick);
            assertEquals(Set.of(source.id()), step.emittingNeuronIds());
            assertEquals(tick == 0, step.activatedNeuronIds().contains(source.id()));
        }
        SimulationStep finalDelivery = session.nextStep();
        assertNotNull(finalDelivery);
        assertEquals(1, finalDelivery.inputSums().get(target.id()));
        assertTrue(finalDelivery.nextInputSums().isEmpty());
        assertTrue(session.isFinished());
    }

    /** Перевіряє, що повторне перетинання порога перезапускає лічильник до повної тривалості. */
    @Test
    void newActivationRestartsRemainingActiveTicks() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        Neuron retrigger = model.createNeuron(NeuronType.EXCITATORY, 0, 100);
        source.setActiveTicks(4);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        assertTrue(model.createConnection(retrigger.id(), source.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());
        assertEquals(1, session.nextStep().nextInputSums().get(target.id()));
        assertTrue(session.queueManualStart(retrigger.id()));
        assertEquals(1, session.nextStep().nextInputSums().get(target.id()));
        SimulationStep retriggered = session.nextStep();
        assertTrue(retriggered.activatedNeuronIds().contains(source.id()));
        assertEquals(Set.of(source.id()), retriggered.emittingNeuronIds());
        assertEquals(1, retriggered.nextInputSums().get(target.id()));
        for (int tick = 0; tick < 3; tick++) {
            SimulationStep step = session.nextStep();
            assertEquals(1, step.nextInputSums().get(target.id()));
            assertEquals(Set.of(source.id()), step.emittingNeuronIds());
        }
        assertTrue(session.nextStep().nextInputSums().isEmpty());
        assertTrue(session.isFinished());
    }

    /** Перевіряє, що типовий нейрон зберігає попередню поведінку з одним активним тактом. */
    @Test
    void defaultDurationKeepsPreviousSingleTickBehavior() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());
        assertEquals(1, session.nextStep().nextInputSums().get(target.id()));
        assertTrue(session.nextStep().nextInputSums().isEmpty());
        assertTrue(session.isFinished());
    }
}
