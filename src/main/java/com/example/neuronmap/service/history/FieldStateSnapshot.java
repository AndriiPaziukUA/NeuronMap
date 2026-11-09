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
import com.example.neuronmap.model.SignalType;
import com.example.neuronmap.model.StructureNeuronCreationMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Зберігає незмінний знімок усіх даних карти, необхідних для скасування та повторення змін.
 * Персональна сила кожного нейрона фіксується через його ефективні {@link NeuronParameters}.
 */
public final class FieldStateSnapshot {

    private final List<NeuronState> neurons;
    private final List<PresentationState> presentations;
    private final List<ConnectionState> connections;
    private final List<GroupState> groups;
    private final List<StructureState> structures;

    /**
     * Створює знімок із незмінних списків станів елементів карти.
     *
     * @param neurons стани нейронів, включно з їхніми персональними адаптивними силами.
     * @param presentations стани візуальних подань нейронів.
     * @param connections стани зв'язків між нейронами.
     * @param groups стани груп нейронів.
     * @param structures стани структур, їхніх учасників і спільних режимів.
     */
    private FieldStateSnapshot(List<NeuronState> neurons, List<PresentationState> presentations,
                               List<ConnectionState> connections, List<GroupState> groups,
                               List<StructureState> structures) {
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
            neuronStates.add(new NeuronState(neuron.id(), neuron.type(), neuron.parameters(), neuron.name()));
        }
        List<PresentationState> presentationStates = new ArrayList<>();
        for (NeuronPresentation presentation : model.presentations()) {
            presentationStates.add(new PresentationState(presentation.neuron().id(), presentation.position(),
                    presentation.rotationDegrees(), presentation.directionReversed()));
        }
        List<ConnectionState> connectionStates = new ArrayList<>();
        for (Connection connection : model.connections()) {
            connectionStates.add(new ConnectionState(connection.id(), connection.sourceId(), connection.targetId()));
        }
        List<GroupState> groupStates = new ArrayList<>();
        for (NeuronGroup group : model.groups()) {
            groupStates.add(new GroupState(group.id(), List.copyOf(group.memberIds())));
        }
        List<StructureState> structureStates = new ArrayList<>();
        for (NeuronStructure structure : model.structures()) {
            structureStates.add(new StructureState(
                    structure.id(), List.copyOf(structure.memberIds()), structure.isExpanded(),
                    structure.settings().memberType(), structure.settings().directionReversed(),
                    structure.settings().strongestNeuronOnly(), structure.settings().neuronCreationMode()));
        }
        return new FieldStateSnapshot(neuronStates, presentationStates, connectionStates, groupStates, structureStates);
    }

    /**
     * Відновлює знімок у переданій моделі карти, зберігаючи індивідуальні сили адаптивних учасників.
     * Числові параметри спочатку завантажуються незалежно, потім відновлюється членство структур,
     * а наприкінці повторно встановлюються персональні сили вже під'єднаних нейронів.
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
        restoreAdaptiveSignalStrengths(model);
        for (PresentationState presentationState : presentations) {
            NeuronPresentation presentation = model.presentation(presentationState.neuronId());
            if (presentation == null) {
                throw new IllegalStateException("Missing neuron presentation for " + presentationState.neuronId());
            }
            presentation.setPosition(presentationState.position());
            presentation.setRotationDegrees(presentationState.rotationDegrees());
            presentation.setDirectionReversed(presentationState.directionReversed());
        }
        for (ConnectionState connectionState : connections) {
            model.addConnection(connectionState.id(), connectionState.sourceId(), connectionState.targetId());
        }
        for (GroupState groupState : groups) {
            model.addGroup(groupState.id(), groupState.memberIds());
        }
    }

    /**
     * Відновлює персональну силу кожного звичайного нейрона після встановлення спільних налаштувань структур.
     * Значення структури-контейнера не копіюється до учасників: у статичному режимі вони й надалі читають
     * спільне значення, а в адаптивному отримують силу зі свого власного запису знімка.
     *
     * @param model модель із уже відновленими деревами структур.
     */
    private void restoreAdaptiveSignalStrengths(NeuronMapModel model) {
        for (NeuronState state : neurons) {
            if (state.type() == NeuronType.STRUCTURE || state.parameters().signalType() == SignalType.STATIC) {
                continue;
            }
            Neuron neuron = model.neuron(state.id());
            if (neuron != null) {
                neuron.setSignalStrength(state.parameters().signalStrength());
            }
        }
    }

    /**
     * Відновлює контейнери структур, їхні спільні налаштування, стан шухлядок і впорядковане членство.
     *
     * @param model модель карти, до якої потрібно повернути структури.
     * @throws IllegalStateException якщо контейнер або його учасника неможливо відновити.
     */
    private void restoreStructures(NeuronMapModel model) {
        for (StructureState structureState : structures) {
            if (!model.addStructure(structureState.id())) {
                throw new IllegalStateException("Missing neuron for structure " + structureState.id());
            }
            NeuronStructure structure = model.structure(structureState.id());
            structure.setExpanded(structureState.expanded());
            if (structureState.memberType() != null) {
                structure.settings().setMemberType(structureState.memberType());
            }
            structure.settings().setDirectionReversed(structureState.directionReversed());
            structure.settings().setStrongestNeuronOnly(structureState.strongestNeuronOnly());
            structure.settings().setNeuronCreationMode(structureState.neuronCreationMode());
        }
        for (StructureState structureState : structures) {
            for (String memberId : structureState.memberIds()) {
                if (!model.addStructureMember(structureState.id(), memberId)) {
                    throw new IllegalStateException(
                            "Unable to restore member " + memberId + " in structure " + structureState.id());
                }
            }
        }
    }

    /** Повертає незмінні стани нейронів, що були захоплені у знімок. @return список станів нейронів. */
    public List<NeuronState> neurons() { return neurons; }

    /** Повертає незмінні стани візуальних подань. @return список станів подань. */
    public List<PresentationState> presentations() { return presentations; }

    /** Повертає незмінні стани напрямлених зв'язків. @return список станів зв'язків. */
    public List<ConnectionState> connections() { return connections; }

    /** Повертає незмінні стани груп і їхніх учасників. @return список станів груп. */
    public List<GroupState> groups() { return groups; }

    /** Повертає незмінні стани структур та їхніх спільних режимів. @return список станів структур. */
    public List<StructureState> structures() { return structures; }

    /**
     * Порівнює знімки за всіма даними карти, які зберігаються в історії.
     *
     * @param other об'єкт для порівняння з поточним знімком.
     * @return {@code true}, якщо знімки містять однакові дані; інакше {@code false}.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FieldStateSnapshot that)) return false;
        return neurons.equals(that.neurons) && presentations.equals(that.presentations)
                && connections.equals(that.connections) && groups.equals(that.groups) && structures.equals(that.structures);
    }

    /** Обчислює хеш-код на основі всіх даних знімка. @return хеш-код поточного знімка. */
    @Override
    public int hashCode() { return Objects.hash(neurons, presentations, connections, groups, structures); }

    /**
     * Перевіряє наявність моделі перед виконанням операції над знімком.
     *
     * @param model модель карти для перевірки.
     * @throws IllegalArgumentException якщо модель не задано.
     */
    private static void requireModel(NeuronMapModel model) {
        if (model == null) throw new IllegalArgumentException("Model must not be null.");
    }

    /**
     * Зберігає параметри й назву нейрона, необхідні для відновлення його збереженого стану.
     *
     * @param id ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param parameters параметри сигналу, включно з персональною силою, якщо режим адаптивний.
     * @param name назва нейрона або {@code null}.
     */
    public record NeuronState(String id, NeuronType type, NeuronParameters parameters, String name) {
        /** Перевіряє, що знімок містить тип і параметри нейрона. */
        public NeuronState {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(parameters, "parameters");
        }

        /** Створює знімок зі старого контракту без заданої назви. */
        public NeuronState(String id, NeuronType type, NeuronParameters parameters) {
            this(id, type, parameters, null);
        }

        /** Створює знімок із попереднього контракту з окремими числовими параметрами. */
        public NeuronState(String id, NeuronType type, int signalStrength, int activationThreshold) {
            this(id, type, new NeuronParameters(signalStrength, activationThreshold), null);
        }

        /** Повертає базову або персональну силу сигналу знімка. @return сила сигналу. */
        public int signalStrength() { return parameters.signalStrength(); }

        /** Повертає поріг активації знімка. @return поріг активації. */
        public int activationThreshold() { return parameters.activationThreshold(); }
    }

    /**
     * Зберігає геометрію та напрямок візуального подання нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param position позиція нейрона на карті.
     * @param rotationDegrees кут обертання у градусах.
     * @param directionReversed ознака зворотного напрямку портів.
     */
    public record PresentationState(String neuronId, MapPosition position, double rotationDegrees,
                                    boolean directionReversed) {
        /** Перевіряє, що знімок містить позицію нейрона. */
        public PresentationState { Objects.requireNonNull(position, "position"); }

        /** Створює знімок зі старого контракту, у якому координати передавалися окремо. */
        public PresentationState(String neuronId, double x, double y, double rotationDegrees,
                                 boolean directionReversed) {
            this(neuronId, new MapPosition(x, y), rotationDegrees, directionReversed);
        }

        /** Повертає горизонтальну координату. @return координата X. */
        public double x() { return position.x(); }

        /** Повертає вертикальну координату. @return координата Y. */
        public double y() { return position.y(); }
    }

    /** Зберігає ідентифікатор зв'язку та його початковий і кінцевий нейрони. */
    public record ConnectionState(String id, String sourceId, String targetId) { }

    /**
     * Зберігає ідентифікатор групи та незмінний перелік її учасників.
     *
     * @param id ідентифікатор групи.
     * @param memberIds ідентифікатори нейронів у групі.
     */
    public record GroupState(String id, List<String> memberIds) {
        /** Створює стан групи з незмінним переліком учасників. */
        public GroupState { memberIds = List.copyOf(memberIds); }
    }

    /**
     * Зберігає членство, стан шухлядки, тип, напрямок та режими поведінки структури для undo/redo.
     *
     * @param id ідентифікатор нейрона-контейнера.
     * @param memberIds ідентифікатори учасників у порядку їх додавання.
     * @param expanded {@code true}, якщо шухлядка відкрита у збереженому стані.
     * @param memberType спільний тип звичайних учасників.
     * @param directionReversed {@code true}, якщо спільний напрямок портів розвернуто.
     * @param strongestNeuronOnly {@code true}, якщо режим конкуренції ввімкнено.
     * @param neuronCreationMode умова створення нового спільного нейрона.
     */
    public record StructureState(String id, List<String> memberIds, boolean expanded,
                                 NeuronType memberType, boolean directionReversed, boolean strongestNeuronOnly,
                                 StructureNeuronCreationMode neuronCreationMode) {
        /** Перевіряє перелік членів та режим створення нейрона. */
        public StructureState {
            memberIds = List.copyOf(memberIds);
            Objects.requireNonNull(neuronCreationMode, "neuronCreationMode");
        }

        /** Зберігає сумісність зі знімками, у яких не фіксувався режим найсильнішого нейрона. */
        public StructureState(String id, List<String> memberIds, boolean expanded,
                              NeuronType memberType, boolean directionReversed) {
            this(id, memberIds, expanded, memberType, directionReversed, false,
                    StructureNeuronCreationMode.NEURON_COUNT);
        }

        /** Зберігає сумісність зі знімками, у яких не фіксувався режим створення нейрона. */
        @SuppressWarnings("unused")
        public StructureState(String id, List<String> memberIds, boolean expanded,
                              NeuronType memberType, boolean directionReversed, boolean strongestNeuronOnly) {
            this(id, memberIds, expanded, memberType, directionReversed, strongestNeuronOnly,
                    StructureNeuronCreationMode.NEURON_COUNT);
        }

        /** Зберігає сумісність зі старим контрактом без спільних характеристик структури. */
        @SuppressWarnings("unused")
        public StructureState(String id, List<String> memberIds, boolean expanded) {
            this(id, memberIds, expanded, null, false, false, StructureNeuronCreationMode.NEURON_COUNT);
        }

        /** Зберігає сумісність зі старим контрактом, де структура за замовчуванням закрита. */
        public StructureState(String id, List<String> memberIds) {
            this(id, memberIds, false, null, false, false, StructureNeuronCreationMode.NEURON_COUNT);
        }
    }
}
