package com.example.neuronmap.service.history;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.model.SignalType;
import com.example.neuronmap.model.StructureNeuronCreationMode;
import com.example.neuronmap.service.NeuronService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє відновлення спільних режимів структур і персонального стану сигналу через undo/redo. */
final class StructureSharedBehaviorHistoryTest {

    /** Перевіряє успадкування типу, напрямку, конкуренції та режиму створення нейронів після відновлення знімка. */
    @Test
    void snapshotRestoresBehaviorOfAnEmptyStructure() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron structure = service.create(NeuronType.STRUCTURE, 20.0, 30.0);
        assertTrue(service.remove(model.structure(structure.id()).memberIds().iterator().next()).isPresent());

        assertTrue(service.toggleType(structure.id()));
        assertTrue(service.setDirectionReversed(structure.id(), true));
        assertTrue(service.setStructureStrongestNeuronOnly(structure.id(), true));
        assertTrue(service.setStructureNeuronCreationMode(structure.id(), StructureNeuronCreationMode.STRUCTURE_COUNT));
        FieldStateSnapshot snapshot = FieldStateSnapshot.capture(model);

        assertTrue(service.toggleType(structure.id()));
        assertTrue(service.setDirectionReversed(structure.id(), false));
        assertTrue(service.setStructureStrongestNeuronOnly(structure.id(), false));
        assertTrue(service.setStructureNeuronCreationMode(structure.id(), StructureNeuronCreationMode.NEURON_COUNT));
        snapshot.restoreInto(model);

        NeuronService restoredService = new NeuronService(model);
        assertTrue(restoredService.ensureStructureMemberCount(structure.id(), 2));
        for (String memberId : model.structure(structure.id()).memberIds()) {
            assertEquals(NeuronType.INHIBITORY, model.neuron(memberId).type());
            assertTrue(model.presentation(memberId).directionReversed());
            assertTrue(model.neuron(memberId).strongestNeuronOnly());
            assertEquals(StructureNeuronCreationMode.STRUCTURE_COUNT, model.neuron(memberId).neuronCreationMode());
        }
    }

    /** Перевіряє, що знімки старого формату використовують попередні типові режими структури. */
    @Test
    void legacyStructureSnapshotDefaultsNewModesToPreviousBehavior() {
        FieldStateSnapshot.StructureState state = new FieldStateSnapshot.StructureState(
                "legacy", java.util.List.of(), false, NeuronType.EXCITATORY, false);

        assertFalse(state.strongestNeuronOnly());
        assertEquals(StructureNeuronCreationMode.NEURON_COUNT, state.neuronCreationMode());
    }

    /** Перевіряє, що undo та redo відновлюють тип сигналу й персональні сили всіх членів структури. */
    @Test
    void undoAndRedoRestoreAdaptiveSignalTypeAndPersonalMemberStrengths() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
        Neuron first = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron second = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        assertTrue(model.addStructureMember(structure.id(), first.id()));
        assertTrue(model.addStructureMember(structure.id(), second.id()));
        structure.setParameters(new NeuronParameters(
                5, 1, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH, SignalType.RECEIVED));
        first.setSignalStrength(2);
        second.setSignalStrength(4);

        FieldHistory history = new FieldHistory();
        history.initialize(model);
        first.setSignalStrength(3);
        second.setSignalStrength(1);
        assertTrue(history.commitSavedState(model));

        assertTrue(history.undo(model));
        Neuron restoredStructure = model.neuron(structure.id());
        assertEquals(SignalType.RECEIVED, restoredStructure.signalType());
        assertEquals(2, model.neuron(first.id()).signalStrength());
        assertEquals(4, model.neuron(second.id()).signalStrength());

        assertTrue(history.redo(model));
        assertEquals(SignalType.RECEIVED, model.neuron(structure.id()).signalType());
        assertEquals(3, model.neuron(first.id()).signalStrength());
        assertEquals(1, model.neuron(second.id()).signalStrength());
    }
    /** Перевіряє статичний тип сигналу для звичайного нейрона та нового дерева структури. */
    @Test
    void defaultsToStaticSignalType() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron neuron = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        assertTrue(model.addStructureMember(structure.id(), member.id()));

        assertEquals(SignalType.STATIC, neuron.signalType());
        assertEquals(SignalType.STATIC, structure.signalType());
        assertEquals(SignalType.STATIC, member.signalType());
    }

    /** Перевіряє, що зміна спільних параметрів не затирає персональні адаптивні сили учасників. */
    @Test
    void adaptiveStructureKeepsPersonalStrengthsWhenSharedParametersChange() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
        Neuron first = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron second = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        assertTrue(model.addStructureMember(structure.id(), first.id()));
        assertTrue(model.addStructureMember(structure.id(), second.id()));

        structure.setParameters(new NeuronParameters(
                6, 2, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH, SignalType.RECEIVED));
        assertEquals(6, first.signalStrength());
        assertEquals(6, second.signalStrength());
        first.setSignalStrength(9);
        second.setSignalStrength(4);
        structure.setActivationThreshold(3);
        structure.setActiveTicks(2);

        assertEquals(9, first.signalStrength());
        assertEquals(4, second.signalStrength());
        assertEquals(3, first.activationThreshold());
        assertEquals(2, second.activeTicks());

        structure.setSignalType(SignalType.STATIC);
        assertEquals(6, first.signalStrength());
        assertEquals(6, second.signalStrength());
        structure.setSignalType(SignalType.RECEIVED);
        assertEquals(6, first.signalStrength());
        assertEquals(6, second.signalStrength());
    }

    /** Перевіряє збереження персональної сили після вилучення нейрона з адаптивної структури. */
    @Test
    void detachingAdaptiveMemberPreservesItsLearnedStrength() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        assertTrue(model.addStructureMember(structure.id(), member.id()));
        structure.setParameters(new NeuronParameters(
                5, 1, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH, SignalType.STRONGEST));
        member.setSignalStrength(11);

        assertTrue(model.removeStructureMember(structure.id(), member.id()));

        assertEquals(SignalType.STRONGEST, member.signalType());
        assertEquals(11, member.signalStrength());
    }

}
