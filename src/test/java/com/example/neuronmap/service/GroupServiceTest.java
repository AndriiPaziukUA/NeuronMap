package com.example.neuronmap.service;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє контракти груп нейронів, включно з пошуком і переміщенням учасників. */
class GroupServiceTest {

    /** Перевіряє переміщення всіх учасників групи та повернення групи через Optional. */
    @Test
    void movesAllMembersOfContainingGroup() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neurons = new NeuronService(model);
        GroupService groups = new GroupService(model);
        var first = neurons.create(NeuronType.EXCITATORY, 10, 20);
        var second = neurons.create(NeuronType.INHIBITORY, 30, 40);
        groups.create(new LinkedHashSet<>(Set.of(first.id(), second.id())));

        assertTrue(groups.containing(first.id()).isPresent());
        assertTrue(groups.moveContaining(first.id(), 5, -3));
        assertEquals(15, model.presentation(first.id()).x());
        assertEquals(17, model.presentation(first.id()).y());
        assertEquals(35, model.presentation(second.id()).x());
        assertEquals(37, model.presentation(second.id()).y());
        assertTrue(groups.containing("missing").isEmpty());
    }

    /** Перевіряє, що обов’язкові набори учасників відхиляють null замість непомітної відмови. */
    @Test
    void rejectsMissingMemberCollections() {
        GroupService groups = new GroupService(new NeuronMapModel());

        assertThrows(NullPointerException.class, () -> groups.create(null));
        assertThrows(NullPointerException.class, () -> groups.ungroup(null));
    }
}
