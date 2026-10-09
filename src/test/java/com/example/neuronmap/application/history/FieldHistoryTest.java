package com.example.neuronmap.application.history;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевіряє скасування, повторення, відгалуження історії після нової зміни та обмеження розміру історії.
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
