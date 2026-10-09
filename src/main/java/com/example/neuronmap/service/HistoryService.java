package com.example.neuronmap.service;

import com.example.neuronmap.application.history.FieldHistory;
import com.example.neuronmap.model.NeuronMapModel;

import java.util.Objects;

/**
 * Надає редактору операції скасування й повторення, делегуючи зберігання знімків компоненту історії.
 */
public final class HistoryService {

    private final NeuronMapModel model;
    private final FieldHistory history;

    /**
     * Створює екземпляр HistoryService та зберігає передані залежності, потрібні для його роботи.
     *
     * @param model модель карти нейронів.
     */
    public HistoryService(NeuronMapModel model) {
        this(model, new FieldHistory());
    }

    /**
     * Створює екземпляр HistoryService та зберігає передані залежності, потрібні для його роботи.
     *
     * @param model модель карти нейронів.
     * @param history значення «history», яке використовується в цьому методі.
     */
    public HistoryService(
            NeuronMapModel model,
            FieldHistory history
    ) {
        this.model = Objects.requireNonNull(model, "model");
        this.history = Objects.requireNonNull(history, "history");
    }

    /**
     * Ініціалізує історію поточного стану карти.
     */
    public void initialize() {
        history.initialize(model);
    }

    /**
     * Відновлює попередній стан карти.
     */
    public boolean undo() {
        return history.undo(model);
    }

    /**
     * Повторно застосовує скасований стан карти.
     */
    public boolean redo() {
        return history.redo(model);
    }

    /**
     * Перевіряє, чи дозволяє поточний стан виконати undo.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean canUndo() {
        return history.canUndo();
    }

    /**
     * Перевіряє, чи дозволяє поточний стан виконати redo.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean canRedo() {
        return history.canRedo();
    }

    /**
     * Фіксує поточний стан як новий крок історії, якщо збережені дані змінилися.
     */
    public void commitSavedState() {
        history.commitSavedState(model);
    }

    /**
     * Очищає  від тимчасових або застарілих значень.
     */
    public void clear() {
        history.clear(model);
    }

    /**
     * Повертає кількість змін, які ще можна скасувати.
     *
     * @return кількість змін, які ще можна скасувати.
     */
    public int undoSize() {
        return history.undoSize();
    }

    /**
     * Повертає кількість скасованих змін, які можна повторити.
     *
     * @return кількість скасованих змін, які можна повторити.
     */
    public int redoSize() {
        return history.redoSize();
    }

    /**
     * Повертає компонент, що зберігає історію станів карти.
     *
     * @return компонент, що зберігає історію станів карти.
     */
    public FieldHistory history() {
        return history;
    }
}
