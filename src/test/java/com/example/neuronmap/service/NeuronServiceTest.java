package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronStructure;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє операції служби нейронів, початкове наповнення структур і зміну типів. */
class NeuronServiceTest {

    /** Перевіряє, що старий метод оновлення налаштувань зберігає кількість активних тактів. */
    @Test
    void legacyUpdateSettingsOverloadPreservesActiveTicks() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron neuron = service.create(NeuronType.EXCITATORY, 10.0, 20.0);
        neuron.setParameters(new NeuronParameters(2, 3, 5));

        assertTrue(service.updateSettings(neuron.id(), 7, 9));
        assertEquals(7, neuron.signalStrength());
        assertEquals(9, neuron.activationThreshold());
        assertEquals(5, neuron.parameters().activeTicks());
        assertFalse(service.updateSettings("missing-neuron", 7, 9));
    }

    /** Перевіряє, що нова структура автоматично отримує рівно одного збуджувального учасника. */
    @Test
    void createsStructureWithOneExcitatoryMember() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);

        Neuron structureNeuron = service.create(NeuronType.STRUCTURE, 12.0, 18.0);
        NeuronStructure structure = model.structure(structureNeuron.id());

        assertEquals(1, structure.memberIds().size());
        Neuron initialMember = model.neuron(structure.memberIds().iterator().next());
        assertNotNull(initialMember);
        assertEquals(NeuronType.EXCITATORY, initialMember.type());
        assertTrue(initialMember.type().isActivating());
        assertEquals(0.0, service.presentation(initialMember.id()).orElseThrow().x());
        assertEquals(0.0, service.presentation(initialMember.id()).orElseThrow().y());
        assertEquals(2, service.neurons().size());
    }

    /** Перевіряє каскадне видалення всіх вкладених нейронів, включно з учасниками вкладеної структури. */
    @Test
    void removingStructureDeletesAllNestedMembers() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron outer = service.create(NeuronType.STRUCTURE, 100.0, 200.0);
        String outerMemberId = model.structure(outer.id()).memberIds().iterator().next();
        Neuron inner = model.createNeuron(NeuronType.STRUCTURE, 400.0, 500.0);
        assertTrue(model.addStructureMember(outer.id(), inner.id()));
        Neuron innerMember = model.createNeuron(NeuronType.EXCITATORY, 600.0, 700.0);
        assertTrue(model.addStructureMember(inner.id(), innerMember.id()));
        Neuron standalone = service.create(NeuronType.INHIBITORY, 900.0, 900.0);

        assertTrue(service.remove(outer.id()).isPresent());

        assertEquals(1, service.neurons().size());
        assertNotNull(model.neuron(standalone.id()));
        assertNull(model.neuron(outer.id()));
        assertNull(model.neuron(outerMemberId));
        assertNull(model.neuron(inner.id()));
        assertNull(model.neuron(innerMember.id()));
        assertTrue(model.structures().isEmpty());
    }

    /** Перевіряє, що звичайний нейрон не створює структуру або додаткового учасника. */
    @Test
    void createsOrdinaryNeuronWithoutStructureMembers() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);

        service.create(NeuronType.EXCITATORY, 12.0, 18.0);

        assertEquals(1, service.neurons().size());
        assertTrue(model.structures().isEmpty());
    }

    /** Перевіряє, що структурний тип при перемиканні переходить у гальмівний, а потім у звичайний активуючий. */
    @Test
    void togglesStructureThroughActivatingAndInhibitoryCategories() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron structure = service.create(NeuronType.STRUCTURE, 10.0, 20.0);

        assertTrue(service.toggleType(structure.id()));
        assertEquals(NeuronType.INHIBITORY, structure.type());
        assertTrue(service.toggleType(structure.id()));
        assertEquals(NeuronType.EXCITATORY, structure.type());
        assertFalse(service.toggleType("missing-neuron"));
    }

    /** Перевіряє, чи служба повідомляє про переміщення наявного нейрона та повертає false для невідомого. */
    @Test
    void moveReportsWhetherTheNeuronPresentationWasFound() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron neuron = service.create(NeuronType.EXCITATORY, 10.0, 20.0);

        assertTrue(service.move(neuron.id(), 3.5, -4.0));
        assertEquals(13.5, service.presentation(neuron.id()).orElseThrow().x());
        assertEquals(16.0, service.presentation(neuron.id()).orElseThrow().y());
        assertFalse(service.move("missing-neuron", 1.0, 1.0));
    }
}
