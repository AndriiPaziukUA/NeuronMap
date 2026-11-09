package com.example.neuronmap.simulation;

import com.example.neuronmap.model.*;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє конкуренцію найсильніших учасників і адаптивні типи сигналу під час реального поширення. */
final class SimulationSessionStrongestNeuronTest {

    /** Перевіряє, що із двох одночасно активованих членів структури наступний такт отримує лише сильніший. */
    @Test
    void activatesOnlyMemberWithLargestSignalSum() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron weakerMember = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron strongerMember = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron weakerSource = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron strongerSource = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        model.addStructureMember(structure.id(), weakerMember.id());
        model.addStructureMember(structure.id(), strongerMember.id());
        model.structure(structure.id()).settings().setStrongestNeuronOnly(true);
        weakerSource.setSignalStrength(2);
        strongerSource.setSignalStrength(7);
        model.createConnection(weakerSource.id(), weakerMember.id());
        model.createConnection(strongerSource.id(), strongerMember.id());

        SimulationSession session = SimulationSession.manual(model, weakerSource.id());
        assertTrue(session.queueManualStart(strongerSource.id()));
        SimulationStep emission = nextStepOrFail(session);

        assertEquals(2, emission.nextInputSums().get(weakerMember.id()));
        assertEquals(7, emission.nextInputSums().get(strongerMember.id()));
        assertEquals(Set.of(strongerMember.id()), emission.nextActivatedNeuronIds());
        SimulationStep activation = nextStepOrFail(session);
        assertEquals(Set.of(strongerMember.id()), activation.activatedNeuronIds());
    }

    /**
     * Перевіряє 100-відсотковий поріг за кількістю входів і вибір кандидата, який фактично пройшов поріг.
     */
    @Test
    void activatesEligibleOneInputMemberWhenThreeInputMemberReceivesOnlyOneSignal() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron memberA = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron memberB = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron activeSource = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron inactiveSourceA = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron inactiveSourceB = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        model.addStructureMember(structure.id(), memberA.id());
        model.addStructureMember(structure.id(), memberB.id());
        model.structure(structure.id()).settings().setStrongestNeuronOnly(true);
        structure.setParameters(new NeuronParameters(1, 100, 1, 1, NeuronActivationMode.SIGNAL_COUNT));
        model.createConnection(activeSource.id(), memberA.id());
        model.createConnection(inactiveSourceA.id(), memberA.id());
        model.createConnection(inactiveSourceB.id(), memberA.id());
        model.createConnection(activeSource.id(), memberB.id());

        SimulationSession session = SimulationSession.manual(model, activeSource.id());
        SimulationStep emission = nextStepOrFail(session);

        assertEquals(1, emission.nextInputSums().get(memberA.id()));
        assertEquals(1, emission.nextInputSums().get(memberB.id()));
        assertFalse(emission.nextActivatedNeuronIds().contains(memberA.id()));
        assertTrue(emission.nextActivatedNeuronIds().contains(memberB.id()));
        SimulationStep activation = nextStepOrFail(session);
        assertEquals(Set.of(memberB.id()), activation.activatedNeuronIds());
    }

    /** Перевіряє, що серед учасників із пройденим 100-відсотковим порогом перемагає сильніший сигнал. */
    @Test
    void selectsStrongestAmongMembersThatBothReachedHundredPercentCountThreshold() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron memberA = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron memberB = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron sourceA = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron sourceB = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron sourceC = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        model.addStructureMember(structure.id(), memberA.id());
        model.addStructureMember(structure.id(), memberB.id());
        model.structure(structure.id()).settings().setStrongestNeuronOnly(true);
        structure.setParameters(new NeuronParameters(1, 100, 1, 1, NeuronActivationMode.SIGNAL_COUNT));
        model.createConnection(sourceA.id(), memberA.id());
        model.createConnection(sourceB.id(), memberA.id());
        model.createConnection(sourceC.id(), memberA.id());
        model.createConnection(sourceA.id(), memberB.id());

        SimulationSession session = SimulationSession.manual(model, sourceA.id());
        assertTrue(session.queueManualStart(sourceB.id()));
        assertTrue(session.queueManualStart(sourceC.id()));
        SimulationStep emission = nextStepOrFail(session);

        assertEquals(3, emission.nextInputSums().get(memberA.id()));
        assertEquals(1, emission.nextInputSums().get(memberB.id()));
        assertTrue(emission.nextActivatedNeuronIds().contains(memberA.id()));
        assertFalse(emission.nextActivatedNeuronIds().contains(memberB.id()));
        SimulationStep activation = nextStepOrFail(session);
        assertEquals(Set.of(memberA.id()), activation.activatedNeuronIds());
    }

    /** Перевіряє конкуренцію лише між учасниками, які пройшли поріг сили сигналу. */
    @Test
    void selectsStrongestEligibleMemberInSignalStrengthMode() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron memberA = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron memberB = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron sourceA = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron sourceB = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron sourceC = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        sourceA.setSignalStrength(2);
        sourceB.setSignalStrength(4);
        sourceC.setSignalStrength(5);
        model.addStructureMember(structure.id(), memberA.id());
        model.addStructureMember(structure.id(), memberB.id());
        model.structure(structure.id()).settings().setStrongestNeuronOnly(true);
        structure.setParameters(new NeuronParameters(1, 5, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH));
        model.createConnection(sourceA.id(), memberA.id());
        model.createConnection(sourceB.id(), memberA.id());
        model.createConnection(sourceC.id(), memberB.id());

        SimulationSession session = SimulationSession.manual(model, sourceA.id());
        assertTrue(session.queueManualStart(sourceB.id()));
        assertTrue(session.queueManualStart(sourceC.id()));
        SimulationStep emission = nextStepOrFail(session);

        assertEquals(6, emission.nextInputSums().get(memberA.id()));
        assertEquals(5, emission.nextInputSums().get(memberB.id()));
        assertEquals(Set.of(memberA.id()), emission.nextActivatedNeuronIds());
        SimulationStep activation = nextStepOrFail(session);
        assertEquals(Set.of(memberA.id()), activation.activatedNeuronIds());
    }

    /** Перевіряє, що конкуренція не примушує до активації кандидатів, які не пройшли власний поріг. */
    @Test
    void doesNotForceActivationWhenAllCandidatesAreBelowThreshold() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron first = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron second = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        model.addStructureMember(structure.id(), first.id());
        model.addStructureMember(structure.id(), second.id());
        model.structure(structure.id()).settings().setStrongestNeuronOnly(true);
        structure.setActivationThreshold(2);
        model.createConnection(source.id(), first.id());
        model.createConnection(source.id(), second.id());

        SimulationStep emission = nextStepOrFail(SimulationSession.manual(model, source.id()));
        assertTrue(emission.nextActivatedNeuronIds().isEmpty());
    }

    /**
     * Перевіряє персональне навчання кожного учасника структури та використання нового значення в його наступному сигналі.
     */
    @Test
    void strongestModeLearnsEachStructureMemberBeforeItsNextEmission() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron firstMember = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron secondMember = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron firstSource = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron secondSource = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron firstSink = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron secondSink = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        assertTrue(model.addStructureMember(structure.id(), firstMember.id()));
        assertTrue(model.addStructureMember(structure.id(), secondMember.id()));
        structure.setParameters(new NeuronParameters(
                1, 2, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH, SignalType.STRONGEST));
        firstSource.setSignalStrength(5);
        secondSource.setSignalStrength(7);
        assertTrue(model.createConnection(firstSource.id(), firstMember.id()));
        assertTrue(model.createConnection(secondSource.id(), secondMember.id()));
        assertTrue(model.createConnection(firstMember.id(), firstSink.id()));
        assertTrue(model.createConnection(secondMember.id(), secondSink.id()));

        SimulationSession session = SimulationSession.manual(model, firstSource.id());
        assertTrue(session.queueManualStart(secondSource.id()));
        SimulationStep sourceEmission = nextStepOrFail(session);

        assertEquals(Set.of(firstMember.id(), secondMember.id()), sourceEmission.nextActivatedNeuronIds());
        assertEquals(1, firstMember.signalStrength(), "Передбачення наступного такту не повинно змінювати модель");
        assertEquals(1, secondMember.signalStrength(), "Передбачення наступного такту не повинно змінювати модель");

        SimulationStep memberEmission = nextStepOrFail(session);

        assertEquals(5, firstMember.signalStrength());
        assertEquals(7, secondMember.signalStrength());
        assertEquals(5, memberEmission.nextInputSums().get(firstSink.id()));
        assertEquals(7, memberEmission.nextInputSums().get(secondSink.id()));
    }

    /** Перевіряє, що режим «Найсильніший» не зменшує силу після слабшого сигналу, який пройшов поріг. */
    @Test
    void strongestModeDoesNotReplaceStrengthWithSmallerThresholdPassingSignal() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron sink = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        target.setParameters(new NeuronParameters(
                8, 2, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH, SignalType.STRONGEST));
        source.setSignalStrength(4);
        assertTrue(model.createConnection(source.id(), target.id()));
        assertTrue(model.createConnection(target.id(), sink.id()));

        SimulationSession session = SimulationSession.manual(model, source.id());
        nextStepOrFail(session);
        SimulationStep activation = nextStepOrFail(session);

        assertEquals(8, target.signalStrength());
        assertEquals(8, activation.nextInputSums().get(sink.id()));
    }

    /** Перевіряє, що «Отриманий» замінює силу навіть меншим сигналом, який пройшов поріг. */
    @Test
    void receivedModeReplacesStrengthWithSmallerThresholdPassingSignal() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron sink = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        target.setParameters(new NeuronParameters(
                8, 2, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH, SignalType.RECEIVED));
        source.setSignalStrength(2);
        assertTrue(model.createConnection(source.id(), target.id()));
        assertTrue(model.createConnection(target.id(), sink.id()));

        SimulationSession session = SimulationSession.manual(model, source.id());
        nextStepOrFail(session);
        SimulationStep activation = nextStepOrFail(session);

        assertEquals(2, target.signalStrength());
        assertEquals(2, activation.nextInputSums().get(sink.id()));
    }

    /** Перевіряє, що сигнал нижче порога не змінює персональну силу в режимі «Отриманий». */
    @Test
    void receivedModeDoesNotAdaptWhenInputDoesNotReachActivationThreshold() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        target.setParameters(new NeuronParameters(
                8, 3, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH, SignalType.RECEIVED));
        source.setSignalStrength(2);
        assertTrue(model.createConnection(source.id(), target.id()));

        SimulationSession session = SimulationSession.manual(model, source.id());
        SimulationStep emission = nextStepOrFail(session);

        assertTrue(emission.nextActivatedNeuronIds().isEmpty());
        assertEquals(8, target.signalStrength());
        assertTrue(session.isFinished());
        assertNull(session.nextStep());
        assertEquals(8, target.signalStrength());
    }

    /** Перевіряє, що адаптивний гальмівний нейрон зберігає від'ємну вагу вихідного сигналу. */
    @Test
    void receivedModeKeepsInhibitoryOutputNegative() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron target = model.createNeuron(NeuronType.INHIBITORY, 0, 0);
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron sink = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        target.setParameters(new NeuronParameters(
                9, 4, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH, SignalType.RECEIVED));
        source.setSignalStrength(4);
        assertTrue(model.createConnection(source.id(), target.id()));
        assertTrue(model.createConnection(target.id(), sink.id()));

        SimulationSession session = SimulationSession.manual(model, source.id());
        nextStepOrFail(session);
        SimulationStep activation = nextStepOrFail(session);

        assertEquals(4, target.signalStrength());
        assertEquals(-4, target.signalWeight());
        assertEquals(-4, activation.nextInputSums().get(sink.id()));
    }

    /** Повертає наступний такт або завершує тест, якщо симуляція вже не має роботи. */
    private static SimulationStep nextStepOrFail(SimulationSession session) {
        return Objects.requireNonNull(session.nextStep(), "Expected the simulation to produce another step");
    }
}
