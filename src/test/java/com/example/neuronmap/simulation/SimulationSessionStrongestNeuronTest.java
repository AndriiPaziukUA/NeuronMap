package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє інтеграцію конкуренції найсильнішого нейрона з реальним поширенням сигналів у сеансі симуляції. */
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
     * Перевіряє 100-відсотковий поріг за кількістю входів: один сигнал не активує учасника з трьома входами,
     * але активує учасника з одним входом, і саме він перемагає в конкуренції.
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

    /** Перевіряє, що коли обидва учасники пройшли поріг 100%, перемагає той, хто отримав три сигнали замість одного. */
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

    /** Перевіряє, що в режимі сили сигналу перемагає найсильніший саме серед учасників, які пройшли поріг. */
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

    /** Перевіряє, що конкуренція не обходить спільний поріг активації, коли всі сигнали недостатні. */
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

    /** Повертає наступний такт або завершує тест із поясненням, якщо симуляція вже не має роботи. */
    private static SimulationStep nextStepOrFail(SimulationSession session) {
        return Objects.requireNonNull(session.nextStep(), "Expected the simulation to produce another step");
    }
}
