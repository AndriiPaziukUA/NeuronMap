package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One live synchronous simulation run. */
public final class SimulationSession {

    private final NeuronMapModel model;
    private final int maxTicks;
    private final Map<String, Integer> pendingSignals =
            new LinkedHashMap<>();
    private final String manualSourceNeuronId;
    private boolean manualStartPending;

    private int tick;
    private boolean finished;

    SimulationSession(
            NeuronMapModel model,
            String sourceNeuronId,
            int maxTicks
    ) {
        this.model = model;
        this.maxTicks = maxTicks;
        this.manualSourceNeuronId = null;
        pendingSignals.put(sourceNeuronId, 1);
    }

    private SimulationSession(
            NeuronMapModel model,
            String sourceNeuronId,
            int maxTicks,
            boolean manualStart
    ) {
        this.model = model;
        this.maxTicks = maxTicks;
        this.manualSourceNeuronId = sourceNeuronId;
        this.manualStartPending = manualStart;
    }

    public static SimulationSession manual(
            NeuronMapModel model,
            String sourceNeuronId,
            int maxTicks
    ) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        if (sourceNeuronId == null || sourceNeuronId.isBlank()) {
            throw new IllegalArgumentException(
                    "sourceNeuronId must not be blank"
            );
        }
        if (maxTicks <= 0) {
            throw new IllegalArgumentException(
                    "maxTicks must be positive"
            );
        }

        return new SimulationSession(
                model,
                sourceNeuronId,
                maxTicks,
                true
        );
    }

    public boolean isFinished() {
        return finished;
    }

    public int tick() {
        return tick;
    }

    public SimulationStep nextStep() {
        if (finished || (pendingSignals.isEmpty() && !manualStartPending)) {
            finished = true;
            return null;
        }

        Map<String, Integer> inputSums =
                new LinkedHashMap<>(pendingSignals);
        pendingSignals.clear();

        Set<String> activatedNeuronIds =
                new LinkedHashSet<>();
        Map<String, Integer> nextSignals =
                new LinkedHashMap<>();

        if (manualStartPending) {
            manualStartPending = false;

            Neuron neuron = model.neuron(manualSourceNeuronId);
            if (neuron != null) {
                activatedNeuronIds.add(neuron.id());
                collectOutgoingSignals(neuron, nextSignals);
            }
        }

        for (Map.Entry<String, Integer> entry : inputSums.entrySet()) {
            Neuron neuron = model.neuron(entry.getKey());

            // A neuron deleted after a pulse was scheduled may remain in the
            // pending map for this tick, but it must not participate any more.
            if (neuron == null) {
                continue;
            }

            int sum = entry.getValue();

            if (sum < neuron.activationThreshold()) {
                continue;
            }

            activatedNeuronIds.add(neuron.id());
            collectOutgoingSignals(neuron, nextSignals);
        }

        SimulationStep step = new SimulationStep(
                tick,
                inputSums,
                activatedNeuronIds,
                nextSignals
        );

        tick++;

        if (nextSignals.isEmpty() || tick >= maxTicks) {
            finished = true;
        } else {
            pendingSignals.putAll(nextSignals);
        }

        return step;
    }

    private void collectOutgoingSignals(
            Neuron neuron,
            Map<String, Integer> nextSignals
    ) {
        for (Connection connection : model.connections()) {
            if (!connection.sourceId().equals(neuron.id())) {
                continue;
            }

            if (model.neuron(connection.targetId()) == null) {
                continue;
            }

            nextSignals.merge(
                    connection.targetId(),
                    neuron.signalWeight(),
                    Integer::sum
            );
        }
    }
}
