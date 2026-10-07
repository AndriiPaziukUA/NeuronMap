package com.example.neuronmap.service;

import com.example.neuronmap.application.history.FieldHistory;
import com.example.neuronmap.model.NeuronMapModel;

import java.util.Objects;

/** Application service around undo/redo history. */
public final class HistoryService {

    private final NeuronMapModel model;
    private final FieldHistory history;

    public HistoryService(NeuronMapModel model) {
        this(model, new FieldHistory());
    }

    public HistoryService(
            NeuronMapModel model,
            FieldHistory history
    ) {
        this.model = Objects.requireNonNull(model, "model");
        this.history = Objects.requireNonNull(history, "history");
    }

    public void initialize() {
        history.initialize(model);
    }

    public boolean undo() {
        return history.undo(model);
    }

    public boolean redo() {
        return history.redo(model);
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public void commitSavedState() {
        history.commitSavedState(model);
    }

    public void clear() {
        history.clear(model);
    }

    public int undoSize() {
        return history.undoSize();
    }

    public int redoSize() {
        return history.redoSize();
    }

    public FieldHistory history() {
        return history;
    }
}
