package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronStructure;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Перевіряє публічний контракт служби структур і делегування правил членства моделі карти. */
class StructureServiceTest {

    /** Перевіряє додавання, пошук і вилучення учасника без видалення самого нейрона. */
    @Test
    void managesStructureMembershipThroughModel() {
        NeuronMapModel model = new NeuronMapModel();
        StructureService service = new StructureService(model);
        Neuron structureNeuron = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 50, 75);

        assertTrue(service.find(structureNeuron.id()).isPresent());
        assertTrue(service.addMember(structureNeuron.id(), member.id()));
        assertEquals(structureNeuron.id(), service.containing(member.id()).orElseThrow().id());
        assertEquals(1, service.structures().size());
        assertTrue(service.removeMember(structureNeuron.id(), member.id()));
        assertTrue(service.containing(member.id()).isEmpty());
        assertNotNull(model.neuron(member.id()));
    }

    /** Перевіряє безпечні результати для відсутніх структур, нейронів і некоректних ідентифікаторів. */
    @Test
    void rejectsUnknownEntitiesAndHandlesInvalidLookups() {
        NeuronMapModel model = new NeuronMapModel();
        StructureService service = new StructureService(model);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 0, 0);

        assertFalse(service.addMember("missing-structure", member.id()));
        assertFalse(service.addMember("missing-structure", "missing-neuron"));
        assertFalse(service.removeMember("missing-structure", member.id()));
        assertTrue(service.find(null).isEmpty());
        assertTrue(service.find(" ").isEmpty());
        assertTrue(service.containing("missing-neuron").isEmpty());
        assertEquals(0, service.structures().size());
    }

    /** Перевіряє, що структура може зберігати одного учасника й надалі залишатися зареєстрованою. */
    @Test
    void oneMemberDoesNotRemoveStructure() {
        NeuronMapModel model = new NeuronMapModel();
        StructureService service = new StructureService(model);
        Neuron structureNeuron = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 100, 0);

        assertTrue(service.addMember(structureNeuron.id(), member.id()));
        assertTrue(service.find(structureNeuron.id()).orElseThrow().memberIds().contains(member.id()));
        assertEquals(1, service.find(structureNeuron.id()).map(NeuronStructure::memberIds).orElseThrow().size());
    }
}
