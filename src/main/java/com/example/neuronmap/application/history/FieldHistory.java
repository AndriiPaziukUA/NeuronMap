package com.example.neuronmap.application.history;

import com.example.neuronmap.model.NeuronMapModel;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Зберігає історію попередніх значень поля та підтримує скасування й повторення змін.
 */
public final class FieldHistory {

    public static final int MAX_UNDO_STEPS = 30;

    private final Deque<FieldStateSnapshot> undoStack =
            new ArrayDeque<>();
    private final Deque<FieldStateSnapshot> redoStack =
            new ArrayDeque<>();

    private FieldStateSnapshot currentState;

/**
 * Виконує операцію «відповідну операцію».
 *
 * @param model модель карти нейронів.
 */
public void initialize(NeuronMapModel model) {
        currentState = capture(model);
        undoStack.clear();
        redoStack.clear();
    }

/**
 * Повертає результат операції «збережений стан».
 *
 * @param model модель карти нейронів.
 *
 * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

/**
 * Повертає результат операції «відповідну операцію».
 *
 * @param model модель карти нейронів.
 *
 * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
 * Повертає результат операції «відповідну операцію».
 *
 * @param model модель карти нейронів.
 *
 * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
     * Повертає результат операції «розмір».
     *
     * @return числове значення, визначене методом.
     */
    public int undoSize() {
        return undoStack.size();
    }

    /**
     * Повертає результат операції «розмір».
     *
     * @return числове значення, визначене методом.
     */
    public int redoSize() {
        return redoStack.size();
    }

/**
 * Видаляє або скидає дані, повʼязані з «потрібні дані».
 *
 * @param model модель карти нейронів.
 */
public void clear(NeuronMapModel model) {
        initialize(model);
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param model модель карти нейронів.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static FieldStateSnapshot capture(NeuronMapModel model) {
        if (model == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("Model must not be null.");
        }
        return FieldStateSnapshot.capture(model);
    }

    /**
     * Виконує операцію «потребувати модель».
     *
     * @param model модель карти нейронів.
     */
    private static void requireModel(NeuronMapModel model) {
        if (model == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("Model must not be null.");
        }
    }

    /**
     * Виконує операцію «потребувати».
     */
    private void requireInitialized() {
        if (currentState == null) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException(
                    "Field history is not initialized."
            );
        }
    }

    /**
     * Виконує операцію «відповідну операцію».
     */
    private void trimUndoStack() {
        while (undoStack.size() > MAX_UNDO_STEPS) {
            undoStack.removeFirst();
        }
    }
}
