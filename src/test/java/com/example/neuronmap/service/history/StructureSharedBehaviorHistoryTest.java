package com.example.neuronmap.service.history;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що спільний тип, напрямок і режим найсильнішого нейрона зберігаються у знімках undo/redo. */
final class StructureSharedBehaviorHistoryTest {

    /** Перевіряє успадкування типу, напрямку й конкуренції учасниками, створеними після відновлення знімка. */
    @Test
    void snapshotRestoresBehaviorOfAnEmptyStructure() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron structure = service.create(NeuronType.STRUCTURE, 20.0, 30.0);
        assertTrue(service.remove(model.structure(structure.id()).memberIds().iterator().next()).isPresent());

        assertTrue(service.toggleType(structure.id()));
        assertTrue(service.setDirectionReversed(structure.id(), true));
        assertTrue(service.setStructureStrongestNeuronOnly(structure.id(), true));
        FieldStateSnapshot snapshot = FieldStateSnapshot.capture(model);

        assertTrue(service.toggleType(structure.id()));
        assertTrue(service.setDirectionReversed(structure.id(), false));
        assertTrue(service.setStructureStrongestNeuronOnly(structure.id(), false));
        snapshot.restoreInto(model);

        NeuronService restoredService = new NeuronService(model);
        assertTrue(restoredService.ensureStructureMemberCount(structure.id(), 2));
        for (String memberId : model.structure(structure.id()).memberIds()) {
            assertEquals(NeuronType.INHIBITORY, model.neuron(memberId).type());
            assertTrue(model.presentation(memberId).directionReversed());
            assertTrue(model.neuron(memberId).strongestNeuronOnly());
        }
    }

    /** Перевіряє, що старий знімок із п'ятьма полями зберігає вимкнений режим конкуренції для сумісності. */
    @Test
    void legacyStructureSnapshotDefaultsStrongestNeuronModeToFalse() {
        FieldStateSnapshot.StructureState state = new FieldStateSnapshot.StructureState(
                "legacy", java.util.List.of(), false, NeuronType.EXCITATORY, false);

        assertFalse(state.strongestNeuronOnly());
    }
}
