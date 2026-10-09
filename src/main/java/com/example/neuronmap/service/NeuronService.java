package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;

import java.util.Collection;
import java.util.Objects;

/**
 * Надає операції створення, пошуку, налаштування, переміщення та видалення нейронів.
 */
public final class NeuronService {

    private final NeuronMapModel model;

    /**
     * Повертає результат операції «нейрон служба».
     *
     * @param model модель карти нейронів.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Повертає модель карти, з якою працює служба.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronMapModel model() { return model; }

    /**
     * Повертає нейрони, наявні в моделі карти.
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public Collection<Neuron> neurons() { return model.neurons(); }

    /**
     * Знаходить нейрон за ідентифікатором; повертає null, якщо його немає.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Neuron find(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) return null;
        return model.neuron(neuronId);
    }

    /**
     * Знаходить дані відображення нейрона; повертає null, якщо нейрона немає.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronPresentation presentation(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) return null;
        return model.presentation(neuronId);
    }

    /**
     * Створює нейрон заданого типу в указаних координатах.
     *
     * @param type тип обʼєкта.
     *
     * @param x координата по горизонталі.
     *
     * @param y координата по вертикалі.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Neuron create(NeuronType type, double x, double y) {
        return model.createNeuron(type, x, y);
    }

    /**
     * Видаляє нейрон за ідентифікатором і повертає його, якщо його знайдено.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Neuron remove(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) return null;
        return model.removeNeuron(neuronId);
    }

    /**
     * Перемикає нейрон між збуджувальним і гальмівним типами.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean toggleType(String neuronId) {
        Neuron neuron = find(neuronId);
        if (neuron == null) return false;
        neuron.setType(
                neuron.type() == NeuronType.EXCITATORY
                        ? NeuronType.INHIBITORY
                        : NeuronType.EXCITATORY
        );
        return true;
    }

    /**
     * Перемикає напрямок відображення нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean toggleDirection(String neuronId) {
        NeuronPresentation presentation = presentation(neuronId);
        if (presentation == null) return false;
        presentation.toggleDirection();
        return true;
    }

    /**
     * Оновлює силу сигналу та поріг активації нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @param signalStrength сила сигналу нейрона.
     *
     * @param activationThreshold поріг активації нейрона.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean updateSettings(String neuronId, int signalStrength, int activationThreshold) {
        Neuron neuron = find(neuronId);
        if (neuron == null) return false;
        neuron.setSignalStrength(signalStrength);
        neuron.setActivationThreshold(activationThreshold);
        return true;
    }

    /**
     * Зміщує нейрон на задану різницю координат.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @param dx зміщення по горизонталі.
     *
     * @param dy зміщення по вертикалі.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean move(String neuronId, double dx, double dy) {
        NeuronPresentation presentation = presentation(neuronId);
        if (presentation == null) return false;
        presentation.moveBy(dx, dy);
        return true;
    }

    /**
     * Задає кут повороту нейрона в градусах.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @param degrees кут повороту в градусах.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean setRotation(String neuronId, double degrees) {
        NeuronPresentation presentation = presentation(neuronId);
        if (presentation == null) return false;
        presentation.setRotationDegrees(degrees);
        return true;
    }

    /**
     * Скидає значення активації всіх нейронів карти.
     */
    public void clearActivations() { model.clearActivations(); }

    /**
     * Задає значення активації нейрона за його ідентифікатором.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @param activation значення активації нейрона.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean setActivation(String neuronId, int activation) {
        Neuron neuron = find(neuronId);
        if (neuron == null) return false;
        neuron.setActivation(activation);
        return true;
    }
}
