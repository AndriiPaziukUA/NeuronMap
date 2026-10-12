package com.example.neuronmap.service.history;

import com.example.neuronmap.model.NeuronMapModel;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Керує знімками моделі та стеками скасування й повторення змін.
 */
public final class FieldHistory {

    /** Максимальна кількість кроків, доступних для скасування. */
    public static final int MAX_UNDO_STEPS = 30;

    private final Deque<FieldStateSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<FieldStateSnapshot> redoStack = new ArrayDeque<>();
    private FieldStateSnapshot currentState;

    /**
     * Створює порожню історію змін, яку потрібно ініціалізувати перед використанням.
     */
    public FieldHistory() {
    }

    /**
     * Запам’ятовує поточний стан моделі та очищає попередні кроки історії.
     *
     * @param model модель карти нейронів.
     */
    public void initialize(NeuronMapModel model) {
        currentState = capture(model);
        undoStack.clear();
        redoStack.clear();
    }

    /**
     * Додає крок історії, якщо збережені дані моделі відрізняються від поточного знімка.
     *
     * @param model модель карти нейронів.
     * @return {@code true}, якщо до історії додано новий крок; інакше {@code false}.
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
     * Перевіряє, чи доступний крок для скасування.
     *
     * @return {@code true}, якщо скасування доступне; інакше {@code false}.
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Перевіряє, чи доступний крок для повторення.
     *
     * @return {@code true}, якщо повторення доступне; інакше {@code false}.
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Відновлює попередній стан моделі та додає поточний стан до стека повторення.
     *
     * @param model модель карти нейронів.
     * @return {@code true}, якщо стан було скасовано; інакше {@code false}.
     * @throws IllegalStateException якщо історію не ініціалізовано.
     * @throws IllegalArgumentException якщо модель не задано.
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
     * @return {@code true}, якщо стан було повторено; інакше {@code false}.
     * @throws IllegalStateException якщо історію не ініціалізовано.
     * @throws IllegalArgumentException якщо модель не задано.
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
     * Повертає кількість доступних кроків скасування.
     *
     * @return кількість елементів у стеку скасування.
     */
    public int undoSize() {
        return undoStack.size();
    }

    /**
     * Повертає кількість доступних кроків повторення.
     *
     * @return кількість елементів у стеку повторення.
     */
    public int redoSize() {
        return redoStack.size();
    }

    /**
     * Очищає історію та встановлює поточний стан моделі як початковий.
     *
     * @param model модель карти нейронів.
     */
    public void clear(NeuronMapModel model) {
        initialize(model);
    }

    /**
     * Створює знімок моделі після перевірки аргументу.
     *
     * @param model модель карти нейронів.
     * @return знімок поточного стану моделі.
     * @throws IllegalArgumentException якщо модель не задано.
     */
    private static FieldStateSnapshot capture(NeuronMapModel model) {
        requireModel(model);
        return FieldStateSnapshot.capture(model);
    }

    /**
     * Перевіряє, що модель карти задано.
     *
     * @param model модель карти нейронів.
     * @throws IllegalArgumentException якщо модель не задано.
     */
    private static void requireModel(NeuronMapModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model must not be null.");
        }
    }

    /**
     * Перевіряє, що історія ініціалізована перед скасуванням або повторенням.
     *
     * @throws IllegalStateException якщо історію не ініціалізовано.
     */
    private void requireInitialized() {
        if (currentState == null) {
            throw new IllegalStateException("Field history is not initialized.");
        }
    }

    /**
     * Видаляє найстаріші кроки, якщо кількість доступних скасувань перевищила встановлену межу.
     */
    private void trimUndoStack() {
        while (undoStack.size() > MAX_UNDO_STEPS) {
            undoStack.removeFirst();
        }
    }
}
