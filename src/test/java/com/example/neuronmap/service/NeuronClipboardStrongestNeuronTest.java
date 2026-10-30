package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що режим найсильнішого нейрона зберігається під час копіювання структури. */
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
}
