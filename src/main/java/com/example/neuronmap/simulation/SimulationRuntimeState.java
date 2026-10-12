package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;

import java.util.Objects;

/**
 * Застосовує та очищує runtime-стан активації нейронів за результатами симуляції.
 */
public final class SimulationRuntimeState {

    private final NeuronMapModel model;

    /**
     * Створює компонент керування runtime-станом симуляції.
     *
     * @param model модель, у якій зберігаються поточні активації нейронів.
     */
    public SimulationRuntimeState(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Застосовує суми вхідних сигналів до активацій моделі, попередньо очищуючи попередній стан.
     *
     * @param step результат обробленого такту симуляції.
     */
    public void apply(SimulationStep step) {
        Objects.requireNonNull(step, "step");
        model.clearActivations();
        step.inputSums().forEach((neuronId, sum) -> {
            Neuron neuron = model.neuron(neuronId);
            if (neuron != null) {
                neuron.setActivation(sum);
            }
        });
    }

    /**
     * Очищує runtime-активації всіх нейронів моделі.
     */
    public void clear() {
        model.clearActivations();
    }
}
