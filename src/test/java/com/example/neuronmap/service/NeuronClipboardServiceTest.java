package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє копіювання й вставлення нейронів, груп і структур із новими внутрішніми учасниками. */
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
        source.setName("Copied <> #1");
        neurons.presentation(source.id()).orElseThrow().setRotationDegrees(30.0);
        neurons.presentation(source.id()).orElseThrow().setDirectionReversed(true);

        assertTrue(clipboard.copy(Set.of(source.id())));

        source.setSignalStrength(2);
        source.setActivationThreshold(3);
        source.setName("Changed");
        neurons.presentation(source.id()).orElseThrow().setRotationDegrees(120.0);
        neurons.presentation(source.id()).orElseThrow().setDirectionReversed(false);
        List<Neuron> pasted = clipboard.paste(500.0, 600.0);

        assertEquals(1, pasted.size());
        Neuron copy = pasted.getFirst();
        assertEquals(NeuronType.INHIBITORY, copy.type());
        assertEquals(75, copy.signalStrength());
        assertEquals(42, copy.activationThreshold());
        assertEquals("Copied <> #1", copy.name());
        assertEquals(500.0, neurons.presentation(copy.id()).orElseThrow().x());
        assertEquals(600.0, neurons.presentation(copy.id()).orElseThrow().y());
        assertEquals(30.0, neurons.presentation(copy.id()).orElseThrow().rotationDegrees());
        assertTrue(neurons.presentation(copy.id()).orElseThrow().directionReversed());
    }

    /** Перевіряє, що копія структури зберігає кількість учасників, а кожен учасник має новий ідентифікатор. */
    @Test
    void copiesStructureWithSameNumberOfNewMembers() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neurons = new NeuronService(model);
        GroupService groups = new GroupService(model);
        StructureService structures = new StructureService(model);
        NeuronClipboardService clipboard = new NeuronClipboardService(neurons, groups);
        Neuron source = neurons.create(NeuronType.STRUCTURE, 100.0, 200.0);

        Neuron secondMember = neurons.create(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron thirdMember = neurons.create(NeuronType.EXCITATORY, 0.0, 0.0);
        assertTrue(structures.addMember(source.id(), secondMember.id()));
        assertTrue(structures.addMember(source.id(), thirdMember.id()));
        Set<String> originalMemberIds = Set.copyOf(model.structure(source.id()).memberIds());
        assertEquals(3, originalMemberIds.size());

        assertTrue(clipboard.copy(Set.of(source.id())));
        Neuron copiedStructure = clipboard.paste(500.0, 600.0).getFirst();
        Set<String> copiedMemberIds = model.structure(copiedStructure.id()).memberIds();

        assertEquals(originalMemberIds.size(), copiedMemberIds.size());
        assertTrue(originalMemberIds.stream().allMatch(id -> model.neuron(id) != null));
        assertTrue(copiedMemberIds.stream().allMatch(id -> model.neuron(id) != null));
        assertTrue(copiedMemberIds.stream().noneMatch(originalMemberIds::contains));
    }

    /** Перевіряє, що копіювання порожньої структури не залишає створеного за замовчуванням учасника. */
    @Test
    void copiesEmptyStructureWithoutAddingDefaultMember() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neurons = new NeuronService(model);
        NeuronClipboardService clipboard = new NeuronClipboardService(neurons, new GroupService(model));
        Neuron source = neurons.create(NeuronType.STRUCTURE, 100.0, 200.0);
        String initialMemberId = model.structure(source.id()).memberIds().iterator().next();
        assertTrue(neurons.remove(initialMemberId).isPresent());
        assertTrue(model.structure(source.id()).memberIds().isEmpty());

        assertTrue(clipboard.copy(Set.of(source.id())));
        Neuron copiedStructure = clipboard.paste(500.0, 600.0).getFirst();

        assertTrue(model.structure(copiedStructure.id()).memberIds().isEmpty());
    }

    /** Перевіряє, що копія структурного елемента зберігає спільний тип майбутніх учасників і їхній напрямок. */
    @Test
    void copiesSharedStructureMemberTypeAndDirection() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neurons = new NeuronService(model);
        NeuronClipboardService clipboard = new NeuronClipboardService(neurons, new GroupService(model));
        Neuron sourceStructure = neurons.create(NeuronType.STRUCTURE, 100.0, 200.0);

        assertTrue(neurons.toggleType(sourceStructure.id()));
        assertTrue(neurons.setDirectionReversed(sourceStructure.id(), true));
        assertTrue(clipboard.copy(Set.of(sourceStructure.id())));
        assertEquals(NeuronType.INHIBITORY,
                clipboard.content().orElseThrow().items().getFirst().structureMemberType());

        Neuron pastedStructure = clipboard.paste(500.0, 600.0).getFirst();
        String pastedMemberId = model.structure(pastedStructure.id()).memberIds().iterator().next();
        assertEquals(NeuronType.INHIBITORY, model.neuron(pastedMemberId).type());
        assertTrue(neurons.presentation(pastedMemberId).orElseThrow().directionReversed());
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

    /** Перевіряє, що кількість учасників у даних буфера дозволена лише для структури й не може бути від'ємною. */
    @Test
    void rejectsInvalidStructureMemberCounts() {
        NeuronParameters parameters = new NeuronParameters(1, 1, 1);

        assertThrows(IllegalArgumentException.class, () -> new NeuronClipboardService.NeuronData(
                NeuronType.STRUCTURE, parameters, 0.0, 0.0, 0.0, false, null, null, -1));
        assertThrows(IllegalArgumentException.class, () -> new NeuronClipboardService.NeuronData(
                NeuronType.EXCITATORY, parameters, 0.0, 0.0, 0.0, false, null, null, 1));
    }

    /** Перевіряє нормалізацію порожньої назви та граничну довжину даних буфера копіювання. */
    @Test
    void validatesNameInClipboardSnapshot() {
        NeuronClipboardService.NeuronData unnamed = new NeuronClipboardService.NeuronData(
                NeuronType.EXCITATORY,
                new NeuronParameters(1, 1, 1),
                0.0,
                0.0,
                0.0,
                false,
                ""
        );
        assertNull(unnamed.name());
        assertThrows(IllegalArgumentException.class, () ->
                new NeuronClipboardService.NeuronData(
                        NeuronType.EXCITATORY,
                        new NeuronParameters(1, 1, 1),
                        0.0,
                        0.0,
                        0.0,
                        false,
                        "x".repeat(31)
                ));
    }

    /** Перевіряє, що конструктори старого контракту буфера копіювання зберігають параметри. */
    @Test
    void legacyNeuronDataConstructorsRemainCompatible() {
        NeuronParameters parameters = new NeuronParameters(6, 4, 3);
        NeuronClipboardService.NeuronData fromParameters = new NeuronClipboardService.NeuronData(
                NeuronType.EXCITATORY, parameters, 11.0, -8.0, 25.0, true);

        assertEquals(parameters, fromParameters.parameters());
        assertEquals(11.0, fromParameters.offsetX());
        assertEquals(-8.0, fromParameters.offsetY());
        assertEquals(25.0, fromParameters.rotationDegrees());
        assertTrue(fromParameters.directionReversed());
        assertNull(fromParameters.name());
        assertNull(fromParameters.structureMemberType());
        assertNull(fromParameters.structureMemberCount());

        NeuronClipboardService.NeuronData fromSharedMemberType = new NeuronClipboardService.NeuronData(
                NeuronType.STRUCTURE, parameters, 11.0, -8.0, 25.0, true, "Structure", NeuronType.INHIBITORY);
        assertEquals(NeuronType.INHIBITORY, fromSharedMemberType.structureMemberType());
        assertNull(fromSharedMemberType.structureMemberCount());

        NeuronClipboardService.NeuronData fromSeparateValues = new NeuronClipboardService.NeuronData(
                NeuronType.INHIBITORY, 7, 5, 3.0, 4.0, -15.0, false);
        assertEquals(7, fromSeparateValues.signalStrength());
        assertEquals(5, fromSeparateValues.activationThreshold());
        assertEquals(3.0, fromSeparateValues.offsetX());
        assertEquals(4.0, fromSeparateValues.offsetY());
        assertEquals(-15.0, fromSeparateValues.rotationDegrees());
        assertFalse(fromSeparateValues.directionReversed());
        assertNull(fromSeparateValues.name());
        assertNull(fromSeparateValues.structureMemberType());
        assertNull(fromSeparateValues.structureMemberCount());
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
        List<Neuron> pasted = clipboard.paste(500.0, 600.0);
        assertEquals(2, pasted.size());
        assertTrue(pasted.stream().allMatch(neuron -> neuron.name() == null));
        assertEquals(2, model.groups().size());
    }
}
