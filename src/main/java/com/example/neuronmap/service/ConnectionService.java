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
     * Створює службу зв’язків для заданої моделі карти.
     *
     * @param model модель карти нейронів, у якій зберігаються зв’язки.
     * @throws NullPointerException якщо модель не задано.
     */
    public ConnectionService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Повертає модель, над якою виконує операції служба зв’язків.
     *
     * @return модель карти нейронів.
     */
    public NeuronMapModel model() {
        return model;
    }

    /**
     * Створює напрямлений зв’язок від початкового нейрона до цільового, якщо це дозволяє модель.
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона, з якого виходить зв’язок.
     * @param targetNeuronId ідентифікатор цільового нейрона, до якого входить зв’язок.
     * @return {@code true}, якщо модель створила зв’язок; інакше {@code false}.
     */
    public boolean create(String sourceNeuronId, String targetNeuronId) {
        return model.createConnection(sourceNeuronId, targetNeuronId);
    }

    /**
     * Видаляє зв’язок за його ідентифікатором.
     *
     * @param connectionId ідентифікатор зв’язку.
     * @return {@code true}, якщо зв’язок існував і був видалений; для некоректного або відсутнього
     *         ідентифікатора повертає {@code false}.
     */
    public boolean remove(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return false;
        }
        return model.removeConnection(connectionId) != null;
    }

    /**
     * Видаляє зв’язки між двома заданими нейронами незалежно від напрямку кожного зв’язку.
     *
     * @param firstNeuronId ідентифікатор першого нейрона.
     * @param secondNeuronId ідентифікатор другого нейрона.
     * @return кількість видалених зв’язків; нуль, якщо між нейронами немає зв’язків для видалення.
     */
    public int removeBetween(String firstNeuronId, String secondNeuronId) {
        return model.removeConnectionsBetween(firstNeuronId, secondNeuronId);
    }

    /**
     * Перевіряє, чи має нейрон хоча б один зв’язок, у якому він є початковим або цільовим.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return {@code true}, якщо нейрон має пов’язаний зв’язок; для некоректного або невикористаного
     *         ідентифікатора повертає {@code false}.
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
     * @return знайдений зв’язок або {@code null}, якщо ідентифікатор є {@code null}, порожнім,
     *         складається лише з пробілів або такого зв’язку не існує.
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
     * Перевіряє наявність напрямленого зв’язку від початкового нейрона до цільового.
     *
     * @param sourceNeuronId ідентифікатор нейрона, з якого виходить зв’язок.
     * @param targetNeuronId ідентифікатор нейрона, до якого входить зв’язок.
     * @return {@code true}, якщо такий напрямлений зв’язок існує; інакше {@code false}.
     */
    public boolean contains(String sourceNeuronId, String targetNeuronId) {
        if (sourceNeuronId == null || targetNeuronId == null) {
            return false;
        }
        return model.hasConnection(sourceNeuronId, targetNeuronId);
    }
}
