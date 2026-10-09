package com.example.neuronmap.simulation;

import com.example.neuronmap.model.NeuronMapModel;

/**
 * Створює сеанс симуляції для моделі карти та початкового нейрона.
 */
public final class SimulationService {

    /**
     * Створює сеанс симуляції для моделі та ставить вказаний нейрон у чергу ручного запуску.
     *
     * @param model модель карти нейронів.
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     */
    public SimulationSession start(
            NeuronMapModel model,
            String sourceNeuronId
    ) {
        if (model == null || model.neuron(sourceNeuronId) == null) {
            return null;
        }

        return SimulationSession.manual(
                model,
                sourceNeuronId
        );
    }
}
