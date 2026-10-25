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

    /** Перевіряє, що видалення цільового нейрона не запускає порожній такт для сигналу, який утратив адресата. */
    @Test
    void deletedPendingTargetDoesNotStartAnEmptyTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron b = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        Neuron c = model.createNeuron(NeuronType.EXCITATORY, 200, 0);
        assertTrue(model.createConnection(a.id(), b.id()));
        assertTrue(model.createConnection(b.id(), c.id()));
        SimulationSession session = SimulationSession.manual(model, a.id());

        SimulationStep first = session.nextStep();

        assertNotNull(first);
        assertTrue(first.activatedNeuronIds().contains(a.id()));
        assertEquals(1, first.nextInputSums().get(b.id()));
        model.removeNeuron(b.id());

        assertNull(session.nextStep());
        assertTrue(session.isFinished());
        assertFalse(session.hasPendingWork());
        assertEquals(BigInteger.ONE, session.tick());
    }

    /** Перевіряє, що сигнал нижче порога не породжує наступний такт без іншої очікуваної роботи. */
    @Test
    void subthresholdSignalDoesNotScheduleAnEmptyTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        target.setActivationThreshold(2);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep emission = session.nextStep();

        assertNotNull(emission);

        assertEquals(1, emission.nextInputSums().get(target.id()));
        assertTrue(emission.nextActivatedNeuronIds().isEmpty());
        assertTrue(session.isFinished());
        assertFalse(session.hasPendingWork());
        assertNull(session.nextStep());
        assertEquals(BigInteger.ONE, session.tick());
    }

    /** Перевіряє, що перетинання порога зберігає наступний такт та активує цільовий нейрон. */
    @Test
    void thresholdCrossingSchedulesTheNextTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        source.setSignalStrength(2);
        target.setActivationThreshold(2);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep emission = session.nextStep();

        assertNotNull(emission);

        assertTrue(emission.nextActivatedNeuronIds().contains(target.id()));
        assertTrue(session.hasPendingWork());
        SimulationStep activation = session.nextStep();
        assertNotNull(activation);
        assertTrue(activation.activatedNeuronIds().contains(target.id()));
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

        assertNotNull(first);
        assertEquals(BigInteger.ZERO, first.tick());
        assertEquals(Set.of(a.id()), first.activatedNeuronIds());
        assertTrue(session.queueManualStart(c.id()));
        SimulationStep second = session.nextStep();
        assertNotNull(second);
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
        SimulationStep initial = session.nextStep();
        assertNotNull(initial);
        assertEquals(BigInteger.ZERO, initial.tick());
        SimulationStep last = session.nextStep();
        assertNotNull(last);
        assertEquals(BigInteger.ONE, last.tick());
        assertTrue(session.isFinished());
        assertTrue(session.queueManualStart(c.id()));
        assertFalse(session.isFinished());
        SimulationStep resumed = session.nextStep();
        assertNotNull(resumed);
        assertEquals(BigInteger.TWO, resumed.tick());
        assertEquals(Set.of(c.id()), resumed.activatedNeuronIds());
    }

    /** Перевіряє, що новий ручний запуск не використовує повторно сигнал нижче порога з уже завершеного сеансу. */
    @Test
    void manualStartAfterSubthresholdFinishDiscardsTheOldSignal() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        Neuron newSource = model.createNeuron(NeuronType.EXCITATORY, 300, 0);
        target.setActivationThreshold(2);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        assertNotNull(session.nextStep());
        assertTrue(session.isFinished());
        assertTrue(session.queueManualStart(newSource.id()));

        SimulationStep restarted = session.nextStep();

        assertNotNull(restarted);
        assertTrue(restarted.inputSums().isEmpty());
        assertEquals(Set.of(newSource.id()), restarted.activatedNeuronIds());
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
        assertNotNull(step);
        assertEquals(Set.of(b.id()), step.activatedNeuronIds());
        assertEquals(1, step.nextInputSums().get(a.id()));
    }

    /** Перевіряє, що джерело передає задану кількість сигналів і не створює фінальний такт без активації адресата. */
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
        assertTrue(session.isFinished());
        assertFalse(session.hasPendingWork());
        assertNull(session.nextStep());
        assertEquals(BigInteger.valueOf(4), session.tick());
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
        SimulationStep first = session.nextStep();
        assertNotNull(first);
        assertEquals(1, first.nextInputSums().get(target.id()));
        assertTrue(session.queueManualStart(retrigger.id()));
        SimulationStep second = session.nextStep();
        assertNotNull(second);
        assertEquals(1, second.nextInputSums().get(target.id()));
        SimulationStep retriggered = session.nextStep();
        assertNotNull(retriggered);
        assertTrue(retriggered.activatedNeuronIds().contains(source.id()));
        assertEquals(Set.of(source.id()), retriggered.emittingNeuronIds());
        assertEquals(1, retriggered.nextInputSums().get(target.id()));
        for (int tick = 0; tick < 3; tick++) {
            SimulationStep step = session.nextStep();
            assertNotNull(step);
            assertEquals(1, step.nextInputSums().get(target.id()));
            assertEquals(Set.of(source.id()), step.emittingNeuronIds());
        }
        assertTrue(session.isFinished());
        assertNull(session.nextStep());
    }

    /** Перевіряє, що типовий нейрон зберігає один активний такт і не породжує порожню доставку нижче порога. */
    @Test
    void defaultDurationKeepsPreviousSingleTickBehavior() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());
        SimulationStep step = session.nextStep();
        assertNotNull(step);
        assertEquals(1, step.nextInputSums().get(target.id()));
        assertTrue(session.isFinished());
        assertFalse(session.hasPendingWork());
        assertNull(session.nextStep());
        assertEquals(BigInteger.ONE, session.tick());
    }
}
