package com.example.neuronmap.service.history;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що історія змін відновлює відкритість шухлядки структури через undo та redo. */
class FieldHistoryStructureDrawerStateTest {

    /** Перевіряє послідовне скасування й повторення відкриття шухлядки через знімки моделі. */
    @Test
    void undoAndRedoRestoreStructureDrawerState() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 20.0, 30.0);
        FieldHistory history = new FieldHistory();
        history.initialize(model);

        model.structure(structure.id()).setExpanded(true);
        assertTrue(history.commitSavedState(model));
        model.structure(structure.id()).setExpanded(false);
        assertTrue(history.commitSavedState(model));

        assertTrue(history.undo(model));
        assertTrue(model.structure(structure.id()).isExpanded());
        assertTrue(history.redo(model));
        assertFalse(model.structure(structure.id()).isExpanded());
    }
}
