package com.example.neuronmap.service.history;

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
 * Зберігає незмінний знімок даних карти, необхідних для скасування й повторення змін.
 */
public final class FieldStateSnapshot {

    private final List<NeuronState> neurons;
    private final List<PresentationState> presentations;
    private final List<ConnectionState> connections;
    private final List<GroupState> groups;

    /**
     * Створює знімок із незмінних списків станів елементів карти.
     *
     * @param neurons знімки станів нейронів.
     * @param presentations знімки візуальних подань нейронів.
     * @param connections знімки зв’язків між нейронами.
     * @param groups знімки груп нейронів.
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
     * Створює знімок усіх збережених даних моделі карти.
     *
     * @param model модель карти, яку потрібно зафіксувати.
     * @return незмінний знімок стану карти.
     * @throws IllegalArgumentException якщо модель не задано.
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
     * Відновлює знімок у переданій моделі карти.
     *
     * @param model модель, стан якої потрібно відновити.
     * @throws IllegalArgumentException якщо модель не задано.
     * @throws IllegalStateException якщо в знімку відсутнє подання потрібного нейрона.
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
     * Повертає незмінний список станів нейронів зі знімка.
     *
     * @return список станів нейронів.
     */
    public List<NeuronState> neurons() {
        return neurons;
    }

    /**
     * Повертає незмінний список станів візуальних подань нейронів.
     *
     * @return список станів візуальних подань.
     */
    public List<PresentationState> presentations() {
        return presentations;
    }

    /**
     * Повертає незмінний список станів зв’язків зі знімка.
     *
     * @return список станів зв’язків.
     */
    public List<ConnectionState> connections() {
        return connections;
    }

    /**
     * Повертає незмінний список станів груп зі знімка.
     *
     * @return список станів груп.
     */
    public List<GroupState> groups() {
        return groups;
    }

    /**
     * Порівнює знімки за всіма даними карти, що зберігаються в історії.
     *
     * @param other об’єкт для порівняння з поточним знімком.
     * @return {@code true}, якщо знімки містять однакові дані; інакше {@code false}.
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
     * Обчислює хеш-код на основі всіх даних знімка.
     *
     * @return хеш-код поточного знімка.
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
     * Перевіряє, що посилання на модель карти не дорівнює {@code null}.
     *
     * @param model модель для перевірки.
     * @throws IllegalArgumentException якщо модель не задано.
     */
    private static void requireModel(NeuronMapModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model must not be null.");
        }
    }

    /**
     * Зберігає знімок основних параметрів нейрона.
     *
     * @param id ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param signalStrength сила сигналу.
     * @param activationThreshold поріг активації.
     */
    public record NeuronState(
            String id,
            NeuronType type,
            int signalStrength,
            int activationThreshold
    ) {
    }

    /**
     * Зберігає стан візуального подання нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param x горизонтальна координата.
     * @param y вертикальна координата.
     * @param rotationDegrees кут обертання у градусах.
     * @param directionReversed ознака зворотного напрямку.
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
     * Зберігає ідентифікатори зв’язку та його початкового й кінцевого нейронів.
     *
     * @param id ідентифікатор зв’язку.
     * @param sourceId ідентифікатор початкового нейрона.
     * @param targetId ідентифікатор кінцевого нейрона.
     */
    public record ConnectionState(
            String id,
            String sourceId,
            String targetId
    ) {
    }

    /**
     * Зберігає ідентифікатор групи та перелік її учасників.
     *
     * @param id ідентифікатор групи.
     * @param memberIds ідентифікатори нейронів у групі.
     */
    public record GroupState(
            String id,
            List<String> memberIds
    ) {
        /**
         * Створює стан групи з незмінним переліком учасників.
         *
         * @param id ідентифікатор групи.
         * @param memberIds ідентифікатори нейронів у групі.
         */
        public GroupState {
            memberIds = List.copyOf(memberIds);
        }
    }
}
