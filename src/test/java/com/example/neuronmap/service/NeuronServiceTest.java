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

    /** Перевіряє зміну типів звичайних нейронів у вкладених структурах без зміни типів контейнерів. */
    @Test
    void togglesTypesOfStructureMembersWithoutChangingStructureTypes() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron outer = service.create(NeuronType.STRUCTURE, 10.0, 20.0);
        String outerMemberId = model.structure(outer.id()).memberIds().iterator().next();
        Neuron inner = service.create(NeuronType.STRUCTURE, 30.0, 40.0);
        assertTrue(model.addStructureMember(outer.id(), inner.id()));
        String innerMemberId = model.structure(inner.id()).memberIds().iterator().next();
        Neuron inhibitoryMember = model.createNeuron(NeuronType.INHIBITORY, 50.0, 60.0);
        assertTrue(model.addStructureMember(outer.id(), inhibitoryMember.id()));

        assertTrue(service.toggleType(outer.id()));

        assertEquals(NeuronType.STRUCTURE, outer.type());
        assertEquals(NeuronType.STRUCTURE, inner.type());
        assertEquals(NeuronType.INHIBITORY, model.neuron(outerMemberId).type());
        assertEquals(NeuronType.INHIBITORY, model.neuron(innerMemberId).type());
        assertEquals(NeuronType.EXCITATORY, inhibitoryMember.type());

        assertTrue(service.toggleType(outer.id()));
        assertEquals(NeuronType.STRUCTURE, outer.type());
        assertEquals(NeuronType.STRUCTURE, inner.type());
        assertEquals(NeuronType.EXCITATORY, model.neuron(outerMemberId).type());
        assertEquals(NeuronType.EXCITATORY, model.neuron(innerMemberId).type());
        assertEquals(NeuronType.INHIBITORY, inhibitoryMember.type());
        assertFalse(service.toggleType("missing-neuron"));
    }

    /** Перевіряє, що зміна типу порожньої структури не змінює сам контейнер і не повідомляє про зміну. */
    @Test
    void emptyStructureDoesNotChangeItsType() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron emptyStructure = model.createNeuron(NeuronType.STRUCTURE, 10.0, 20.0);

        assertFalse(service.toggleType(emptyStructure.id()));
        assertEquals(NeuronType.STRUCTURE, emptyStructure.type());
    }

    /** Перевіряє, що зміна типу звичайного нейрона й надалі перемикає його між активуючим і гальмівним. */
    @Test
    void togglesTypeOfOrdinaryNeuron() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron neuron = service.create(NeuronType.EXCITATORY, 10.0, 20.0);

        assertTrue(service.toggleType(neuron.id()));
        assertEquals(NeuronType.INHIBITORY, neuron.type());
        assertTrue(service.toggleType(neuron.id()));
        assertEquals(NeuronType.EXCITATORY, neuron.type());
    }

    /** Перевіряє розворот напрямку структури, усіх її учасників і вкладеної шухлядки без впливу на сторонні нейрони. */
    @Test
    void togglesDirectionOfStructureAndAllNestedMembers() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron outer = service.create(NeuronType.STRUCTURE, 10.0, 20.0);
        String outerMemberId = model.structure(outer.id()).memberIds().iterator().next();
        Neuron inner = service.create(NeuronType.STRUCTURE, 30.0, 40.0);
        assertTrue(model.addStructureMember(outer.id(), inner.id()));
        String innerMemberId = model.structure(inner.id()).memberIds().iterator().next();
        Neuron standalone = service.create(NeuronType.EXCITATORY, 70.0, 80.0);

        assertTrue(service.toggleDirection(outer.id()));

        assertTrue(service.presentation(outer.id()).orElseThrow().directionReversed());
        assertTrue(service.presentation(outerMemberId).orElseThrow().directionReversed());
        assertTrue(service.presentation(inner.id()).orElseThrow().directionReversed());
        assertTrue(service.presentation(innerMemberId).orElseThrow().directionReversed());
        assertFalse(service.presentation(standalone.id()).orElseThrow().directionReversed());

        assertTrue(service.toggleDirection(outer.id()));
        assertFalse(service.presentation(outer.id()).orElseThrow().directionReversed());
        assertFalse(service.presentation(outerMemberId).orElseThrow().directionReversed());
        assertFalse(service.presentation(inner.id()).orElseThrow().directionReversed());
        assertFalse(service.presentation(innerMemberId).orElseThrow().directionReversed());
        assertFalse(service.toggleDirection("missing-neuron"));
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
