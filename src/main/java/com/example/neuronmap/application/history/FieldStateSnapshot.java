package com.example.neuronmap.application.history;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronGroup;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of the editable field state.
 *
 * <p>Simulation activation is intentionally excluded because it is runtime
 * state rather than editable field data.</p>
 */
public final class FieldStateSnapshot {

    private final List<NeuronState> neurons;
    private final List<PresentationState> presentations;
    private final List<ConnectionState> connections;
    private final List<GroupState> groups;

    private FieldStateSnapshot(
            List<NeuronState> neurons,
            List<PresentationState> presentations,
            List<ConnectionState> connections,
            List<GroupState> groups
    ) {
        this.neurons = List.copyOf(neurons);
        this.presentations = List.copyOf(presentations);
        this.connections = List.copyOf(connections);
        this.groups = List.copyOf(groups);
    }

    public static FieldStateSnapshot capture(NeuronMapModel model) {
        requireModel(model);

        List<NeuronState> neuronStates = new ArrayList<>();
        for (Neuron neuron : model.neurons()) {
            neuronStates.add(
                    new NeuronState(
                            neuron.id(),
                            neuron.type(),
                            neuron.signalStrength(),
                            neuron.activationThreshold()
                    )
            );
        }

        List<PresentationState> presentationStates = new ArrayList<>();
        for (NeuronPresentation presentation : model.presentations()) {
            presentationStates.add(
                    new PresentationState(
                            presentation.neuron().id(),
                            presentation.x(),
                            presentation.y(),
                            presentation.rotationDegrees(),
                            presentation.directionReversed()
                    )
            );
        }

        List<ConnectionState> connectionStates = new ArrayList<>();
        for (Connection connection : model.connections()) {
            connectionStates.add(
                    new ConnectionState(
                            connection.id(),
                            connection.sourceId(),
                            connection.targetId()
                    )
            );
        }

        List<GroupState> groupStates = new ArrayList<>();
        for (NeuronGroup group : model.groups()) {
            groupStates.add(
                    new GroupState(
                            group.id(),
                            List.copyOf(group.memberIds())
                    )
            );
        }

        return new FieldStateSnapshot(
                neuronStates,
                presentationStates,
                connectionStates,
                groupStates
        );
    }

    /**
     * Restores the captured editable field state into the supplied model.
     * Runtime activation is reset to zero because it is not part of history.
     */
    public void restoreInto(NeuronMapModel model) {
        requireModel(model);

        model.clear();

        for (NeuronState neuronState : neurons) {
            Neuron neuron = model.addNeuron(
                    neuronState.id(),
                    neuronState.type(),
                    0
            );
            neuron.setSignalStrength(neuronState.signalStrength());
            neuron.setActivationThreshold(
                    neuronState.activationThreshold()
            );
        }

        for (PresentationState presentationState : presentations) {
            NeuronPresentation presentation =
                    model.presentation(presentationState.neuronId());

            if (presentation == null) {
                throw new IllegalStateException(
                        "Missing neuron presentation for "
                                + presentationState.neuronId()
                );
            }

            presentation.setPosition(
                    presentationState.x(),
                    presentationState.y()
            );
            presentation.setRotationDegrees(
                    presentationState.rotationDegrees()
            );
            presentation.setDirectionReversed(
                    presentationState.directionReversed()
            );
        }

        for (ConnectionState connectionState : connections) {
            model.addConnection(
                    connectionState.id(),
                    connectionState.sourceId(),
                    connectionState.targetId()
            );
        }

        for (GroupState groupState : groups) {
            model.addGroup(
                    groupState.id(),
                    groupState.memberIds()
            );
        }
    }

    public List<NeuronState> neurons() {
        return neurons;
    }

    public List<PresentationState> presentations() {
        return presentations;
    }

    public List<ConnectionState> connections() {
        return connections;
    }

    public List<GroupState> groups() {
        return groups;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof FieldStateSnapshot that)) {
            return false;
        }

        return neurons.equals(that.neurons)
                && presentations.equals(that.presentations)
                && connections.equals(that.connections)
                && groups.equals(that.groups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                neurons,
                presentations,
                connections,
                groups
        );
    }

    private static void requireModel(NeuronMapModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model must not be null.");
        }
    }

    public record NeuronState(
            String id,
            NeuronType type,
            int signalStrength,
            int activationThreshold
    ) {
    }

    public record PresentationState(
            String neuronId,
            double x,
            double y,
            double rotationDegrees,
            boolean directionReversed
    ) {
    }

    public record ConnectionState(
            String id,
            String sourceId,
            String targetId
    ) {
    }

    public record GroupState(
            String id,
            List<String> memberIds
    ) {
        public GroupState {
            memberIds = List.copyOf(memberIds);
        }
    }
}
