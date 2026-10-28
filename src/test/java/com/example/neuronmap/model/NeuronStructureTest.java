package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє модель структур, правила членства та узгоджене очищення посилань карти. */
class NeuronStructureTest {

    /** Перевіряє, що структурний нейрон має окремий контейнер, який може залишатися порожнім. */
    @Test
    void structuralNeuronRegistersAnEmptyStructure() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structureNeuron = model.createNeuron(NeuronType.STRUCTURE, 12.0, 18.0);

        NeuronStructure structure = model.structure(structureNeuron.id());

        assertNotNull(structure);
        assertEquals(structureNeuron.id(), structure.id());
        assertTrue(structure.memberIds().isEmpty());
        assertSame(structureNeuron, model.neuron(structure.id()));
    }

    /** Перевіряє збереження порядку та підтримку великої кількості учасників без фіксованого ліміту. */
    @Test
    void structureStoresManyMembersInInsertionOrder() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structureNeuron = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
        List<String> expectedOrder = new ArrayList<>();

        for (int index = 0; index < 1_000; index++) {
            Neuron member = model.createNeuron(NeuronType.EXCITATORY, index, index);
            assertTrue(model.addStructureMember(structureNeuron.id(), member.id()));
            expectedOrder.add(member.id());
        }

        assertEquals(expectedOrder.size(), model.structure(structureNeuron.id()).memberIds().size());
        assertEquals(expectedOrder, List.copyOf(model.structure(structureNeuron.id()).memberIds()));
    }

    /** Перевіряє, що нейрон має не більше одного власника-структури, а повторне членство не створюється. */
    @Test
    void memberCanBelongToOnlyOneStructure() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronStructure first = model.structure(model.createNeuron(NeuronType.STRUCTURE, 0, 0).id());
        NeuronStructure second = model.structure(model.createNeuron(NeuronType.STRUCTURE, 100, 0).id());
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 50, 50);

        assertTrue(model.addStructureMember(first.id(), member.id()));
        assertFalse(model.addStructureMember(first.id(), member.id()));
        assertFalse(model.addStructureMember(second.id(), member.id()));
        assertSame(first, model.structureContaining(member.id()));
    }

    /** Перевіряє заборону циклічного вкладення структур без обмеження звичайної вкладеності. */
    @Test
    void nestedStructuresCannotCreateCycles() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronStructure outer = model.structure(model.createNeuron(NeuronType.STRUCTURE, 0, 0).id());
        NeuronStructure inner = model.structure(model.createNeuron(NeuronType.STRUCTURE, 100, 0).id());
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 200, 0);

        assertTrue(model.addStructureMember(outer.id(), inner.id()));
        assertTrue(model.addStructureMember(inner.id(), member.id()));
        assertFalse(model.addStructureMember(inner.id(), outer.id()));
        assertEquals(1, inner.memberIds().size());
        assertSame(outer, model.structureContaining(inner.id()));
    }

    /** Перевіряє, що зміни візуальних груп не впливають на незалежне членство у структурах. */
    @Test
    void structureMembershipIsIndependentFromVisualGroups() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structureNeuron = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron first = model.createNeuron(NeuronType.EXCITATORY, 20, 20);
        Neuron second = model.createNeuron(NeuronType.INHIBITORY, 40, 40);

        assertTrue(model.addStructureMember(structureNeuron.id(), first.id()));
        model.createGroup(Set.of(first.id(), second.id()));

        model.ungroup(Set.of(first.id()));

        assertSame(model.structure(structureNeuron.id()), model.structureContaining(first.id()));
        assertNull(model.groupContaining(first.id()));
        assertNotNull(model.neuron(first.id()));
    }

    /** Перевіряє видалення контейнера та учасника без видалення інших нейронів із карти. */
    @Test
    void deletingStructureOrMemberCleansOnlyRelatedMemberships() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron containerNeuron = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        NeuronStructure container = model.structure(containerNeuron.id());
        assertTrue(model.addStructureMember(container.id(), member.id()));

        model.removeNeuron(member.id());
        assertTrue(container.memberIds().isEmpty());
        assertNull(model.neuron(member.id()));
        assertNotNull(model.structure(container.id()));

        Neuron child = model.createNeuron(NeuronType.EXCITATORY, 200, 0);
        assertTrue(model.addStructureMember(container.id(), child.id()));
        model.removeNeuron(container.id());

        assertNull(model.structure(container.id()));
        assertNotNull(model.neuron(child.id()));
        assertNull(model.structureContaining(child.id()));
    }
}
