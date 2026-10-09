package com.example.neuronmap.application.history;

import com.example.neuronmap.model.NeuronMapModel;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Зберігає знімки стану карти та реалізує стек скасування й повторення змін, не записуючи зміни, що не впливають на збережені дані.
 */
public final class FieldHistory {

    public static final int MAX_UNDO_STEPS = 30;

    private final Deque<FieldStateSnapshot> undoStack =
            new ArrayDeque<>();
    private final Deque<FieldStateSnapshot> redoStack =
            new ArrayDeque<>();

    private FieldStateSnapshot currentState;

/**
 * Створює початковий знімок карти та очищає стеки скасування й повторення.
 *
 * @param model модель карти нейронів.
 */
public void initialize(NeuronMapModel model) {
        currentState = capture(model);
        undoStack.clear();
        redoStack.clear();
    }

/**
 * Фіксує новий знімок стану, лише якщо змінилися дані, які потрібно зберігати.
 *
 * @param model модель карти нейронів.
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

    /**
     * Перевіряє, чи є в історії стан для скасування.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Перевіряє, чи є стан для повторення.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

/**
 * Відновлює попередній збережений стан моделі та переносить поточний стан до стека повторення.
 *
 * @param model модель карти нейронів.
 */
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

/**
 * Повторно застосовує стан зі стека повторення та оновлює стек скасування.
 *
 * @param model модель карти нейронів.
 */
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

    /**
     * Повертає кількість доступних записів у стеку скасування.
     *
     * @return кількість доступних записів у стеку скасування.
     */
    public int undoSize() {
        return undoStack.size();
    }

    /**
     * Повертає кількість доступних записів у стеку повторення.
     *
     * @return кількість доступних записів у стеку повторення.
     */
    public int redoSize() {
        return redoStack.size();
    }

/**
 * Очищає  від тимчасових або застарілих значень.
 *
 * @param model модель карти нейронів.
 */
public void clear(NeuronMapModel model) {
        initialize(model);
    }

    /**
     * Створює знімок стану моделі для порівняння або відновлення.
     *
     * @param model модель карти нейронів.
     */
    private static FieldStateSnapshot capture(NeuronMapModel model) {
        if (model == null) {

            throw new IllegalArgumentException("Model must not be null.");
        }
        return FieldStateSnapshot.capture(model);
    }

    /**
     * Перевіряє, що передано ненульову модель карти.
     *
     * @param model модель карти нейронів.
     */
    private static void requireModel(NeuronMapModel model) {
        if (model == null) {

            throw new IllegalArgumentException("Model must not be null.");
        }
    }

    /**
     * Перевіряє, що історію ініціалізовано перед виконанням скасування чи повторення.
     */
    private void requireInitialized() {
        if (currentState == null) {

            throw new IllegalStateException(
                    "Field history is not initialized."
            );
        }
    }

    /**
     * Видаляє найстаріші записи, якщо стек скасування перевищив дозволену кількість кроків.
     */
    private void trimUndoStack() {
        while (undoStack.size() > MAX_UNDO_STEPS) {
            undoStack.removeFirst();
        }
    }
}
