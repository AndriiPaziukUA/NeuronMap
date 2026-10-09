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
 * Представляє незмінний знімок значень полів, потрібний для порівняння та історії змін.
 */
public final class FieldStateSnapshot {

    private final List<NeuronState> neurons;
    private final List<PresentationState> presentations;
    private final List<ConnectionState> connections;
    private final List<GroupState> groups;

    /**
     * Повертає результат операції «поле стан знімок».
     *
     * @param neurons значення, що визначає нейрони для цієї операції.
     *
     * @param presentations значення, що визначає відповідну операцію для цієї операції.
     *
     * @param connections значення, що визначає звʼязки для цієї операції.
     *
     * @param groups значення, що визначає групи для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Повертає результат операції «відповідну операцію».
     *
     * @param model модель карти нейронів.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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

        /**
         * Повертає результат операції «поле стан знімок».
         *
         * @param neuronStates значення, що визначає нейрон стани для цієї операції.
         *
         * @param presentationStates значення, що визначає представлення стани для цієї операції.
         *
         * @param connectionStates значення, що визначає звʼязок стани для цієї операції.
         *
         * @param groupStates значення, що визначає група стани для цієї операції.
         *
         * @return значення або обʼєкт, визначений описаною операцією.
         */
        return new FieldStateSnapshot(
                neuronStates,
                presentationStates,
                connectionStates,
                groupStates
        );
    }

/**
 * Задає або оновлює значення, повʼязані з «відповідну операцію».
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
     * Повертає результат операції «нейрони».
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public List<NeuronState> neurons() {
        return neurons;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public List<PresentationState> presentations() {
        return presentations;
    }

    /**
     * Повертає результат операції «звʼязки».
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public List<ConnectionState> connections() {
        return connections;
    }

    /**
     * Повертає результат операції «групи».
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public List<GroupState> groups() {
        return groups;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param other значення, що визначає відповідну операцію для цієї операції.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
     * Повертає результат операції «відповідну операцію».
     *
     * @return числове значення, визначене методом.
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
     * Виконує операцію «потребувати модель».
     *
     * @param model модель карти нейронів.
     */
    private static void requireModel(NeuronMapModel model) {
        if (model == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("Model must not be null.");
        }
    }

    /**
     * Компонент NeuronState у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
     */
    /**
     * Повертає результат операції «нейрон стан».
     *
     * @param id ідентифікатор обʼєкта.
     *
     * @param type тип обʼєкта.
     *
     * @param signalStrength сила сигналу нейрона.
     *
     * @param activationThreshold поріг активації нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public record NeuronState(
            String id,
            NeuronType type,
            int signalStrength,
            int activationThreshold
    ) {
    }

    /**
     * Компонент PresentationState у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
     */
    /**
     * Повертає результат операції «представлення стан».
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @param x координата по горизонталі.
     *
     * @param y координата по вертикалі.
     *
     * @param rotationDegrees значення, що визначає обертання градуси для цієї операції.
     *
     * @param directionReversed значення, що визначає напрямок для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Повертає результат операції «звʼязок стан».
     *
     * @param id ідентифікатор обʼєкта.
     *
     * @param sourceId значення, що визначає джерело ідентифікатор для цієї операції.
     *
     * @param targetId значення, що визначає кінцевий ідентифікатор для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    /**
     * Компонент ConnectionState у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
     */
    public record ConnectionState(
            String id,
            String sourceId,
            String targetId
    ) {
    }

    /**
     * Повертає результат операції «група стан».
     *
     * @param id ідентифікатор обʼєкта.
     *
     * @param memberIds значення, що визначає ідентифікатори для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    /**
     * Компонент GroupState у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
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
