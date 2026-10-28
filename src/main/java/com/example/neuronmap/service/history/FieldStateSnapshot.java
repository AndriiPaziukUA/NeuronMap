package com.example.neuronmap.service.history;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.MapPosition;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronGroup;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronStructure;
import com.example.neuronmap.model.NeuronType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Зберігає незмінний знімок даних карти, потрібних для скасування й повторення змін. */
public final class FieldStateSnapshot {

    private final List<NeuronState> neurons;
    private final List<PresentationState> presentations;
    private final List<ConnectionState> connections;
    private final List<GroupState> groups;
    private final List<StructureState> structures;

    /**
     * Створює знімок із незмінних списків станів елементів карти.
     *
     * @param neurons знімки станів нейронів.
     * @param presentations знімки візуальних подань нейронів.
     * @param connections знімки зв'язків між нейронами.
     * @param groups знімки груп нейронів.
     * @param structures знімки структур, їхніх учасників і стану шухлядок.
     */
    private FieldStateSnapshot(
            List<NeuronState> neurons,
            List<PresentationState> presentations,
            List<ConnectionState> connections,
            List<GroupState> groups,
            List<StructureState> structures
    ) {
        this.neurons = List.copyOf(neurons);
        this.presentations = List.copyOf(presentations);
        this.connections = List.copyOf(connections);
        this.groups = List.copyOf(groups);
        this.structures = List.copyOf(structures);
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
            neuronStates.add(new NeuronState(
                    neuron.id(), neuron.type(), neuron.parameters(), neuron.name()
            ));
        }

        List<PresentationState> presentationStates = new ArrayList<>();
        for (NeuronPresentation presentation : model.presentations()) {
            presentationStates.add(new PresentationState(
                    presentation.neuron().id(), presentation.position(),
                    presentation.rotationDegrees(), presentation.directionReversed()
            ));
        }

        List<ConnectionState> connectionStates = new ArrayList<>();
        for (Connection connection : model.connections()) {
            connectionStates.add(new ConnectionState(
                    connection.id(), connection.sourceId(), connection.targetId()
            ));
        }

        List<GroupState> groupStates = new ArrayList<>();
        for (NeuronGroup group : model.groups()) {
            groupStates.add(new GroupState(group.id(), List.copyOf(group.memberIds())));
        }

        List<StructureState> structureStates = new ArrayList<>();
        for (NeuronStructure structure : model.structures()) {
            structureStates.add(new StructureState(
                    structure.id(), List.copyOf(structure.memberIds()), structure.isExpanded()
            ));
        }

