package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One global synchronous simulation timeline shared by all manual starts. */
public final class SimulationSession {

    private final NeuronMapModel model;
    private final Map<String, Integer> pendingSignals =
            new LinkedHashMap<>();
    private final Set<String> pendingManualStarts =
            new LinkedHashSet<>();

    private BigInteger tick = BigInteger.ZERO;
    private boolean finished;

    private SimulationSession(NeuronMapModel model) {
        this.model = model;
    }

    public static SimulationSession manual(
            NeuronMapModel model,
            String sourceNeuronId
    ) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }

        SimulationSession session = new SimulationSession(model);
        session.queueManualStart(sourceNeuronId);
        return session;
    }

    /**
     * Queues a manually triggered source for the next global simulation tick.
     * Multiple distinct sources queued before that tick are activated together.
     * Re-queuing the same source does not duplicate its outgoing signal.
     */
    public boolean queueManualStart(String sourceNeuronId) {
        if (sourceNeuronId == null || sourceNeuronId.isBlank()) {
            return false;
        }

        if (model.neuron(sourceNeuronId) == null) {
            return false;
        }

        pendingManualStarts.add(sourceNeuronId);
        finished = false;
        return true;
    }

    public boolean isFinished() {
        return finished;
    }

    public BigInteger tick() {
        return tick;
    }

    /** Returns whether there is work that can be processed on the next tick. */
    public boolean hasPendingWork() {
        return !pendingSignals.isEmpty()
                || !pendingManualStarts.isEmpty();
    }

    public SimulationStep nextStep() {
        if (finished || !hasPendingWork()) {
            finished = true;
            return null;
        }

        Map<String, Integer> inputSums =
                new LinkedHashMap<>(pendingSignals);
        pendingSignals.clear();

        Set<String> manualStarts =
                new LinkedHashSet<>(pendingManualStarts);
        pendingManualStarts.clear();

        Set<String> activatedNeuronIds =
                new LinkedHashSet<>();
        Map<String, Integer> nextSignals =
                new LinkedHashMap<>();

        for (String neuronId : manualStarts) {
            Neuron neuron = model.neuron(neuronId);
            if (neuron != null) {
                activatedNeuronIds.add(neuron.id());
            }
        }

        for (Map.Entry<String, Integer> entry : inputSums.entrySet()) {
            Neuron neuron = model.neuron(entry.getKey());

            // A neuron deleted after a pulse was scheduled may remain in the
            // pending map for this tick, but it must not participate any more.
            if (neuron == null) {
                continue;
            }

            if (entry.getValue() >= neuron.activationThreshold()) {
                activatedNeuronIds.add(neuron.id());
            }
        }

        // Each activated neuron emits once per global tick, even when it was
        // both manually started and activated by an incoming signal.
        for (String neuronId : activatedNeuronIds) {
            Neuron neuron = model.neuron(neuronId);
            if (neuron != null) {
                collectOutgoingSignals(neuron, nextSignals);
            }
        }

        SimulationStep step = new SimulationStep(
                tick,
                inputSums,
                activatedNeuronIds,
                nextSignals
        );

        tick = tick.add(BigInteger.ONE);

        if (nextSignals.isEmpty()) {
            finished = true;
        } else {
            pendingSignals.putAll(nextSignals);
            finished = false;
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
