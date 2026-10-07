package com.example.neuronmap.simulation;

import com.example.neuronmap.model.NeuronMapModel;

public final class SimulationService {

    public static final int MAX_TICKS = 256;

    public SimulationSession start(
            NeuronMapModel model,
            String sourceNeuronId
    ) {
        if (model == null || model.neuron(sourceNeuronId) == null) {
            return null;
        }

        return new SimulationSession(
                model,
                sourceNeuronId,
                MAX_TICKS
        );
    }
}
