package com.example.neuronmap.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NeuronMapModel {

    private final Map<String, Neuron> neurons = new LinkedHashMap<>();
    private final Map<String, NeuronPresentation> presentations = new LinkedHashMap<>();
    private final Map<String, Connection> connections = new LinkedHashMap<>();
    private final Map<String, NeuronGroup> groups = new LinkedHashMap<>();

    public Neuron createNeuron(
            NeuronType type,
            double x,
            double y
    ) {
        return createNeuron(
                type,
                x,
                y,
                0.0
        );
    }

    public Neuron createNeuron(
            NeuronType type,
            double x,
            double y,
            double rotationDegrees
    ) {
        String id = UUID.randomUUID().toString();

        return addNeuron(
                id,
                type,
                0,
                x,
                y,
                rotationDegrees
        );
    }

    public Neuron addNeuron(
            String id,
            NeuronType type,
            int activation
    ) {
        Neuron neuron = new Neuron(
                id,
                type,
                activation
        );

        neurons.put(id, neuron);
        presentations.put(
                id,
                new NeuronPresentation(
                        neuron,
                        0.0,
                        0.0,
                        0.0
                )
        );

        return neuron;
    }

    public Neuron addNeuron(
            String id,
            NeuronType type,
            int activation,
            double x,
            double y,
            double rotationDegrees
    ) {
        Neuron neuron = addNeuron(
                id,
                type,
                activation
        );

        presentation(id).setPosition(x, y);
        presentation(id).setRotationDegrees(rotationDegrees);

        return neuron;
    }

    public void addConnection(
            String id,
            String sourceId,
            String targetId
    ) {
        if (id == null || id.isBlank()
                || sourceId == null
                || targetId == null
                || !neurons.containsKey(sourceId)
                || !neurons.containsKey(targetId)
                || sourceId.equals(targetId)
                || hasConnection(sourceId, targetId)) {
            return;
        }

        connections.put(
                id,
                new Connection(
                        id,
                        sourceId,
                        targetId
                )
        );
    }

    public boolean createConnection(
            String sourceId,
            String targetId
    ) {
        if (sourceId == null
                || targetId == null
                || sourceId.equals(targetId)
                || neuron(sourceId) == null
                || neuron(targetId) == null
                || hasConnection(sourceId, targetId)) {
            return false;
        }

        addConnection(
                UUID.randomUUID().toString(),
                sourceId,
                targetId
        );

        return true;
    }

    public boolean hasConnection(
            String sourceId,
            String targetId
    ) {
        return connections.values()
                .stream()
                .anyMatch(connection ->
                        connection.sourceId().equals(sourceId)
                                && connection.targetId().equals(targetId)
                );
    }

    public Connection removeConnection(String id) {
        return connections.remove(id);
    }

    /**
     * Removes every connection between two neurons, regardless of direction.
     *
     * This is the operation used by the connection-delete mode: after
     * choosing neuron A in the menu and clicking neuron B, their contact
     * is completely severed.
     *
     * @return number of removed connections
     */
    public int removeConnectionsBetween(
            String firstNeuronId,
            String secondNeuronId
    ) {
        if (firstNeuronId == null
                || secondNeuronId == null
                || firstNeuronId.equals(secondNeuronId)) {
            return 0;
        }

        int before = connections.size();

        connections.values().removeIf(connection ->
                (connection.sourceId().equals(firstNeuronId)
                        && connection.targetId().equals(secondNeuronId))
                        || (connection.sourceId().equals(secondNeuronId)
                        && connection.targetId().equals(firstNeuronId))
        );

        return before - connections.size();
    }

    public Neuron removeNeuron(String neuronId) {
        Neuron removed = neurons.remove(neuronId);

        if (removed == null) {
            return null;
        }

        presentations.remove(neuronId);

        connections.values().removeIf(connection ->
                connection.sourceId().equals(neuronId)
                        || connection.targetId().equals(neuronId)
        );

        for (NeuronGroup group : new ArrayList<>(groups.values())) {
            group.removeMember(neuronId);

            if (group.memberIds().size() < 2) {
                groups.remove(group.id());
            }
        }

        return removed;
    }

    public void addGroup(
            String id,
            Collection<String> memberIds
    ) {
        Set<String> existingMembers = new LinkedHashSet<>();

        for (String memberId : memberIds) {
            if (neurons.containsKey(memberId)) {
                existingMembers.add(memberId);
            }
        }

        if (existingMembers.size() >= 2) {
            groups.put(
                    id,
                    new NeuronGroup(
                            id,
                            existingMembers
                    )
            );
        }
    }

    public Neuron neuron(String id) {
        return neurons.get(id);
    }

    public NeuronPresentation presentation(String neuronId) {
        return presentations.get(neuronId);
    }

    public Collection<Neuron> neurons() {
        return Collections.unmodifiableCollection(
                neurons.values()
        );
    }

    public Collection<NeuronPresentation> presentations() {
        return Collections.unmodifiableCollection(
                presentations.values()
        );
    }

    public Collection<Connection> connections() {
        return Collections.unmodifiableCollection(
                connections.values()
        );
    }

    public Collection<NeuronGroup> groups() {
        return Collections.unmodifiableCollection(
                groups.values()
        );
    }

    public NeuronGroup groupContaining(String neuronId) {
        for (NeuronGroup group : groups.values()) {
            if (group.memberIds().contains(neuronId)) {
                return group;
            }
        }

        return null;
    }

    public void createGroup(Set<String> memberIds) {
        LinkedHashSet<String> validIds = new LinkedHashSet<>();

        for (String id : memberIds) {
            if (neurons.containsKey(id)) {
                validIds.add(id);
            }
        }

        if (validIds.size() < 2) {
            return;
        }

        removeMembersFromExistingGroups(validIds);

        groups.values().removeIf(
                group -> group.memberIds().size() < 2
        );

        addGroup(
                UUID.randomUUID().toString(),
                validIds
        );
    }

    public void ungroup(Set<String> selectedIds) {
        for (NeuronGroup group : groups.values()) {
            group.removeMembers(selectedIds);
        }

        groups.values().removeIf(
                group -> group.memberIds().size() < 2
        );
    }

    public void clearActivations() {
        for (Neuron neuron : neurons.values()) {
            neuron.setActivation(0);
        }
    }

    public boolean isEmpty() {
        return neurons.isEmpty()
                && connections.isEmpty()
                && groups.isEmpty();
    }

    public void clear() {
        neurons.clear();
        presentations.clear();
        connections.clear();
        groups.clear();
    }

    private void removeMembersFromExistingGroups(
            Set<String> selectedIds
    ) {
        for (NeuronGroup group : groups.values()) {
            group.removeMembers(selectedIds);
        }
    }
}
