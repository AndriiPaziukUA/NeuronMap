package com.example.neuronmap.application.history;

import com.example.neuronmap.model.NeuronMapModel;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * In-memory undo/redo history for editable field state.
 *
 * <p>The history is deliberately not persisted. It exists only for the
 * lifetime of one application run.</p>
 */
public final class FieldHistory {

    public static final int MAX_UNDO_STEPS = 30;

    private final Deque<FieldStateSnapshot> undoStack =
            new ArrayDeque<>();
    private final Deque<FieldStateSnapshot> redoStack =
            new ArrayDeque<>();

    private FieldStateSnapshot currentState;

    /** Starts a fresh history baseline at the supplied model state. */
    public void initialize(NeuronMapModel model) {
        currentState = capture(model);
        undoStack.clear();
        redoStack.clear();
    }

    /**
     * Commits the current model state at a save boundary.
     *
     * <p>Only changes visible in {@link FieldStateSnapshot} are recorded. This
     * means simulation activation, camera state and status messages do not
     * create history steps.</p>
     *
     * @return true when a new undo step was created
     */
    public boolean commitSavedState(NeuronMapModel model) {
        FieldStateSnapshot nextState = capture(model);

        if (currentState == null) {
            currentState = nextState;
            return false;
        }

        if (currentState.equals(nextState)) {
            return false;
        }

        undoStack.addLast(currentState);
        trimUndoStack();
        redoStack.clear();
        currentState = nextState;
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** Restores one previous field state. */
    public boolean undo(NeuronMapModel model) {
        requireInitialized();
        requireModel(model);

        if (!canUndo()) {
            return false;
        }

        FieldStateSnapshot liveState = capture(model);
        FieldStateSnapshot targetState = undoStack.removeLast();

        redoStack.addLast(liveState);
        currentState = targetState;
        targetState.restoreInto(model);
        return true;
    }

    /** Restores one next field state on the current redo branch. */
    public boolean redo(NeuronMapModel model) {
        requireInitialized();
        requireModel(model);

        if (!canRedo()) {
            return false;
        }

        FieldStateSnapshot liveState = capture(model);
        FieldStateSnapshot targetState = redoStack.removeLast();

        undoStack.addLast(liveState);
        trimUndoStack();
        currentState = targetState;
        targetState.restoreInto(model);
        return true;
    }

    public int undoSize() {
        return undoStack.size();
    }

    public int redoSize() {
        return redoStack.size();
    }

    /** Discards all history and starts a new baseline. */
    public void clear(NeuronMapModel model) {
        initialize(model);
    }

    private static FieldStateSnapshot capture(NeuronMapModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model must not be null.");
        }
        return FieldStateSnapshot.capture(model);
    }

    private static void requireModel(NeuronMapModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model must not be null.");
        }
    }

    private void requireInitialized() {
        if (currentState == null) {
            throw new IllegalStateException(
                    "Field history is not initialized."
            );
        }
    }

    private void trimUndoStack() {
        while (undoStack.size() > MAX_UNDO_STEPS) {
            undoStack.removeFirst();
        }
    }
}
