package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.model.StructureNeuronCreationMode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє затримку першого вихідного сигналу та її взаємодію з тривалістю активності нейрона. */
final class SimulationSessionDelayTest {

    /** Перевіряє, що затримка в один такт зберігає початкову поведінку передавання сигналу. */
    @Test
    void oneTickDelayEmitsDuringActivationTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));

        SimulationSession session = SimulationSession.manual(model, source.id());
        SimulationStep activation = session.nextStep();
        assertNotNull(activation, "Очікується результат першого такту симуляції");

        assertEquals(Set.of(source.id()), activation.emittingNeuronIds());
        assertEquals(1, activation.nextInputSums().get(target.id()));
        assertEquals(0, activation.tick().intValue());
        assertTrue(session.isFinished(), "Недостатній сигнал не повинен спричиняти ще один такт");
    }

    /** Перевіряє, що затримка у два такти пропускає перший такт активації перед передаванням сигналу. */
    @Test
    void twoTickDelaySkipsActivationTickAndEmitsOnFollowingTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        source.setDelayTicks(2);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep waiting = session.nextStep();
        assertNotNull(waiting, "Очікується результат такту очікування затримки");
        assertTrue(waiting.activatedNeuronIds().contains(source.id()));
        assertFalse(waiting.emittingNeuronIds().contains(source.id()));
        assertTrue(waiting.nextInputSums().isEmpty());
        assertTrue(waiting.remainingActiveTicks().isEmpty());
        assertTrue(session.hasPendingWork());

        SimulationStep emission = session.nextStep();
        assertNotNull(emission, "Очікується результат такту передавання сигналу");
        assertEquals(Set.of(source.id()), emission.emittingNeuronIds());
        assertEquals(1, emission.nextInputSums().get(target.id()));
        assertTrue(emission.remainingActiveTicks().isEmpty(),
                "Однотиковий період активності вже витрачено під час першого випуску сигналу");
        assertTrue(session.isFinished(),
                "Після затримки та випуску недостатнього сигналу доставка не створює додаткового такту");

        assertNull(session.nextStep());
    }

    /** Перевіряє, що тривалість активності відраховується лише після завершення затримки та першого випуску. */
    @Test
    void activeTickCounterStartsOnlyWhenFirstSignalIsActuallyEmitted() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        source.setDelayTicks(3);
        source.setActiveTicks(2);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep firstWaitingTick = session.nextStep();
        assertNotNull(firstWaitingTick, "Очікується результат першого такту затримки");
        assertTrue(firstWaitingTick.remainingActiveTicks().isEmpty());
        assertFalse(firstWaitingTick.emittingNeuronIds().contains(source.id()));
        SimulationStep secondWaitingTick = session.nextStep();
        assertNotNull(secondWaitingTick, "Очікується результат другого такту затримки");
        assertTrue(secondWaitingTick.remainingActiveTicks().isEmpty());
        assertFalse(secondWaitingTick.emittingNeuronIds().contains(source.id()));

        SimulationStep firstEmission = session.nextStep();
        assertNotNull(firstEmission, "Очікується результат першого такту випуску сигналу");
        assertEquals(Set.of(source.id()), firstEmission.emittingNeuronIds());
        assertEquals(1, firstEmission.remainingActiveTicks().get(source.id()));
        assertEquals(1, firstEmission.nextInputSums().get(target.id()));

        SimulationStep secondEmission = session.nextStep();
        assertNotNull(secondEmission, "Очікується результат другого такту випуску сигналу");
        assertEquals(Set.of(source.id()), secondEmission.emittingNeuronIds());
        assertTrue(secondEmission.remainingActiveTicks().isEmpty());
        assertEquals(1, secondEmission.nextInputSums().get(target.id()));
        assertTrue(session.isFinished());

        assertNull(session.nextStep(), "Недостатній сигнал не повинен створювати порожній такт доставки");
    }

    /** Перевіряє, що повторна активація перезапускає затримку, не залишаючи старий активний лічильник. */
    @Test
    void reactivationRestartsDelayBeforeResumingEmission() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        Neuron retrigger = model.createNeuron(NeuronType.EXCITATORY, 0, 100);
        source.setDelayTicks(2);
        source.setActiveTicks(2);
        target.setActivationThreshold(100);
        retrigger.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        assertTrue(model.createConnection(retrigger.id(), source.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep initialDelayTick = session.nextStep();
        assertNotNull(initialDelayTick, "Очікується результат початкового такту затримки");
        assertFalse(initialDelayTick.emittingNeuronIds().contains(source.id()));
        assertTrue(session.queueManualStart(retrigger.id()));
        SimulationStep retriggerTick = session.nextStep();
        assertNotNull(retriggerTick, "Очікується результат такту повторного запуску");
        assertTrue(retriggerTick.activatedNeuronIds().contains(retrigger.id()));
        assertTrue(retriggerTick.emittingNeuronIds().contains(source.id()),
                "Первісна затримка джерела має завершитися незалежно від нового вхідного сигналу");

        SimulationStep sourceRetriggered = session.nextStep();
        assertNotNull(sourceRetriggered, "Очікується результат такту повторної активації джерела");
        assertTrue(sourceRetriggered.activatedNeuronIds().contains(source.id()));
        assertFalse(sourceRetriggered.emittingNeuronIds().contains(source.id()));
        assertTrue(sourceRetriggered.remainingActiveTicks().isEmpty());

        SimulationStep emission = session.nextStep();
        assertNotNull(emission, "Очікується результат такту після повторної затримки");
        assertTrue(emission.emittingNeuronIds().contains(source.id()));
        assertEquals(1, emission.nextInputSums().get(target.id()));
    }

    /** Перевіряє, що спільний центр не створюється під час затримки, а з'являється разом із першим сигналом. */
    @Test
    void delayedActivationCreatesSharedCenterOnlyWhenFirstSignalIsEmitted() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructure = createStructure(model);
        Neuron targetStructure = createStructure(model);
        Neuron source = addMember(model, sourceStructure);
        addMember(model, targetStructure);
        source.setDelayTicks(3);
        connectStructures(model, sourceStructure, targetStructure);
        int originalMemberCount = model.structure(targetStructure.id()).memberIds().size();
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep firstWaitingTick = session.nextStep();
        assertNotNull(firstWaitingTick);
        assertTrue(firstWaitingTick.activatedNeuronIds().contains(source.id()));
        assertFalse(firstWaitingTick.emittingNeuronIds().contains(source.id()));
        assertEquals(originalMemberCount, model.structure(targetStructure.id()).memberIds().size());
        assertTrue(firstWaitingTick.nextInputSums().isEmpty());

        SimulationStep secondWaitingTick = session.nextStep();
        assertNotNull(secondWaitingTick);
        assertFalse(secondWaitingTick.emittingNeuronIds().contains(source.id()));
        assertEquals(originalMemberCount, model.structure(targetStructure.id()).memberIds().size());

        SimulationStep emission = session.nextStep();
        assertNotNull(emission);
        assertTrue(emission.emittingNeuronIds().contains(source.id()));
        assertEquals(originalMemberCount + 1, model.structure(targetStructure.id()).memberIds().size());
        String centerId = findMembersConnectedFrom(model, targetStructure, source).iterator().next();
        assertEquals(1, emission.nextInputSums().get(centerId));
    }

    /** Перевіряє, що одночасно активовані нейрони з різними затримками отримують окремі центри випромінювання. */
    @Test
    void delayedAndReadySourcesAreNotGroupedIntoOneSharedCenter() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructureA = createStructure(model);
        Neuron sourceStructureB = createStructure(model);
        Neuron targetStructure = createStructure(model);
        Neuron delayedSource = addMember(model, sourceStructureA);
        Neuron readySource = addMember(model, sourceStructureB);
        addMember(model, targetStructure);
        delayedSource.setDelayTicks(3);
        connectStructures(model, sourceStructureA, targetStructure);
        connectStructures(model, sourceStructureB, targetStructure);

        SimulationSession session = SimulationSession.manual(model, delayedSource.id());
        assertTrue(session.queueManualStart(readySource.id()));
        SimulationStep readyEmission = session.nextStep();
        assertNotNull(readyEmission);
        assertFalse(readyEmission.emittingNeuronIds().contains(delayedSource.id()));
        assertTrue(readyEmission.emittingNeuronIds().contains(readySource.id()));

        Set<String> readyCenters = findMembersConnectedFrom(model, targetStructure, readySource);
        assertEquals(1, readyCenters.size());
        String readyCenterId = readyCenters.iterator().next();
        assertFalse(model.hasConnection(delayedSource.id(), readyCenterId));
        assertEquals(1, readyEmission.nextInputSums().get(readyCenterId));

        SimulationStep waitingTick = session.nextStep();
        assertNotNull(waitingTick);
        assertFalse(waitingTick.emittingNeuronIds().contains(delayedSource.id()));
        assertEquals(1, readyCenters.size());

        SimulationStep delayedEmission = session.nextStep();
        assertNotNull(delayedEmission);
        assertTrue(delayedEmission.emittingNeuronIds().contains(delayedSource.id()));
        Set<String> delayedCenters = findMembersConnectedFrom(model, targetStructure, delayedSource);
        assertEquals(1, delayedCenters.size());
        String delayedCenterId = delayedCenters.iterator().next();
        assertNotEquals(readyCenterId, delayedCenterId);
        assertFalse(model.hasConnection(readySource.id(), delayedCenterId));
        assertEquals(1, delayedEmission.nextInputSums().get(delayedCenterId));
    }

    /** Перевіряє, що режим за кількістю структур не об'єднує джерела, які випускають сигнали в різні такти. */
    @Test
    void structureCountModeDoesNotGroupSourcesWithDifferentEmissionTicks() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructureA = createStructure(model);
        Neuron sourceStructureB = createStructure(model);
        Neuron targetStructure = createStructure(model);
        Neuron delayedSource = addMember(model, sourceStructureA);
        Neuron readySource = addMember(model, sourceStructureB);
        addMember(model, targetStructure);
        delayedSource.setDelayTicks(3);
        model.structure(targetStructure.id()).settings()
                .setNeuronCreationMode(StructureNeuronCreationMode.STRUCTURE_COUNT);
        connectStructures(model, sourceStructureA, targetStructure);
        connectStructures(model, sourceStructureB, targetStructure);
        int originalMemberCount = model.structure(targetStructure.id()).memberIds().size();

        SimulationSession session = SimulationSession.manual(model, delayedSource.id());
        assertTrue(session.queueManualStart(readySource.id()));
        SimulationStep firstTick = session.nextStep();
        assertNotNull(firstTick);
        assertTrue(firstTick.emittingNeuronIds().contains(readySource.id()));
        assertFalse(firstTick.emittingNeuronIds().contains(delayedSource.id()));
        assertEquals(originalMemberCount, model.structure(targetStructure.id()).memberIds().size());

        SimulationStep waitingTick = session.nextStep();
        assertNotNull(waitingTick);
        assertFalse(waitingTick.emittingNeuronIds().contains(delayedSource.id()));
        assertEquals(originalMemberCount, model.structure(targetStructure.id()).memberIds().size());
        SimulationStep delayedEmission = session.nextStep();
        assertNotNull(delayedEmission);
        assertTrue(delayedEmission.emittingNeuronIds().contains(delayedSource.id()));
        assertFalse(delayedEmission.emittingNeuronIds().contains(readySource.id()));
        assertEquals(originalMemberCount, model.structure(targetStructure.id()).memberIds().size());
        assertTrue(findMembersConnectedFrom(model, targetStructure, delayedSource).isEmpty());
        assertTrue(findMembersConnectedFrom(model, targetStructure, readySource).isEmpty());
    }

    /** Перевіряє, що негайна ручна активація також враховує затримку до створення структурного центру. */
    @Test
    void immediateActivationWithDelayDefersSharedCenterUntilEmission() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructure = createStructure(model);
        Neuron targetStructure = createStructure(model);
        Neuron source = addMember(model, sourceStructure);
        addMember(model, targetStructure);
        source.setDelayTicks(3);
        connectStructures(model, sourceStructure, targetStructure);
        int originalMemberCount = model.structure(targetStructure.id()).memberIds().size();
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep activation = session.activateManualStartNow(source.id());
        assertNotNull(activation);
        assertFalse(activation.emittingNeuronIds().contains(source.id()));
        assertTrue(activation.nextInputSums().isEmpty());
        assertEquals(originalMemberCount, model.structure(targetStructure.id()).memberIds().size());

        SimulationStep firstWaitingTick = session.nextStep();
        assertNotNull(firstWaitingTick);
        assertFalse(firstWaitingTick.emittingNeuronIds().contains(source.id()));
        assertEquals(originalMemberCount, model.structure(targetStructure.id()).memberIds().size());
        SimulationStep secondWaitingTick = session.nextStep();
        assertNotNull(secondWaitingTick);
        assertFalse(secondWaitingTick.emittingNeuronIds().contains(source.id()));
        assertEquals(originalMemberCount, model.structure(targetStructure.id()).memberIds().size());

        SimulationStep emission = session.nextStep();
        assertNotNull(emission);
        assertTrue(emission.emittingNeuronIds().contains(source.id()));
        assertEquals(originalMemberCount + 1, model.structure(targetStructure.id()).memberIds().size());
        String centerId = findMembersConnectedFrom(model, targetStructure, source).iterator().next();
        assertEquals(1, emission.nextInputSums().get(centerId));
    }

    /** Створює порожній контейнер структури для перевірки маршрутизації внутрішніх випромінювачів. */
    private Neuron createStructure(NeuronMapModel model) {
        return model.createNeuron(NeuronType.STRUCTURE, 0, 0);
    }

    /** Додає звичайного нейрона до структури, щоб він брав участь у маршрутизації структурних імпульсів. */
    private Neuron addMember(NeuronMapModel model, Neuron structure) {
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        assertTrue(model.addStructureMember(structure.id(), member.id()));
        return member;
    }

    /** Створює напрямлений структурний зв'язок від джерела до структури-приймача. */
    private void connectStructures(NeuronMapModel model, Neuron source, Neuron target) {
        assertTrue(model.createConnection(source.id(), target.id()));
    }

    /** Знаходить учасників структури-приймача, з'єднаних із заданим нейроном-випромінювачем. */
    private Set<String> findMembersConnectedFrom(
            NeuronMapModel model,
            Neuron targetStructure,
            Neuron sourceNeuron
    ) {
        Set<String> result = new LinkedHashSet<>(model.structure(targetStructure.id()).memberIds());
        result.removeIf(memberId -> !model.hasConnection(sourceNeuron.id(), memberId));
        return result;
    }
}
