package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronStructure;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє додавання внутрішніх нейронів до заданої кількості без зміни наявних учасників. */
class NeuronServiceStructureMemberCountTest {

    /** Перевіряє, що збільшення лічильника створює лише відсутніх учасників і залишає попередніх незмінними. */
    @Test
    void expandsStructureWithoutReplacingExistingMembers() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron structureNeuron = service.create(NeuronType.STRUCTURE, 40.0, 50.0);
        NeuronStructure structure = model.structure(structureNeuron.id());
        Set<String> initialMemberIds = Set.copyOf(structure.memberIds());
        Neuron initialMember = model.neuron(initialMemberIds.iterator().next());
        String initialName = initialMember.name();
        var initialParameters = initialMember.parameters();

        assertEquals(1, service.structureMemberCount(structureNeuron.id()));
        assertTrue(service.ensureStructureMemberCount(structureNeuron.id(), 3));

        assertEquals(3, structure.memberIds().size());
        assertTrue(structure.memberIds().containsAll(initialMemberIds));
        assertEquals(initialName, initialMember.name());
        assertEquals(initialParameters, initialMember.parameters());
        for (String memberId : structure.memberIds()) {
            Neuron member = model.neuron(memberId);
            assertNotNull(member);
            assertEquals(NeuronType.EXCITATORY, member.type());
            assertEquals(0.0, model.presentation(memberId).x(), 0.0);
            assertEquals(0.0, model.presentation(memberId).y(), 0.0);
        }

        assertTrue(service.ensureStructureMemberCount(structureNeuron.id(), 3));
        assertEquals(3, structure.memberIds().size(), "Збереження того самого значення не має створювати дублікатів");
    }

    /** Перевіряє, що тип і напрямок усіх учасників беруться зі спільних налаштувань структури. */
    @Test
    void generatedMembersFollowSharedStructureTypeAndDirection() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron structureNeuron = service.create(NeuronType.STRUCTURE, 15.0, 25.0);
        NeuronStructure structure = model.structure(structureNeuron.id());
        String originalMemberId = structure.memberIds().iterator().next();
        Neuron independentNeuron = model.createNeuron(NeuronType.INHIBITORY, 90.0, 90.0);

        assertTrue(service.toggleType(structureNeuron.id()));
        assertTrue(service.setDirectionReversed(structureNeuron.id(), true));
        assertTrue(service.ensureStructureMemberCount(structureNeuron.id(), 4));

        assertEquals(NeuronType.INHIBITORY, model.neuron(originalMemberId).type());
        assertTrue(model.presentation(originalMemberId).directionReversed());
        for (String memberId : structure.memberIds()) {
            assertEquals(NeuronType.INHIBITORY, model.neuron(memberId).type(),
                    "Тип кожного учасника має походити зі спільних налаштувань структури");
            assertTrue(model.presentation(memberId).directionReversed(),
                    "Напрямок кожного учасника має походити зі спільних налаштувань структури");
        }
        assertEquals(NeuronType.INHIBITORY, independentNeuron.type(),
                "Зміна типу структури не повинна змінювати окремі нейрони поза нею");
        assertFalse(model.presentation(independentNeuron.id()).directionReversed(),
                "Зміна напрямку структури не повинна змінювати окремі нейрони поза нею");
    }

    /** Перевіряє, що від'єднаний нейрон зберігає ефективний тип і напрямок після зміни колишньої структури. */
    @Test
    void detachedMemberKeepsItsLastSharedTypeAndDirection() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        StructureService structureService = new StructureService(model);
        Neuron structureNeuron = service.create(NeuronType.STRUCTURE, 0.0, 0.0);
        NeuronStructure structure = model.structure(structureNeuron.id());
        String memberId = structure.memberIds().iterator().next();

        assertTrue(service.toggleType(structureNeuron.id()));
        assertTrue(service.setDirectionReversed(structureNeuron.id(), true));
        assertTrue(structureService.removeMember(structureNeuron.id(), memberId));
        assertTrue(service.toggleType(structureNeuron.id()));
        assertTrue(service.setDirectionReversed(structureNeuron.id(), false));

        assertEquals(NeuronType.INHIBITORY, model.neuron(memberId).type());
        assertTrue(model.presentation(memberId).directionReversed());
    }

    /** Перевіряє відхилення від'ємної та меншої за поточну цілі без видалення наявних нейронів. */
    @Test
    void rejectsNegativeAndSmallerCountsWithoutChangingStructure() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron structureNeuron = service.create(NeuronType.STRUCTURE, 0.0, 0.0);
        NeuronStructure structure = model.structure(structureNeuron.id());
        Set<String> originalIds = Set.copyOf(structure.memberIds());

        assertFalse(service.ensureStructureMemberCount(structureNeuron.id(), -1));
        assertFalse(service.ensureStructureMemberCount(structureNeuron.id(), 0));
        assertEquals(originalIds, structure.memberIds());
        assertEquals(1, service.structureMemberCount(structureNeuron.id()));
    }

    /** Перевіряє результати для відсутньої структури без створення нейронів у моделі. */
    @Test
    void handlesUnknownStructureWithoutModifyingModel() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);

        assertEquals(0, service.structureMemberCount("missing-structure"));
        assertFalse(service.ensureStructureMemberCount("missing-structure", 1));
        assertTrue(model.neurons().isEmpty());
    }
}
