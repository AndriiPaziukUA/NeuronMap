package com.example.neuronmap.service;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.service.history.FieldHistory;

import java.util.Objects;

/**
 * Надає операції скасування й повторення змін карти, делегуючи керування знімками компоненту історії.
 */
public final class HistoryService {

    private final NeuronMapModel model;
    private final FieldHistory history;

    /**
     * Створює службу історії для заданої моделі карти.
     *
     * @param model модель карти нейронів.
     */
    public HistoryService(NeuronMapModel model) {
        this(model, new FieldHistory());
    }

    /**
     * Створює службу історії з переданою реалізацією механізму скасування й повторення.
     *
     * @param model модель карти нейронів.
     * @param history компонент керування знімками історії.
     */
    public HistoryService(
            NeuronMapModel model,
            FieldHistory history
    ) {
        this.model = Objects.requireNonNull(model, "model");
        this.history = Objects.requireNonNull(history, "history");
    }

    /**
     * Ініціалізує історію з поточного стану карти.
     */
    public void initialize() {
        history.initialize(model);
    }

    /**
     * Відновлює попередній стан карти, якщо доступний крок скасування.
     *
     * @return {@code true}, якщо стан карти було відновлено; інакше {@code false}.
     */
    public boolean undo() {
        return history.undo(model);
    }

    /**
     * Повторно застосовує раніше скасований стан карти, якщо він доступний.
     *
     * @return {@code true}, якщо стан карти було повторно застосовано; інакше {@code false}.
     */
    public boolean redo() {
        return history.redo(model);
    }

    /**
     * Перевіряє, чи доступне скасування останньої зміни.
     *
     * @return {@code true}, якщо скасування доступне; інакше {@code false}.
     */
    public boolean canUndo() {
        return history.canUndo();
    }

    /**
     * Перевіряє, чи доступне повторне застосування скасованої зміни.
     *
     * @return {@code true}, якщо повторення доступне; інакше {@code false}.
     */
    public boolean canRedo() {
        return history.canRedo();
    }

    /**
     * Фіксує поточний стан моделі, якщо її збережені дані змінилися.
     */
    public void commitSavedState() {
        history.commitSavedState(model);
    }

    /**
     * Очищає історію та встановлює поточний стан моделі як початковий.
     */
    public void clear() {
        history.clear(model);
    }

    /**
     * Повертає кількість змін, доступних для скасування.
     *
     * @return кількість доступних кроків скасування.
     */
    public int undoSize() {
        return history.undoSize();
    }

    /**
     * Повертає кількість скасованих змін, доступних для повторення.
     *
     * @return кількість доступних кроків повторення.
     */
    public int redoSize() {
        return history.redoSize();
    }
}
