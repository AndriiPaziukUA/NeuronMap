package com.example.neuronmap.application.history;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевіряє збереження станів, скасування й повторення змін та межі історії.
 */
class FieldHistoryTest {

    /**
     * Перевіряє очікувану поведінку: і відновити поле стан.
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
     * Перевіряє очікувану поведінку: новий змінити після гілка.
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
     * Перевіряє очікувану поведінку: і лише не створити історія.
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
     * Перевіряє очікувану поведінку: історія до кроки.
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
     * Перевіряє очікувану поведінку: запускає новий історія.
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
