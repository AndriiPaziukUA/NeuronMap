package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.model.SignalType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє копіювання спільних режимів структури та персональних сил її адаптивних учасників. */
final class NeuronClipboardStrongestNeuronTest {

    /** Перевіряє відновлення перемикача на вставленій структурі та спільних налаштуваннях її учасників. */
    @Test
    void copiesStrongestNeuronModeToPastedStructure() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neurons = new NeuronService(model);
        NeuronClipboardService clipboard = new NeuronClipboardService(neurons, new GroupService(model));
        Neuron source = neurons.create(NeuronType.STRUCTURE, 100.0, 200.0);
        assertTrue(neurons.setStructureStrongestNeuronOnly(source.id(), true));

        assertTrue(clipboard.copy(Set.of(source.id())));
        assertTrue(clipboard.content().orElseThrow().items().getFirst().strongestNeuronOnly());

        Neuron pasted = clipboard.paste(400.0, 500.0).getFirst();
        assertTrue(pasted.strongestNeuronOnly());
        String memberId = model.structure(pasted.id()).memberIds().iterator().next();
        assertTrue(model.neuron(memberId).strongestNeuronOnly());
    }

    /** Перевіряє, що копіювання адаптивної структури переносить персональну силу кожного прямого учасника. */
    @Test
    void copiesAndRestoresPersonalSignalStrengthsForAdaptiveStructureMembers() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neurons = new NeuronService(model);
        NeuronClipboardService clipboard = new NeuronClipboardService(neurons, new GroupService(model));
        Neuron source = neurons.create(NeuronType.STRUCTURE, 100.0, 200.0);
        List<String> originalMemberIds = List.copyOf(model.structure(source.id()).memberIds());
        Neuron firstMember = model.neuron(originalMemberIds.getFirst());
        Neuron secondMember = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        assertTrue(model.addStructureMember(source.id(), secondMember.id()));
        source.setParameters(new NeuronParameters(
                10, 1, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH, SignalType.RECEIVED));
        firstMember.setSignalStrength(3);
        secondMember.setSignalStrength(7);

        assertTrue(clipboard.copy(Set.of(source.id())));
        assertEquals(List.of(3, 7),
                clipboard.content().orElseThrow().items().getFirst().structureMemberSignalStrengths());

        Neuron pasted = clipboard.paste(400.0, 500.0).getFirst();
        List<String> pastedMemberIds = List.copyOf(model.structure(pasted.id()).memberIds());
        assertEquals(2, pastedMemberIds.size());
        assertEquals(SignalType.RECEIVED, pasted.signalType());
        assertEquals(3, model.neuron(pastedMemberIds.get(0)).signalStrength());
        assertEquals(7, model.neuron(pastedMemberIds.get(1)).signalStrength());
    }
}
