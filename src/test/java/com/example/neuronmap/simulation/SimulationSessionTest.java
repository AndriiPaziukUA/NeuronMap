package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Перевіряє поширення сигналів, лічильники активності, цикли, ручні запуски й індикатори структур. */
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

    /** Перевіряє, що джерело передає задану кількість сигналів і не створює такт доставки нижче порога. */
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

    /** Перевіряє, що типовий нейрон зберігає один активний такт і не створює зайвого такту доставки. */
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

    /** Перевіряє точкову анімацію зовнішніх зв'язків і підрахунок внутрішніх сигналів, включно з гальмівними. */
    @Test
    void structureLinksReflectOnlyActualCrossStructureMemberSignals() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructure = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron targetStructure = model.createNeuron(NeuronType.STRUCTURE, 200, 0);
        Neuron idleStructure = model.createNeuron(NeuronType.STRUCTURE, 400, 0);
        Neuron inhibitoryStructure = model.createNeuron(NeuronType.STRUCTURE, 600, 0);
        Neuron sourceMember = model.createNeuron(NeuronType.EXCITATORY, 0, 100);
        Neuron targetMember = model.createNeuron(NeuronType.EXCITATORY, 200, 100);
        Neuron idleMember = model.createNeuron(NeuronType.EXCITATORY, 400, 100);
        Neuron inhibitoryMember = model.createNeuron(NeuronType.EXCITATORY, 600, 100);
        assertTrue(model.addStructureMember(sourceStructure.id(), sourceMember.id()));
        assertTrue(model.addStructureMember(targetStructure.id(), targetMember.id()));
        assertTrue(model.addStructureMember(idleStructure.id(), idleMember.id()));
        assertTrue(model.addStructureMember(inhibitoryStructure.id(), inhibitoryMember.id()));
        inhibitoryMember.setType(NeuronType.INHIBITORY);
        targetMember.setActivationThreshold(1);
        idleMember.setActivationThreshold(2);

        assertTrue(model.createConnection(sourceMember.id(), targetMember.id()));
        assertTrue(model.createConnection(sourceMember.id(), idleMember.id()));
        assertTrue(model.createConnection(sourceStructure.id(), targetStructure.id()));
        assertTrue(model.createConnection(sourceStructure.id(), idleStructure.id()));
        assertTrue(model.createConnection(inhibitoryStructure.id(), idleStructure.id()));

        Connection activeStructureLink = model.connections().stream()
                .filter(connection -> connection.sourceId().equals(sourceStructure.id())
                        && connection.targetId().equals(targetStructure.id()))
                .findFirst().orElseThrow();
        Connection subthresholdStructureLink = model.connections().stream()
                .filter(connection -> connection.sourceId().equals(sourceStructure.id())
                        && connection.targetId().equals(idleStructure.id()))
                .findFirst().orElseThrow();
        Connection inhibitoryStructureLink = model.connections().stream()
                .filter(connection -> connection.sourceId().equals(inhibitoryStructure.id())
                        && connection.targetId().equals(idleStructure.id()))
                .findFirst().orElseThrow();

        SimulationSession session = SimulationSession.manual(model, sourceMember.id());
        assertTrue(session.queueManualStart(inhibitoryMember.id()));
        SimulationStep step = session.nextStep();

        assertNotNull(step);
        assertTrue(step.emittingNeuronIds().contains(sourceMember.id()));
        assertTrue(step.emittingNeuronIds().contains(inhibitoryMember.id()));
        assertFalse(step.emittingNeuronIds().contains(sourceStructure.id()));
        assertFalse(step.emittingNeuronIds().contains(inhibitoryStructure.id()));
        assertEquals(1, step.nextInputSums().get(targetMember.id()));
        assertEquals(1, step.nextInputSums().get(idleMember.id()));
        assertFalse(step.nextActivatedNeuronIds().contains(idleMember.id()));
        assertFalse(step.nextInputSums().containsKey(targetStructure.id()));
        assertFalse(step.nextInputSums().containsKey(idleStructure.id()));
        assertEquals(0, step.structureInputActivationCounts().get(sourceStructure.id()));
        assertEquals(1, step.structureInputActivationCounts().get(targetStructure.id()));
        assertEquals(0, step.structureInputActivationCounts().get(idleStructure.id()));
        assertEquals(1, step.structureEmittingNeuronCounts().get(sourceStructure.id()));
        assertEquals(0, step.structureEmittingNeuronCounts().get(targetStructure.id()));
        assertEquals(0, step.structureEmittingNeuronCounts().get(idleStructure.id()));
        assertEquals(1, step.structureEmittingNeuronCounts().get(inhibitoryStructure.id()));
        assertEquals(Set.of(activeStructureLink.id(), subthresholdStructureLink.id()),
                step.structureConnectionIdsToAnimate());
        assertTrue(step.structureConnectionIdsToAnimate().contains(subthresholdStructureLink.id()),
                "структурний імпульс показується навіть тоді, коли внутрішній сигнал нижче порога активації");
        assertFalse(step.structureConnectionIdsToAnimate().contains(inhibitoryStructureLink.id()));
    }

    /** Перевіряє, що негайна ручна активація обчислює структурні індикатори й вибирає лише відповідний зв'язок. */
    @Test
    void immediateMemberActivationUpdatesStructureIndicators() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructure = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron targetStructure = model.createNeuron(NeuronType.STRUCTURE, 200, 0);
        Neuron idleStructure = model.createNeuron(NeuronType.STRUCTURE, 400, 0);
        Neuron sourceMember = model.createNeuron(NeuronType.EXCITATORY, 0, 100);
        Neuron targetMember = model.createNeuron(NeuronType.EXCITATORY, 200, 100);
        assertTrue(model.addStructureMember(sourceStructure.id(), sourceMember.id()));
        assertTrue(model.addStructureMember(targetStructure.id(), targetMember.id()));
        assertTrue(model.createConnection(sourceMember.id(), targetMember.id()));
        assertTrue(model.createConnection(sourceStructure.id(), targetStructure.id()));
        assertTrue(model.createConnection(sourceStructure.id(), idleStructure.id()));
        String activeConnectionId = model.connections().stream()
                .filter(connection -> connection.sourceId().equals(sourceStructure.id())
                        && connection.targetId().equals(targetStructure.id()))
                .map(Connection::id)
                .findFirst().orElseThrow();

        SimulationSession session = SimulationSession.manual(model, sourceMember.id());
        SimulationStep step = session.activateManualStartNow(sourceMember.id());

        assertNotNull(step);
        assertTrue(step.emittingNeuronIds().contains(sourceMember.id()));
        assertEquals(1, step.structureEmittingNeuronCounts().get(sourceStructure.id()));
        assertEquals(1, step.structureInputActivationCounts().get(targetStructure.id()));
        assertEquals(Set.of(activeConnectionId), step.structureConnectionIdsToAnimate());
    }

    /** Перевіряє, що лічильники й індикаторні зв'язки враховують звичайних нейронів у вкладеній структурі. */
    @Test
    void nestedStructureIndicatorsIncludeDescendantSignals() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron outerStructure = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron nestedStructure = model.createNeuron(NeuronType.STRUCTURE, 100, 0);
        Neuron targetStructure = model.createNeuron(NeuronType.STRUCTURE, 300, 0);
        Neuron sourceMember = model.createNeuron(NeuronType.EXCITATORY, 100, 100);
        Neuron targetMember = model.createNeuron(NeuronType.EXCITATORY, 300, 100);
        assertTrue(model.addStructureMember(outerStructure.id(), nestedStructure.id()));
        assertTrue(model.addStructureMember(nestedStructure.id(), sourceMember.id()));
        assertTrue(model.addStructureMember(targetStructure.id(), targetMember.id()));
        targetMember.setActivationThreshold(1);
        assertTrue(model.createConnection(sourceMember.id(), targetMember.id()));
        assertTrue(model.createConnection(outerStructure.id(), targetStructure.id()));
        assertTrue(model.createConnection(nestedStructure.id(), targetStructure.id()));

        Set<String> expectedStructuralConnections = model.connections().stream()
                .filter(connection -> connection.sourceId().equals(outerStructure.id())
                        || connection.sourceId().equals(nestedStructure.id()))
                .map(Connection::id)
                .collect(java.util.stream.Collectors.toSet());
        SimulationStep step = SimulationSession.manual(model, sourceMember.id()).nextStep();

        assertNotNull(step);
        // Ручний запуск sourceMember вмикає його вихід, але не вхідний індикатор.
        // Жовтим стає лише вхід targetMember, який отримав сигнал.
        assertEquals(0, step.structureInputActivationCounts().get(outerStructure.id()));
        assertEquals(0, step.structureInputActivationCounts().get(nestedStructure.id()));
        assertEquals(1, step.structureInputActivationCounts().get(targetStructure.id()));
        assertEquals(1, step.structureEmittingNeuronCounts().get(outerStructure.id()));
        assertEquals(1, step.structureEmittingNeuronCounts().get(nestedStructure.id()));
        assertEquals(0, step.structureEmittingNeuronCounts().get(targetStructure.id()));
        assertEquals(expectedStructuralConnections, step.structureConnectionIdsToAnimate());
    }

}
