package com.example.neuronmap.simulation;

import com.example.neuronmap.model.NeuronMapModel;

public final class SimulationService {

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
