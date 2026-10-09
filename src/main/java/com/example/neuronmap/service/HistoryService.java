package com.example.neuronmap.service;

import com.example.neuronmap.application.history.FieldHistory;
import com.example.neuronmap.model.NeuronMapModel;

import java.util.Objects;

/**
 * Надає операції для скасування й повторення змін карти.
 */
public final class HistoryService {

    private final NeuronMapModel model;
    private final FieldHistory history;

    /**
     * Повертає результат операції «історія служба».
     *
     * @param model модель карти нейронів.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public HistoryService(NeuronMapModel model) {
        this(model, new FieldHistory());
    }

    /**
     * Повертає результат операції «історія служба».
     *
     * @param model модель карти нейронів.
     *
     * @param history значення, що визначає історія для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public HistoryService(
            NeuronMapModel model,
            FieldHistory history
    ) {
        this.model = Objects.requireNonNull(model, "model");
        this.history = Objects.requireNonNull(history, "history");
    }

    /**
     * Виконує операцію «відповідну операцію».
     */
    public void initialize() {
        history.initialize(model);
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean undo() {
        return history.undo(model);
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean redo() {
        return history.redo(model);
    }

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean canUndo() {
        return history.canUndo();
    }

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean canRedo() {
        return history.canRedo();
    }

    /**
     * Виконує операцію «збережений стан».
     */
    public void commitSavedState() {
        history.commitSavedState(model);
    }

    /**
     * Видаляє або скидає дані, повʼязані з «потрібні дані».
     */
    public void clear() {
        history.clear(model);
    }

    /**
     * Повертає результат операції «розмір».
     *
     * @return числове значення, визначене методом.
     */
    public int undoSize() {
        return history.undoSize();
    }

    /**
     * Повертає результат операції «розмір».
     *
     * @return числове значення, визначене методом.
     */
    public int redoSize() {
        return history.redoSize();
    }

    /**
     * Повертає результат операції «історія».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public FieldHistory history() {
        return history;
    }
}
