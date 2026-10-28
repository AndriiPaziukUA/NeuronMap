package com.example.neuronmap.service.history;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** Перевіряє відновлення стану карти, включно з відкритістю шухлядок структур та тимчасовою активацією. */
class FieldStateSnapshotTest {

    /** Перевіряє відновлення назв, параметрів, розташування, зв'язків і груп зі знімка. */
    @Test
    void snapshotRestoresPersistentNeuronDataPresentationAndStructure() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron first = model.createNeuron(NeuronType.EXCITATORY, 100, 120, 25);
        Neuron second = model.createNeuron(NeuronType.INHIBITORY, 400, 280, -15);

        first.setSignalStrength(7);
        first.setActivationThreshold(4);
        first.setName("Original label");
        second.setSignalStrength(3);
        second.setActivationThreshold(8);

        NeuronPresentation firstPresentation = model.presentation(first.id());
        firstPresentation.setDirectionReversed(true);

        assertTrue(model.createConnection(first.id(), second.id()));
        model.createGroup(new LinkedHashSet<>(List.of(first.id(), second.id())));

        FieldStateSnapshot snapshot = FieldStateSnapshot.capture(model);
        assertEquals(2, snapshot.presentations().size());

        first.setActivation(99);
        first.setType(NeuronType.INHIBITORY);
        first.setSignalStrength(2);
        first.setActivationThreshold(2);
        first.setName("Modified label");
        firstPresentation.setPosition(999, 888);
        firstPresentation.setRotationDegrees(190);
        firstPresentation.setDirectionReversed(false);
        assertNotNull(model.removeNeuron(second.id()));

        snapshot.restoreInto(model);

        Neuron restoredFirst = model.neuron(first.id());
        Neuron restoredSecond = model.neuron(second.id());

        assertNotNull(restoredFirst);
        assertNotNull(restoredSecond);
        assertEquals(NeuronType.EXCITATORY, restoredFirst.type());
        assertEquals(7, restoredFirst.signalStrength());
        assertEquals(4, restoredFirst.activationThreshold());
        assertEquals("Original label", restoredFirst.name());
        assertEquals(0, restoredFirst.activation());

        NeuronPresentation restoredPresentation = model.presentation(first.id());
        assertEquals(100, restoredPresentation.x());
        assertEquals(120, restoredPresentation.y());
        assertEquals(25, restoredPresentation.rotationDegrees());
        assertTrue(restoredPresentation.directionReversed());

        assertTrue(model.hasConnection(first.id(), second.id()));
        var restoredGroup = model.groupContaining(first.id());
        assertNotNull(restoredGroup);
        assertEquals(2, Objects.requireNonNull(restoredGroup).memberIds().size());
    }

    /** Перевіряє відновлення вкладених і порожніх структур, порядку учасників і стану їхніх шухлядок. */
    @Test
    void snapshotRestoresOrderedNestedAndEmptyStructuresAndDrawerState() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron root = model.createNeuron(NeuronType.STRUCTURE, 10, 20);
        Neuron nested = model.createNeuron(NeuronType.STRUCTURE, 100, 120);
        Neuron empty = model.createNeuron(NeuronType.STRUCTURE, 160, 180);
        Neuron firstMember = model.createNeuron(NeuronType.EXCITATORY, 30, 40);
        Neuron secondMember = model.createNeuron(NeuronType.INHIBITORY, 50, 60);

        assertTrue(model.addStructureMember(root.id(), firstMember.id()));
        assertTrue(model.addStructureMember(root.id(), nested.id()));
        assertTrue(model.addStructureMember(nested.id(), secondMember.id()));
        model.structure(root.id()).setExpanded(true);
        model.structure(nested.id()).setExpanded(false);
        model.structure(empty.id()).setExpanded(true);

        FieldStateSnapshot snapshot = FieldStateSnapshot.capture(model);
        assertEquals(3, snapshot.structures().size());

        // Зміна стану після знімка не повинна змінити його вміст.
        model.structure(root.id()).setExpanded(false);
        model.structure(nested.id()).setExpanded(true);
        model.structure(empty.id()).setExpanded(false);
        assertNotNull(model.removeNeuron(firstMember.id()));
        assertNotNull(model.removeNeuron(root.id()));
        snapshot.restoreInto(model);

        assertEquals(
                List.of(firstMember.id(), nested.id()),
                List.copyOf(Objects.requireNonNull(model.structure(root.id())).memberIds())
        );
        assertEquals(
                List.of(secondMember.id()),
                List.copyOf(Objects.requireNonNull(model.structure(nested.id())).memberIds())
        );
        assertTrue(Objects.requireNonNull(model.structure(empty.id())).memberIds().isEmpty());
        assertTrue(Objects.requireNonNull(model.structure(root.id())).isExpanded());
        assertFalse(Objects.requireNonNull(model.structure(nested.id())).isExpanded());
        assertTrue(Objects.requireNonNull(model.structure(empty.id())).isExpanded());
        assertEquals(snapshot, FieldStateSnapshot.capture(model));
    }

    /** Перевіряє, що старі конструктори знімка зберігають сумісність зі своїм попереднім контрактом. */
    @Test
    void legacySnapshotConstructorsRemainCompatible() {
        var parameters = new NeuronParameters(5, 3);
        var parameterSnapshot = new FieldStateSnapshot.NeuronState(
                "legacy-parameters", NeuronType.EXCITATORY, parameters);
        assertNull(parameterSnapshot.name());
        assertEquals(parameters, parameterSnapshot.parameters());

        var valueSnapshot = new FieldStateSnapshot.NeuronState(
                "legacy-values", NeuronType.INHIBITORY, 7, 4);
        assertNull(valueSnapshot.name());
        assertEquals(7, valueSnapshot.signalStrength());
        assertEquals(4, valueSnapshot.activationThreshold());

        var presentation = new FieldStateSnapshot.PresentationState(
                "legacy-presentation", 21.0, 34.0, 15.0, true);
        assertEquals(21.0, presentation.x());
        assertEquals(34.0, presentation.y());
        assertTrue(presentation.directionReversed());

        var structure = new FieldStateSnapshot.StructureState("legacy-structure", List.of());
        assertFalse(structure.expanded());
    }

    /** Перевіряє, що зміна лише поточної активації не змінює знімок збереженого стану. */
    @Test
    void activationOnlyChangesDoNotChangeSnapshot() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron neuron = model.createNeuron(NeuronType.EXCITATORY, 0, 0);

        FieldStateSnapshot before = FieldStateSnapshot.capture(model);
        neuron.setActivation(42);
        FieldStateSnapshot after = FieldStateSnapshot.capture(model);

        assertEquals(before, after);
    }
}
