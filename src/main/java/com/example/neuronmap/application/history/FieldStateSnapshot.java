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
 * Зберігає повний знімок даних карти, потрібних для історії: нейрони, їхнє подання, зв’язки та групи.
 */
public final class FieldStateSnapshot {

    private final List<NeuronState> neurons;
    private final List<PresentationState> presentations;
    private final List<ConnectionState> connections;
    private final List<GroupState> groups;

    /**
     * Створює знімок карти з копіями станів нейронів, їхніх подань, зв’язків і груп.
     *
     * @param neurons список знімків стану нейронів, які входять до карти.
     * @param presentations список знімків візуального подання нейронів.
     * @param connections набір напрямлених зв’язків моделі.
     * @param groups список знімків груп нейронів.
     */
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

    /**
     * Збирає з моделі нейрони та їхні налаштування, візуальні подання, зв’язки й групи.
     *
     * @param model модель карти нейронів.
     */
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
 * Відновлює знімок у модель, створюючи збережену структуру нейронів, подань, зв’язків і груп.
 *
 * @param model модель карти нейронів.
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

    /**
     * Повертає незмінний список станів нейронів, що входять до знімка.
     *
     * @return незмінний список станів нейронів, що входять до знімка.
     */
    public List<NeuronState> neurons() {
        return neurons;
    }

    /**
     * Повертає незмінний список візуальних подань нейронів у знімку.
     *
     * @return незмінний список візуальних подань нейронів у знімку.
     */
    public List<PresentationState> presentations() {
        return presentations;
    }

    /**
     * Повертає незмінний список зв’язків, збережених у знімку.
     *
     * @return незмінний список зв’язків, збережених у знімку.
     */
    public List<ConnectionState> connections() {
        return connections;
    }

    /**
     * Повертає незмінний список груп, збережених у знімку.
     *
     * @return незмінний список груп, збережених у знімку.
     */
    public List<GroupState> groups() {
        return groups;
    }

    /**
     * Порівнює знімки за збереженими нейронами, поданнями, зв’язками та групами.
     *
     * @param other інший об’єкт, з яким порівнюють поточний об’єкт.
     */
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

    /**
     * Перевіряє, чи є h code у поточному стані.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                neurons,
                presentations,
                connections,
                groups
        );
    }

    /**
     * Перевіряє, що передано ненульову модель карти.
     *
     * @param model модель карти нейронів.
     */
    private static void requireModel(NeuronMapModel model) {
        if (model == null) {

            throw new IllegalArgumentException("Model must not be null.");
        }
    }

    /**
     * Зберігає ідентифікатор, тип, силу сигналу й поріг активації нейрона на момент знімка.
     */
    public record NeuronState(
            String id,
            NeuronType type,
            int signalStrength,
            int activationThreshold
    ) {
    }

    /**
     * Зберігає координати, кут обертання й напрямок візуального подання нейрона.
     */
    public record PresentationState(
            String neuronId,
            double x,
            double y,
            double rotationDegrees,
            boolean directionReversed
    ) {
    }

    /**
     * Зберігає ідентифікатор зв’язку та ідентифікатори його початкового й кінцевого нейронів.
     */
    public record ConnectionState(
            String id,
            String sourceId,
            String targetId
    ) {
    }

    /**
     * Зберігає ідентифікатор групи й незмінний перелік її учасників.
     */
    public record GroupState(
            String id,
            List<String> memberIds
    ) {
        public GroupState {
            memberIds = List.copyOf(memberIds);
        }
    }
}