        return new FieldStateSnapshot(
                neuronStates, presentationStates, connectionStates, groupStates, structureStates
        );
    }

    /**
     * Відновлює знімок у переданій моделі карти.
     *
     * @param model модель, стан якої потрібно відновити.
     * @throws IllegalArgumentException якщо модель не задано.
     * @throws IllegalStateException якщо знімок містить відсутнє подання або неможливе членство структури.
     */
    public void restoreInto(NeuronMapModel model) {
        requireModel(model);
        model.clear();

        for (NeuronState neuronState : neurons) {
            Neuron neuron = model.addNeuron(neuronState.id(), neuronState.type(), 0);
            neuron.setParameters(neuronState.parameters());
            neuron.setName(neuronState.name());
        }

        restoreStructures(model);

        for (PresentationState presentationState : presentations) {
            NeuronPresentation presentation = model.presentation(presentationState.neuronId());
            if (presentation == null) {
                throw new IllegalStateException(
                        "Missing neuron presentation for " + presentationState.neuronId()
                );
            }
            presentation.setPosition(presentationState.position());
            presentation.setRotationDegrees(presentationState.rotationDegrees());
            presentation.setDirectionReversed(presentationState.directionReversed());
        }

        for (ConnectionState connectionState : connections) {
            model.addConnection(
                    connectionState.id(), connectionState.sourceId(), connectionState.targetId()
            );
        }

        for (GroupState groupState : groups) {
            model.addGroup(groupState.id(), groupState.memberIds());
        }
    }

    /**
     * Відновлює контейнери структур, їхній стан відкритості та впорядковане членство.
     *
     * @param model модель карти, до якої потрібно повернути структури.
     * @throws IllegalStateException якщо контейнер або його учасника неможливо відновити.
     */
    private void restoreStructures(NeuronMapModel model) {
        for (StructureState structureState : structures) {
            if (!model.addStructure(structureState.id())) {
                throw new IllegalStateException("Missing neuron for structure " + structureState.id());
            }
            model.structure(structureState.id()).setExpanded(structureState.expanded());
        }

        for (StructureState structureState : structures) {
            for (String memberId : structureState.memberIds()) {
                if (!model.addStructureMember(structureState.id(), memberId)) {
                    throw new IllegalStateException(
                            "Unable to restore member " + memberId + " in structure " + structureState.id()
                    );
                }
            }
        }
    }

    /** Повертає незмінний список станів нейронів зі знімка.
     * @return список станів нейронів.
     */
    public List<NeuronState> neurons() {
        return neurons;
    }

    /** Повертає незмінний список станів подань нейронів зі знімка.
     * @return список станів подань нейронів.
     */
    public List<PresentationState> presentations() {
        return presentations;
    }

    /** Повертає незмінний список станів зв'язків зі знімка.
     * @return список станів зв'язків.
     */
    public List<ConnectionState> connections() {
        return connections;
    }

    /** Повертає незмінний список станів груп зі знімка.
     * @return список станів груп.
     */
    public List<GroupState> groups() {
        return groups;
    }

    /** Повертає незмінний список станів структур зі знімка.
     * @return структури з учасниками та станом відкритості.
     */
    public List<StructureState> structures() {
        return structures;
    }

    /**
     * Порівнює знімки за всіма даними карти, які зберігаються в історії.
     *
     * @param other об'єкт для порівняння з поточним знімком.
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
                && groups.equals(that.groups)
                && structures.equals(that.structures);
    }

    /**
     * Обчислює хеш-код на основі всіх даних знімка.
     *
     * @return хеш-код поточного знімка.
     */
    @Override
    public int hashCode() {
        return Objects.hash(neurons, presentations, connections, groups, structures);
    }

    /**
     * Перевіряє, що модель карти задано.
     *
     * @param model модель карти для перевірки.
     * @throws IllegalArgumentException якщо модель не задано.
     */
    private static void requireModel(NeuronMapModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model must not be null.");
        }
    }

    /**
     * Зберігає параметри й назву нейрона, потрібні для відновлення збереженого стану.
     *
     * @param id ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param parameters параметри сигналу.
     * @param name назва нейрона або {@code null}.
     */
    public record NeuronState(String id, NeuronType type, NeuronParameters parameters, String name) {
        /** Перевіряє, що знімок містить параметри нейрона.
         * @throws NullPointerException якщо тип або параметри не задано.
         */
        public NeuronState {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(parameters, "parameters");
        }

        /** Створює знімок зі старого контракту без заданої назви.
         * @param id ідентифікатор нейрона.
         * @param type тип нейрона.
         * @param parameters параметри сигналу.
         */
        public NeuronState(String id, NeuronType type, NeuronParameters parameters) {
            this(id, type, parameters, null);
        }

        /** Створює знімок із попереднього контракту з окремими значеннями налаштувань.
         * @param id ідентифікатор нейрона.
         * @param type тип нейрона.
         * @param signalStrength сила сигналу.
         * @param activationThreshold поріг активації.
         */
        public NeuronState(String id, NeuronType type, int signalStrength, int activationThreshold) {
            this(id, type, new NeuronParameters(signalStrength, activationThreshold), null);
        }

        /** Повертає силу сигналу нейрона.
         * @return сила сигналу.
         */
        public int signalStrength() {
            return parameters.signalStrength();
        }

        /** Повертає поріг активації нейрона.
         * @return поріг активації.
         */
        public int activationThreshold() {
            return parameters.activationThreshold();
        }
    }

    /**
     * Зберігає стан візуального подання нейрона разом із його позицією як єдиним значенням.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param position позиція нейрона на карті.
     * @param rotationDegrees кут обертання у градусах.
     * @param directionReversed ознака зворотного напрямку.
     */
    public record PresentationState(
            String neuronId,
            MapPosition position,
            double rotationDegrees,
            boolean directionReversed
    ) {
        /** Перевіряє, що знімок містить позицію нейрона.
         * @throws NullPointerException якщо позицію не задано.
         */
        public PresentationState {
            Objects.requireNonNull(position, "position");
        }

        /** Створює знімок зі старого контракту, у якому координати передавалися окремо.
         * @param neuronId ідентифікатор нейрона.
         * @param x горизонтальна координата.
         * @param y вертикальна координата.
         * @param rotationDegrees кут обертання у градусах.
         * @param directionReversed ознака зворотного напрямку.
         */
        public PresentationState(
                String neuronId, double x, double y, double rotationDegrees, boolean directionReversed
        ) {
            this(neuronId, new MapPosition(x, y), rotationDegrees, directionReversed);
        }

        /** Повертає горизонтальну координату для сумісності з наявними читачами знімка.
         * @return координата X.
         */
        public double x() {
            return position.x();
        }

        /** Повертає вертикальну координату для сумісності з наявними читачами знімка.
         * @return координата Y.
         */
        public double y() {
            return position.y();
        }
    }

    /** Зберігає ідентифікатори зв'язку та його початкового й кінцевого нейронів.
     * @param id ідентифікатор зв'язку.
     * @param sourceId ідентифікатор початкового нейрона.
     * @param targetId ідентифікатор кінцевого нейрона.
     */
    public record ConnectionState(String id, String sourceId, String targetId) {
    }

    /** Зберігає ідентифікатор групи та перелік її учасників.
     * @param id ідентифікатор групи.
     * @param memberIds ідентифікатори нейронів у групі.
     */
    public record GroupState(String id, List<String> memberIds) {
        /** Створює стан групи з незмінним переліком учасників.
         * @throws NullPointerException якщо список учасників не задано.
         */
        public GroupState {
            memberIds = List.copyOf(memberIds);
        }
    }

    /**
     * Зберігає ідентифікатор структури, впорядкованих учасників і стан її шухлядки.
     *
     * @param id ідентифікатор нейрона-контейнера.
     * @param memberIds ідентифікатори учасників у порядку їх додавання.
     * @param expanded {@code true}, якщо шухлядка відкрита у збереженому стані.
     */
    public record StructureState(String id, List<String> memberIds, boolean expanded) {
        /** Створює незмінний знімок членства й стану шухлядки структури.
         * @throws NullPointerException якщо список учасників не задано.
         */
        public StructureState {
            memberIds = List.copyOf(memberIds);
        }

        /** Зберігає сумісність зі старим контрактом: структура зі знімка без цього поля закрита.
         * @param id ідентифікатор нейрона-контейнера.
         * @param memberIds ідентифікатори учасників у порядку їх додавання.
         */
        public StructureState(String id, List<String> memberIds) {
            this(id, memberIds, false);
        }
    }
}
