package com.example.neuronmap.service;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.NeuronMapModel;

import java.util.Objects;

/**
 * Виконує операції зі звʼязками та перевіряє правила їх створення й видалення.
 */
public final class ConnectionService {

    private final NeuronMapModel model;

    /**
     * Повертає результат операції «звʼязок служба».
     *
     * @param model модель карти нейронів.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public ConnectionService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Повертає результат операції «модель».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronMapModel model() {
        return model;
    }

    /**
     * Створює обʼєкт із переданих даних «потрібні дані».
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     *
     * @param targetNeuronId ідентифікатор кінцевого нейрона.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean create(String sourceNeuronId, String targetNeuronId) {
        return model.createConnection(sourceNeuronId, targetNeuronId);
    }

    /**
     * Видаляє або скидає дані, повʼязані з «потрібні дані».
     *
     * @param connectionId ідентифікатор звʼязку.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean remove(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return false;
        }
        return model.removeConnection(connectionId) != null;
    }

    /**
     * Видаляє або скидає дані, повʼязані з «відповідну операцію».
     *
     * @param firstNeuronId значення, що визначає перший нейрон ідентифікатор для цієї операції.
     *
     * @param secondNeuronId значення, що визначає нейрон ідентифікатор для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    public int removeBetween(String firstNeuronId, String secondNeuronId) {
        return model.removeConnectionsBetween(firstNeuronId, secondNeuronId);
    }

    /**
     * Перевіряє, чи виконується умова «звʼязки».
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean hasConnections(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return false;
        }

        return model.connections().stream()
                .anyMatch(connection ->
                        neuronId.equals(connection.sourceId())
                                || neuronId.equals(connection.targetId())
                );
    }

    /**
     * Повертає або знаходить дані, повʼязані з «потрібні дані».
     *
     * @param connectionId ідентифікатор звʼязку.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Connection find(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return null;
        }

        return model.connections().stream()
                .filter(connection -> connection.id().equals(connectionId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Перевіряє, чи виконується умова «потрібні дані».
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     *
     * @param targetNeuronId ідентифікатор кінцевого нейрона.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean contains(String sourceNeuronId, String targetNeuronId) {
        if (sourceNeuronId == null || targetNeuronId == null) {
            return false;
        }
        return model.hasConnection(sourceNeuronId, targetNeuronId);
    }
}
