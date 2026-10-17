package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє копіювання й вставлення параметрів, позицій і груп нейронів. */
class NeuronClipboardServiceTest {

    /** Перевіряє, що вставлення відновлює скопійований стан незалежно від подальших змін оригіналу. */
    @Test
    void copiesAndPastesNeuronStateAtRequestedAnchor() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neurons = new NeuronService(model);
        GroupService groups = new GroupService(model);
        NeuronClipboardService clipboard = new NeuronClipboardService(neurons, groups);
        Neuron source = neurons.create(NeuronType.INHIBITORY, 100.0, 200.0);
        source.setSignalStrength(75);
        source.setActivationThreshold(42);
        neurons.presentation(source.id()).orElseThrow().setRotationDegrees(30.0);
        neurons.presentation(source.id()).orElseThrow().setDirectionReversed(true);

        assertTrue(clipboard.copy(Set.of(source.id())));

        source.setSignalStrength(2);
        source.setActivationThreshold(3);
        neurons.presentation(source.id()).orElseThrow().setRotationDegrees(120.0);
        neurons.presentation(source.id()).orElseThrow().setDirectionReversed(false);
        List<Neuron> pasted = clipboard.paste(500.0, 600.0);

        assertEquals(1, pasted.size());
        Neuron copy = pasted.getFirst();
        assertEquals(NeuronType.INHIBITORY, copy.type());
        assertEquals(75, copy.signalStrength());
        assertEquals(42, copy.activationThreshold());
        assertEquals(500.0, neurons.presentation(copy.id()).orElseThrow().x());
        assertEquals(600.0, neurons.presentation(copy.id()).orElseThrow().y());
        assertEquals(30.0, neurons.presentation(copy.id()).orElseThrow().rotationDegrees());
        assertTrue(neurons.presentation(copy.id()).orElseThrow().directionReversed());
    }

    /** Перевіряє, що null відхиляється, а порожній вибір означає, що копіювати нічого. */
    @Test
    void rejectsNullSelectionAndReturnsFalseForEmptySelection() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neurons = new NeuronService(model);
        GroupService groups = new GroupService(model);
        NeuronClipboardService clipboard = new NeuronClipboardService(neurons, groups);

        assertThrows(NullPointerException.class, () -> clipboard.copy(null));
        assertFalse(clipboard.copy(Set.of()));
        assertTrue(clipboard.content().isEmpty());
    }

    /** Перевіряє, що вибір одного учасника групи копіює та вставляє всю групу. */
    @Test
    void copiesWholeGroupWhenOneMemberIsSelected() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neurons = new NeuronService(model);
        GroupService groups = new GroupService(model);
        NeuronClipboardService clipboard = new NeuronClipboardService(neurons, groups);
        Neuron first = neurons.create(NeuronType.EXCITATORY, 100.0, 200.0);
        Neuron second = neurons.create(NeuronType.EXCITATORY, 300.0, 200.0);
        groups.create(Set.of(first.id(), second.id()));

        assertTrue(clipboard.copy(Set.of(first.id())));
        NeuronClipboardService.ClipboardContent content = clipboard.content().orElseThrow();
        assertTrue(content.grouped());
        assertEquals(2, content.items().size());
        assertEquals(2, clipboard.paste(500.0, 600.0).size());
        assertEquals(2, model.groups().size());
    }
}
