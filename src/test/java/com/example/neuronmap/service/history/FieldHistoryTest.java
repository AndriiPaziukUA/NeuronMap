package com.example.neuronmap.service.history;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевіряє скасування, повторення, відгалуження історії та повноту відновлення стану карти.
 */
class FieldHistoryTest {

    /**
     * Перевіряє відновлення стану карти після скасування та повторення зміни.
     */
    @Test
    void undoAndRedoRestoreFieldState() {
        NeuronMapModel model = new NeuronMapModel();
        FieldHistory history = new FieldHistory();
        history.initialize(model);

        model.createNeuron(NeuronType.EXCITATORY, 100, 200);
        assertTrue(history.commitSavedState(model));
        assertTrue(history.canUndo());
        assertFalse(history.canRedo());

        assertTrue(history.undo(model));
        assertTrue(model.isEmpty());
        assertFalse(history.canUndo());
        assertTrue(history.canRedo());

        assertTrue(history.redo(model));
        assertEquals(1, model.neurons().size());
        assertFalse(history.canRedo());
    }

    /**
     * Перевіряє повернення членства учасників у шухляди після видалення структурного нейрона та undo/redo.
     */
    @Test
    void undoAndRedoPreserveDrawerMembershipWhenStructureIsDeleted() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 10, 20);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 30, 40);
        assertTrue(model.addStructureMember(structure.id(), member.id()));

        FieldHistory history = new FieldHistory();
        history.initialize(model);

        assertNotNull(model.removeNeuron(structure.id()));
        assertTrue(history.commitSavedState(model));
        assertNull(model.structureContaining(member.id()));

        assertTrue(history.undo(model));
        assertNotNull(model.neuron(structure.id()));
        assertEquals(
                structure.id(),
                Objects.requireNonNull(
                        model.structureContaining(member.id()),
                        "Після undo учасник має повернутися до початкової структури"
                ).id()
        );

        assertTrue(history.redo(model));
        assertNull(model.neuron(structure.id()));
        assertNull(model.structureContaining(member.id()));
        assertNotNull(model.neuron(member.id()));
    }

    /**
     * Перевіряє повернення видаленого учасника до початкової шухляди, а не на дошку.
     */
    @Test
    void undoAndRedoPreserveDrawerMembershipWhenMemberIsDeleted() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 10, 20);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 30, 40);
        assertTrue(model.addStructureMember(structure.id(), member.id()));

        FieldHistory history = new FieldHistory();
        history.initialize(model);

        assertNotNull(model.removeNeuron(member.id()));
        assertTrue(history.commitSavedState(model));
        assertTrue(model.structure(structure.id()).memberIds().isEmpty());

        assertTrue(history.undo(model));
        assertNotNull(model.neuron(member.id()));
        assertEquals(
                structure.id(),
                Objects.requireNonNull(
                        model.structureContaining(member.id()),
                        "Після undo видалений нейрон має повернутися до початкової структури"
                ).id()
        );

        assertTrue(history.redo(model));
        assertNull(model.neuron(member.id()));
        assertTrue(model.structure(structure.id()).memberIds().isEmpty());
    }

    /**
     * Перевіряє, що зміна лише членства структури утворює крок історії та коректно скасовується.
     */
    @Test
    void membershipOnlyChangesCreateUndoableHistorySteps() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 10, 20);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        assertTrue(model.addStructureMember(structure.id(), member.id()));

        FieldHistory history = new FieldHistory();
        history.initialize(model);

        assertTrue(model.removeStructureMember(structure.id(), member.id()));
        assertTrue(history.commitSavedState(model));

        assertTrue(history.undo(model));
        assertEquals(
                structure.id(),
                Objects.requireNonNull(
                        model.structureContaining(member.id()),
                        "Після undo членство структури має бути відновлене"
                ).id()
        );

        assertTrue(history.redo(model));
        assertNull(model.structureContaining(member.id()));
    }

    /**
     * Перевіряє, що нова зміна після скасування видаляє застарілу гілку повторення.
     */
    @Test
    void newChangeAfterUndoDropsRedoBranch() {
        NeuronMapModel model = new NeuronMapModel();
        FieldHistory history = new FieldHistory();
        history.initialize(model);

        model.createNeuron(NeuronType.EXCITATORY, 10, 10);
        assertTrue(history.commitSavedState(model));

        model.createNeuron(NeuronType.INHIBITORY, 20, 20);
        assertTrue(history.commitSavedState(model));

        assertTrue(history.undo(model));
        assertTrue(history.canRedo());

        model.createNeuron(NeuronType.EXCITATORY, 30, 30);
        assertTrue(history.commitSavedState(model));

        assertFalse(history.canRedo());
    }

    /**
     * Перевіряє, що незмінені дані й тимчасові значення симуляції не створюють крок історії.
     */
    @Test
    void unchangedAndRuntimeOnlySavesDoNotCreateHistory() {
        NeuronMapModel model = new NeuronMapModel();
        FieldHistory history = new FieldHistory();
        history.initialize(model);

        model.createNeuron(NeuronType.EXCITATORY, 10, 10);
        assertTrue(history.commitSavedState(model));

        var neuron = model.neurons().iterator().next();
        neuron.setActivation(999);
        assertFalse(history.commitSavedState(model));

        assertEquals(1, history.undoSize());
    }

    /**
     * Перевіряє, що історія обмежена тридцятьма кроками скасування.
     */
    @Test
    void historyIsLimitedToThirtyUndoSteps() {
        NeuronMapModel model = new NeuronMapModel();
        FieldHistory history = new FieldHistory();
        history.initialize(model);

        for (int i = 0; i < 31; i++) {
            model.createNeuron(
                    NeuronType.EXCITATORY,
                    i * 10,
                    i * 10
            );
            assertTrue(history.commitSavedState(model));
        }

        assertEquals(FieldHistory.MAX_UNDO_STEPS, history.undoSize());

        for (int i = 0; i < FieldHistory.MAX_UNDO_STEPS; i++) {
            assertTrue(history.undo(model));
        }

        assertEquals(1, model.neurons().size());
        assertEquals(FieldHistory.MAX_UNDO_STEPS, history.redoSize());
    }

    /**
     * Перевіряє, що повторна ініціалізація створює нову історію без попередніх записів.
     */
    @Test
    void initializeStartsCompletelyNewHistory() {
        NeuronMapModel model = new NeuronMapModel();
        FieldHistory history = new FieldHistory();
        history.initialize(model);

        model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        assertTrue(history.commitSavedState(model));

        history.initialize(model);

        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }
}
