package com.example.neuronmap.simulation;

import com.example.neuronmap.model.NeuronMapModel;

/**
 * Надає операції запуску й виконання симуляції, не повʼязані з JavaFX.
 */
public final class SimulationService {

    /**
     * Запускає або планує дію, повʼязану з «потрібні дані».
     *
     * @param model модель карти нейронів.
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
