package com.example.neuronmap.service;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.NeuronMapModel;

import java.util.Objects;

/**
 * Реалізує операції створення, пошуку й видалення зв’язків між нейронами поза контролерами інтерфейсу.
 */
public final class ConnectionService {

    private final NeuronMapModel model;

    /**
     * Створює екземпляр ConnectionService та зберігає передані залежності, потрібні для його роботи.
     *
     * @param model модель карти нейронів.
     */
    public ConnectionService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Повертає модель, над якою виконує операції служба зв’язків.
     *
     * @return модель, над якою виконує операції служба зв’язків.
     */
    public NeuronMapModel model() {
        return model;
    }

    /**
     * Створює напрямлений зв’язок від початкового нейрона до цільового, якщо це дозволяє модель.
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     * @param targetNeuronId ідентифікатор цільового нейрона.
     */
    public boolean create(String sourceNeuronId, String targetNeuronId) {
        return model.createConnection(sourceNeuronId, targetNeuronId);
    }

    /**
     * Видаляє зв’язок за його ідентифікатором.
     *
     * @param connectionId ідентифікатор зв’язку.
     */
    public boolean remove(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return false;
        }
        return model.removeConnection(connectionId) != null;
    }

    /**
     * Видаляє зв’язки між указаними нейронами.
     *
     * @param firstNeuronId ідентифікатор елемента, над яким виконується дія.
     * @param secondNeuronId ідентифікатор елемента, над яким виконується дія.
     */
    public int removeBetween(String firstNeuronId, String secondNeuronId) {
        return model.removeConnectionsBetween(firstNeuronId, secondNeuronId);
    }

    /**
     * Перевіряє, чи має нейрон хоча б один пов’язаний із ним зв’язок.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
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
     * Знаходить зв’язок за його ідентифікатором.
     *
     * @param connectionId ідентифікатор зв’язку.
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
     * Перевіряє наявність напрямленого зв’язку між указаними нейронами.
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     * @param targetNeuronId ідентифікатор цільового нейрона.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean contains(String sourceNeuronId, String targetNeuronId) {
        if (sourceNeuronId == null || targetNeuronId == null) {
            return false;
        }
        return model.hasConnection(sourceNeuronId, targetNeuronId);
    }
}
