package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;

import java.util.Collection;
import java.util.Objects;

/**
 * Надає операції над нейронами: створення, пошук, переміщення, обертання, зміну типу, напрямку й параметрів сигналу.
 */
public final class NeuronService {

    private final NeuronMapModel model;

    /**
     * Створює екземпляр NeuronService та зберігає передані залежності, потрібні для його роботи.
     *
     * @param model модель карти нейронів.
     */
    public NeuronService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Повертає модель карти нейронів.
     *
     * @return модель карти нейронів.
     */
    public NeuronMapModel model() { return model; }

    /**
     * Повертає службу операцій над нейронами.
     *
     * @return службу операцій над нейронами.
     */
    public Collection<Neuron> neurons() { return model.neurons(); }

    /**
     * Знаходить  за заданими координатами або критеріями пошуку.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public Neuron find(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) return null;
        return model.neuron(neuronId);
    }

    /**
     * Повертає візуальне подання нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return візуальне подання нейрона.
     */
    public NeuronPresentation presentation(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) return null;
        return model.presentation(neuronId);
    }

    /**
     * Створює нейрон указаного типу в заданих координатах.
     *
     * @param type тип нейрона або елемента.
     * @param x координата X.
     * @param y координата Y.
     */
    public Neuron create(NeuronType type, double x, double y) {
        return model.createNeuron(type, x, y);
    }

    /**
     * Видаляє нейрон і пов’язані з ним дані.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public Neuron remove(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) return null;
        return model.removeNeuron(neuronId);
    }

    /**
     * Перемикає тип нейрона та повертає ознаку успішної зміни.
     *
     * @param neuronId ідентифікатор нейрона.
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
     * Розвертає напрямок вхідного й вихідного портів нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
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
     * @param signalStrength сила сигналу нейрона.
     * @param activationThreshold поріг суми вхідних сигналів для активації нейрона.
     */
    public boolean updateSettings(String neuronId, int signalStrength, int activationThreshold) {
        Neuron neuron = find(neuronId);
        if (neuron == null) return false;
        neuron.setSignalStrength(signalStrength);
        neuron.setActivationThreshold(activationThreshold);
        return true;
    }

    /**
     * Переміщує нейрон на задане зміщення.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param dx значення «dx», яке використовується в цьому методі.
     * @param dy значення «dy», яке використовується в цьому методі.
     */
    public boolean move(String neuronId, double dx, double dy) {
        NeuronPresentation presentation = presentation(neuronId);
        if (presentation == null) return false;
        presentation.moveBy(dx, dy);
        return true;
    }

    /**
     * Установлює кут обертання нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param degrees кут у градусах.
     */
    public boolean setRotation(String neuronId, double degrees) {
        NeuronPresentation presentation = presentation(neuronId);
        if (presentation == null) return false;
        presentation.setRotationDegrees(degrees);
        return true;
    }

    /**
     * Очищає activations від тимчасових або застарілих значень.
     */
    public void clearActivations() { model.clearActivations(); }

    /**
     * Оновлює поточне значення активації нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param activation значення «activation», яке використовується в цьому методі.
     */
    public boolean setActivation(String neuronId, int activation) {
        Neuron neuron = find(neuronId);
        if (neuron == null) return false;
        neuron.setActivation(activation);
        return true;
    }
}
