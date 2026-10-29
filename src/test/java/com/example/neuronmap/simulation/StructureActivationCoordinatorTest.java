package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Перевіряє створення та повторне використання спільних центрів активації між структурами. */
class StructureActivationCoordinatorTest {

    /** Перевіряє, що одночасні активації двох структур надходять до одного щойно створеного центру. */
    @Test
    void createsOneSharedCenterForSimultaneouslyActivatedSourceStructures() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructureA = createStructure(model);
        Neuron sourceStructureB = createStructure(model);
        Neuron targetStructure = createStructure(model);
        Neuron sourceA = addMember(model, sourceStructureA);
        Neuron sourceB = addMember(model, sourceStructureB);
        addMember(model, targetStructure);
        connectStructures(model, sourceStructureA, targetStructure);
        connectStructures(model, sourceStructureB, targetStructure);
        int originalMemberCount = model.structure(targetStructure.id()).memberIds().size();

        SimulationSession session = SimulationSession.manual(model, sourceA.id());
        assertTrue(session.queueManualStart(sourceB.id()));
        SimulationStep step = Objects.requireNonNull(session.nextStep(), "simulation step");

        Set<String> centers = new LinkedHashSet<>(model.structure(targetStructure.id()).memberIds());
        centers.removeIf(neuronId -> !model.hasConnection(sourceA.id(), neuronId)
                || !model.hasConnection(sourceB.id(), neuronId));
        assertEquals(1, centers.size());
        assertEquals(originalMemberCount + 1, model.structure(targetStructure.id()).memberIds().size());
        String centerId = centers.iterator().next();
        assertEquals(2, step.nextInputSums().get(centerId));
        assertTrue(step.emittingNeuronIds().containsAll(Set.of(sourceA.id(), sourceB.id())));
    }

    /** Перевіряє, що центр із входами лише від поточно активованих джерел повторно використовується. */
    @Test
    void reusesExistingCenterConnectedOnlyFromActivatedSources() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructureA = createStructure(model);
        Neuron sourceStructureB = createStructure(model);
        Neuron targetStructure = createStructure(model);
        Neuron sourceA = addMember(model, sourceStructureA);
        Neuron sourceB = addMember(model, sourceStructureB);
        Neuron center = addMember(model, targetStructure);
        connectStructures(model, sourceStructureA, targetStructure);
        connectStructures(model, sourceStructureB, targetStructure);
        assertTrue(model.createConnection(sourceA.id(), center.id()));
        assertTrue(model.createConnection(sourceB.id(), center.id()));
        int originalMemberCount = model.structure(targetStructure.id()).memberIds().size();

        SimulationSession session = SimulationSession.manual(model, sourceA.id());
        assertTrue(session.queueManualStart(sourceB.id()));
        SimulationStep step = Objects.requireNonNull(session.nextStep(), "simulation step");

        assertEquals(originalMemberCount, model.structure(targetStructure.id()).memberIds().size());
        assertEquals(2, step.nextInputSums().get(center.id()));
    }

    /**
     * Перевіряє, що центр із додатковим неактивованим джерелом не використовується іншою групою активації.
     * Наявні спільні центри зберігаються, а для активованих A і B створюються власні центри у V та G.
     */
    @Test
    void createsPrivateCentersWhenExistingSharedCentersAlsoHaveInactiveIncomingSources() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructureA = createStructure(model);
        Neuron sourceStructureB = createStructure(model);
        Neuron inactiveSourceStructure = createStructure(model);
        Neuron targetStructureV = createStructure(model);
        Neuron targetStructureG = createStructure(model);
        Neuron sourceA = addMember(model, sourceStructureA);
        Neuron sourceB = addMember(model, sourceStructureB);
        Neuron inactiveSource = addMember(model, inactiveSourceStructure);
        Neuron existingCenterV = addMember(model, targetStructureV);
        Neuron existingCenterG = addMember(model, targetStructureG);
        connectStructures(model, sourceStructureA, targetStructureV);
        connectStructures(model, sourceStructureB, targetStructureV);
        connectStructures(model, inactiveSourceStructure, targetStructureV);
        connectStructures(model, sourceStructureA, targetStructureG);
        connectStructures(model, sourceStructureB, targetStructureG);
        connectStructures(model, inactiveSourceStructure, targetStructureG);
        assertTrue(model.createConnection(sourceA.id(), existingCenterV.id()));
        assertTrue(model.createConnection(sourceB.id(), existingCenterV.id()));
        assertTrue(model.createConnection(inactiveSource.id(), existingCenterV.id()));
        assertTrue(model.createConnection(sourceA.id(), existingCenterG.id()));
        assertTrue(model.createConnection(sourceB.id(), existingCenterG.id()));
        assertTrue(model.createConnection(inactiveSource.id(), existingCenterG.id()));
        int originalMemberCountV = model.structure(targetStructureV.id()).memberIds().size();
        int originalMemberCountG = model.structure(targetStructureG.id()).memberIds().size();

        SimulationSession session = SimulationSession.manual(model, sourceA.id());
        assertTrue(session.queueManualStart(sourceB.id()));
        SimulationStep step = Objects.requireNonNull(session.nextStep(), "simulation step");

        assertEquals(originalMemberCountV + 1, model.structure(targetStructureV.id()).memberIds().size());
        assertEquals(originalMemberCountG + 1, model.structure(targetStructureG.id()).memberIds().size());
        assertNotNull(model.neuron(existingCenterV.id()), "попередній центр V не видаляється");
        assertNotNull(model.neuron(existingCenterG.id()), "попередній центр G не видаляється");
        Set<String> newCentersV = findSharedCenters(model, targetStructureV.id(), sourceA.id(), sourceB.id());
        newCentersV.remove(existingCenterV.id());
        Set<String> newCentersG = findSharedCenters(model, targetStructureG.id(), sourceA.id(), sourceB.id());
        newCentersG.remove(existingCenterG.id());
        assertEquals(1, newCentersV.size());
        assertEquals(1, newCentersG.size());
        String newCenterV = newCentersV.iterator().next();
        String newCenterG = newCentersG.iterator().next();
        assertNotEquals(newCenterV, newCenterG);
        assertFalse(model.hasConnection(inactiveSource.id(), newCenterV));
        assertFalse(model.hasConnection(inactiveSource.id(), newCenterG));
        assertEquals(2, step.nextInputSums().get(existingCenterV.id()));
        assertEquals(2, step.nextInputSums().get(existingCenterG.id()));
        assertEquals(2, step.nextInputSums().get(newCenterV));
        assertEquals(2, step.nextInputSums().get(newCenterG));
    }

    /** Перевіряє, що частково спільний набір зв'язків не заважає створенню нового повного центру. */
    @Test
    void createsNewCenterWhenOneActivatedSourceIsNotConnectedToExistingMember() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructureA = createStructure(model);
        Neuron sourceStructureB = createStructure(model);
        Neuron targetStructure = createStructure(model);
        Neuron sourceA = addMember(model, sourceStructureA);
        Neuron sourceB = addMember(model, sourceStructureB);
        Neuron partialCenter = addMember(model, targetStructure);
        connectStructures(model, sourceStructureA, targetStructure);
        connectStructures(model, sourceStructureB, targetStructure);
        assertTrue(model.createConnection(sourceA.id(), partialCenter.id()));
        int originalMemberCount = model.structure(targetStructure.id()).memberIds().size();

        SimulationSession session = SimulationSession.manual(model, sourceA.id());
        assertTrue(session.queueManualStart(sourceB.id()));
        SimulationStep step = Objects.requireNonNull(session.nextStep(), "simulation step");

        assertEquals(originalMemberCount + 1, model.structure(targetStructure.id()).memberIds().size());
        assertEquals(1, step.nextInputSums().get(partialCenter.id()));
        Set<String> newCenters = model.structure(targetStructure.id()).memberIds().stream()
                .filter(neuronId -> !neuronId.equals(partialCenter.id()))
                .filter(neuronId -> model.hasConnection(sourceA.id(), neuronId)
                        && model.hasConnection(sourceB.id(), neuronId))
                .collect(Collectors.toSet());
        assertEquals(1, newCenters.size());
        assertEquals(2, step.nextInputSums().get(newCenters.iterator().next()));
    }

    /** Перевіряє незалежне створення спільного центру в кожній цільовій структурі. */
    @Test
    void createsCentersInParallelForMultipleTargetStructures() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructureA = createStructure(model);
        Neuron sourceStructureB = createStructure(model);
        Neuron targetStructureV = createStructure(model);
        Neuron targetStructureG = createStructure(model);
        Neuron sourceA = addMember(model, sourceStructureA);
        Neuron sourceB = addMember(model, sourceStructureB);
        addMember(model, targetStructureV);
        addMember(model, targetStructureG);
        connectStructures(model, sourceStructureA, targetStructureV);
        connectStructures(model, sourceStructureB, targetStructureV);
        connectStructures(model, sourceStructureA, targetStructureG);
        connectStructures(model, sourceStructureB, targetStructureG);

        SimulationSession session = SimulationSession.manual(model, sourceA.id());
        assertTrue(session.queueManualStart(sourceB.id()));
        SimulationStep step = Objects.requireNonNull(session.nextStep(), "simulation step");

        Set<String> centersV = findSharedCenters(model, targetStructureV.id(), sourceA.id(), sourceB.id());
        Set<String> centersG = findSharedCenters(model, targetStructureG.id(), sourceA.id(), sourceB.id());
        assertEquals(1, centersV.size());
        assertEquals(1, centersG.size());
        assertNotEquals(centersV.iterator().next(), centersG.iterator().next());
        assertEquals(2, step.nextInputSums().get(centersV.iterator().next()));
        assertEquals(2, step.nextInputSums().get(centersG.iterator().next()));
    }

    /** Перевіряє, що вкладені структурні шляхи не дублюють те саме активоване джерело в спільному центрі. */
    @Test
    void nestedAndOuterStructureLinksDoNotDuplicateAnActivatedSource() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron outerStructure = createStructure(model);
        Neuron nestedStructure = createStructure(model);
        Neuron targetStructure = createStructure(model);
        Neuron source = addMember(model, outerStructure);
        Neuron targetMember = addMember(model, targetStructure);
        assertTrue(model.addStructureMember(outerStructure.id(), nestedStructure.id()));
        assertTrue(model.removeStructureMember(outerStructure.id(), source.id()));
        assertTrue(model.addStructureMember(nestedStructure.id(), source.id()));
        connectStructures(model, outerStructure, targetStructure);
        connectStructures(model, nestedStructure, targetStructure);

        SimulationStep step = Objects.requireNonNull(
                SimulationSession.manual(model, source.id()).nextStep(), "simulation step");

        Set<String> centers = findSharedCenters(model, targetStructure.id(), source.id());
        assertEquals(1, centers.size());
        assertEquals(1, step.nextInputSums().get(centers.iterator().next()));
        assertFalse(step.nextInputSums().containsKey(targetMember.id()));
    }

    /** Перевіряє, що симуляція без структурного зв'язку не змінює склад приймальної структури. */
    @Test
    void doesNotCreateCenterWithoutDirectedStructuralConnection() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructure = createStructure(model);
        Neuron targetStructure = createStructure(model);
        Neuron source = addMember(model, sourceStructure);
        addMember(model, targetStructure);
        int targetMemberCount = model.structure(targetStructure.id()).memberIds().size();

        SimulationStep step = Objects.requireNonNull(
                SimulationSession.manual(model, source.id()).nextStep(), "simulation step");

        assertEquals(targetMemberCount, model.structure(targetStructure.id()).memberIds().size());
        assertTrue(step.nextInputSums().isEmpty());
    }

    /** Перевіряє, що негайний ручний запуск створює центр до першого передавання сигналу. */
    @Test
    void immediateActivationPreparesCenterBeforeEmittingSignal() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructure = createStructure(model);
        Neuron targetStructure = createStructure(model);
        Neuron source = addMember(model, sourceStructure);
        addMember(model, targetStructure);
        connectStructures(model, sourceStructure, targetStructure);
        int originalMemberCount = model.structure(targetStructure.id()).memberIds().size();

        SimulationStep step = Objects.requireNonNull(
                SimulationSession.manual(model, source.id()).activateManualStartNow(source.id()),
                "simulation step");
        assertEquals(originalMemberCount + 1, model.structure(targetStructure.id()).memberIds().size());
        Set<String> centers = findSharedCenters(model, targetStructure.id(), source.id());
        assertEquals(1, centers.size());
        assertEquals(1, step.nextInputSums().get(centers.iterator().next()));
    }



    /** Перевіряє, що активація лише зовнішнього нейрона не створює центр через наявний структурний зв'язок. */
    @Test
    void externalActivationAloneDoesNotCreateStructureCenter() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructure = createStructure(model);
        Neuron targetStructure = createStructure(model);
        addMember(model, sourceStructure);
        addMember(model, targetStructure);
        Neuron externalSource = model.createNeuron(NeuronType.EXCITATORY, 500.0, 0.0);
        connectStructures(model, sourceStructure, targetStructure);
        int originalMemberCount = model.structure(targetStructure.id()).memberIds().size();

        SimulationStep step = Objects.requireNonNull(
                SimulationSession.manual(model, externalSource.id()).nextStep(), "simulation step");

        assertEquals(originalMemberCount, model.structure(targetStructure.id()).memberIds().size());
        assertTrue(step.nextInputSums().isEmpty());
    }

    /** Перевіряє, що зовнішній нейрон не приєднується до спільного центру лише через одночасну активацію. */
    @Test
    void externalNeuronDoesNotJoinStructureSharedCenter() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceStructure = createStructure(model);
        Neuron targetStructure = createStructure(model);
        Neuron internalSource = addMember(model, sourceStructure);
        Neuron externalSource = model.createNeuron(NeuronType.EXCITATORY, 500.0, 0.0);
        addMember(model, targetStructure);
        connectStructures(model, sourceStructure, targetStructure);

        SimulationSession session = SimulationSession.manual(model, internalSource.id());
        assertTrue(session.queueManualStart(externalSource.id()));
        SimulationStep step = Objects.requireNonNull(session.nextStep(), "simulation step");

        Set<String> centers = findSharedCenters(model, targetStructure.id(), internalSource.id());
        assertEquals(1, centers.size());
        String centerId = centers.iterator().next();
        assertEquals(1, step.nextInputSums().get(centerId));
        assertFalse(model.hasConnection(externalSource.id(), centerId));
    }

    /**
     * Створює структурний контейнер у тестовій моделі.
     *
     * @param model модель, до якої потрібно додати структуру.
     * @return створений структурний нейрон-контейнер.
     */
    private Neuron createStructure(NeuronMapModel model) {
        return model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
    }

    /**
     * Створює звичайного нейрона й додає його до вказаної структури.
     *
     * @param model модель, яка володіє нейронами.
     * @param structure структура-власник нового нейрона.
     * @return новий внутрішній нейрон.
     */
    private Neuron addMember(NeuronMapModel model, Neuron structure) {
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        assertTrue(model.addStructureMember(structure.id(), member.id()));
        return member;
    }

    /**
     * Створює напрямлений структурний зв'язок між двома контейнерами.
     *
     * @param model модель карти.
     * @param sourceStructure вихідна структура.
     * @param targetStructure цільова структура.
     */
    private void connectStructures(NeuronMapModel model, Neuron sourceStructure, Neuron targetStructure) {
        assertTrue(model.createConnection(sourceStructure.id(), targetStructure.id()));
    }

    /**
     * Знаходить звичайних учасників структури, з'єднаних із кожним переданим джерелом.
     *
     * @param model модель карти.
     * @param targetStructureId ідентифікатор структури-приймача.
     * @param sourceIds ідентифікатори активованих джерел.
     * @return множина внутрішніх нейронів, до яких ведуть усі задані зв'язки.
     */
    private Set<String> findSharedCenters(NeuronMapModel model, String targetStructureId, String... sourceIds) {
        Set<String> result = new LinkedHashSet<>(model.structure(targetStructureId).memberIds());
        result.removeIf(candidateId -> {
            Neuron candidate = model.neuron(candidateId);
            if (candidate == null || !candidate.type().participatesInSignalPropagation()) {
                return true;
            }
            for (String sourceId : sourceIds) {
                if (!model.hasConnection(sourceId, candidateId)) {
                    return true;
                }
            }
            return false;
        });
        return result;
    }
}
