package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє вибір одного найсильнішого кандидата в кожній структурі з увімкненим режимом конкуренції. */
final class StrongestNeuronSelectorTest {

    /** Перевіряє, що найвища сума перемагає, а кандидати поза структурою не змінюються. */
    @Test
    void selectsHighestInputSumAndLeavesExternalNeuronsUnaffected() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron root = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron first = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron second = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron external = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        model.addStructureMember(root.id(), first.id());
        model.addStructureMember(root.id(), second.id());
        model.structure(root.id()).settings().setStrongestNeuronOnly(true);
        Map<String, Integer> sums = new LinkedHashMap<>();
        sums.put(first.id(), 3);
        sums.put(second.id(), 8);
        sums.put(external.id(), 2);
        Set<String> eligible = new LinkedHashSet<>(sums.keySet());

        Set<String> winners = new StrongestNeuronSelector(model).selectWinners(sums, eligible);

        assertEquals(Set.of(second.id(), external.id()), winners);
    }

    /** Перевіряє, що за однакової суми обирається перший учасник у збереженому порядку структури. */
    @Test
    void keepsFirstMemberWhenInputSumsAreEqual() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron root = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron first = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron second = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        model.addStructureMember(root.id(), first.id());
        model.addStructureMember(root.id(), second.id());
        model.structure(root.id()).settings().setStrongestNeuronOnly(true);

        Set<String> winners = new StrongestNeuronSelector(model).selectWinners(
                Map.of(first.id(), 5, second.id(), 5), Set.of(first.id(), second.id()));

        assertEquals(Set.of(first.id()), winners);
    }

    /** Перевіряє, що сильніший отримувач нижче порога не блокує слабшого кандидата, який уже пройшов поріг. */
    @Test
    void strongestIneligibleReceiverDoesNotBlockWeakerEligibleMember() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron root = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron weaker = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron stronger = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        model.addStructureMember(root.id(), weaker.id());
        model.addStructureMember(root.id(), stronger.id());
        model.structure(root.id()).settings().setStrongestNeuronOnly(true);

        Set<String> winners = new StrongestNeuronSelector(model).selectWinners(
                Map.of(weaker.id(), 4, stronger.id(), 6), Set.of(weaker.id()));

        assertEquals(Set.of(weaker.id()), winners);
    }

    /** Перевіряє, що без режиму конкуренції всі кандидати залишаються активними. */
    @Test
    void preservesAllCandidatesWhenModeIsDisabled() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron root = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron first = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron second = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        model.addStructureMember(root.id(), first.id());
        model.addStructureMember(root.id(), second.id());
        Set<String> candidates = Set.of(first.id(), second.id());

        assertEquals(candidates, new StrongestNeuronSelector(model).selectWinners(
                Map.of(first.id(), 2, second.id(), 7), candidates));
    }
}
