package com.example.neuronmap.service;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.NeuronMapModel;

import java.util.Objects;

/** Business operations for graph connections. */
public final class ConnectionService {

    private final NeuronMapModel model;

    public ConnectionService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    public NeuronMapModel model() {
        return model;
    }

    public boolean create(String sourceNeuronId, String targetNeuronId) {
        return model.createConnection(sourceNeuronId, targetNeuronId);
    }

    public boolean remove(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return false;
        }
        return model.removeConnection(connectionId) != null;
    }

    public int removeBetween(String firstNeuronId, String secondNeuronId) {
        return model.removeConnectionsBetween(firstNeuronId, secondNeuronId);
    }

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

    public Connection find(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return null;
        }

        return model.connections().stream()
                .filter(connection -> connection.id().equals(connectionId))
                .findFirst()
                .orElse(null);
    }

    public boolean contains(String sourceNeuronId, String targetNeuronId) {
        if (sourceNeuronId == null || targetNeuronId == null) {
            return false;
        }
        return model.hasConnection(sourceNeuronId, targetNeuronId);
    }
}
