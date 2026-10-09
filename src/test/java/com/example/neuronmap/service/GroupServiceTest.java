package com.example.neuronmap.service;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє операції з групами та переміщення їхніх елементів.
 */
class GroupServiceTest {

    /**
     * Перевіряє очікувану поведінку: рухається усі група.
     */
    @Test
    void movesAllMembersOfContainingGroup() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neurons = new NeuronService(model);
        GroupService groups = new GroupService(model);

        var first = neurons.create(NeuronType.EXCITATORY, 10, 20);
        var second = neurons.create(NeuronType.INHIBITORY, 30, 40);
        groups.create(new LinkedHashSet<>(Set.of(first.id(), second.id())));

        assertTrue(groups.moveContaining(first.id(), 5, -3));
        assertEquals(15, model.presentation(first.id()).x());
        assertEquals(17, model.presentation(first.id()).y());
        assertEquals(35, model.presentation(second.id()).x());
        assertEquals(37, model.presentation(second.id()).y());
    }
}
