package com.example.neuronmap.application.history;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевіряє повне відновлення знімка та ігнорування тимчасових змін активації під час порівняння станів.
 */
class FieldStateSnapshotTest {

    /**
     * Перевіряє відновлення параметрів нейронів, їхнього розташування, зв’язків і груп зі знімка.
     */
    @Test
    void snapshotRestoresPersistentNeuronDataPresentationAndStructure() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron first = model.createNeuron(
                NeuronType.EXCITATORY,
                100,
                120,
                25
        );
        Neuron second = model.createNeuron(
                NeuronType.INHIBITORY,
                400,
                280,
                -15
        );

        first.setSignalStrength(7);
        first.setActivationThreshold(4);
        second.setSignalStrength(3);
        second.setActivationThreshold(8);

        NeuronPresentation firstPresentation =
                model.presentation(first.id());
        firstPresentation.setDirectionReversed(true);

        assertTrue(model.createConnection(first.id(), second.id()));
        model.createGroup(
                new LinkedHashSet<>(
                        List.of(first.id(), second.id())
                )
        );

        FieldStateSnapshot snapshot =
                FieldStateSnapshot.capture(model);

        first.setActivation(99);
        first.setType(NeuronType.INHIBITORY);
        first.setSignalStrength(2);
        first.setActivationThreshold(2);
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
        assertEquals(0, restoredFirst.activation());

        NeuronPresentation restoredPresentation =
                model.presentation(first.id());
        assertEquals(100, restoredPresentation.x());
        assertEquals(120, restoredPresentation.y());
        assertEquals(25, restoredPresentation.rotationDegrees());
        assertTrue(restoredPresentation.directionReversed());

        assertTrue(model.hasConnection(first.id(), second.id()));
        assertNotNull(model.groupContaining(first.id()));
        assertEquals(2, model.groupContaining(first.id()).memberIds().size());
    }

    /**
     * Перевіряє, що зміна лише поточної активації не змінює знімок збереженого стану.
     */
    @Test
    void activationOnlyChangesDoNotChangeSnapshot() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron neuron = model.createNeuron(
                NeuronType.EXCITATORY,
                0,
                0
        );

        FieldStateSnapshot before =
                FieldStateSnapshot.capture(model);

        neuron.setActivation(42);

        FieldStateSnapshot after =
                FieldStateSnapshot.capture(model);

        assertEquals(before, after);
    }
}
