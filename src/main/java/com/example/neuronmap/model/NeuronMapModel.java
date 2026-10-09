package com.example.neuronmap.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Є основною моделлю карти: керує нейронами, їхнім поданням, напрямленими зв’язками та групами.
 */
public final class NeuronMapModel {

    private final Map<String, Neuron> neurons = new LinkedHashMap<>();
    private final Map<String, NeuronPresentation> presentations = new LinkedHashMap<>();
    private final Map<String, Connection> connections = new LinkedHashMap<>();
    private final Map<String, NeuronGroup> groups = new LinkedHashMap<>();

    /**
     * Створює нейрон зазначеного типу в заданих координатах і створює для нього візуальне подання.
     *
     * @param type тип нейрона або елемента.
     * @param x координата X.
     * @param y координата Y.
     */
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

    /**
     * Створює нейрон зазначеного типу в заданих координатах і створює для нього візуальне подання.
     *
     * @param type тип нейрона або елемента.
     * @param x координата X.
     * @param y координата Y.
     * @param rotationDegrees кут обертання в градусах.
     */
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

    /**
     * Додає нейрон із заданим ідентифікатором до моделі та створює його подання.
     *
     * @param id унікальний ідентифікатор елемента.
     * @param type тип нейрона або елемента.
     * @param activation значення «activation», яке використовується в цьому методі.
     */
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

    /**
     * Додає нейрон із заданим ідентифікатором до моделі та створює його подання.
     *
     * @param id унікальний ідентифікатор елемента.
     * @param type тип нейрона або елемента.
     * @param activation значення «activation», яке використовується в цьому методі.
     * @param x координата X.
     * @param y координата Y.
     * @param rotationDegrees кут обертання в градусах.
     */
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

    /**
     * Додає імпортований напрямлений зв’язок після перевірки допустимості його кінців.
     *
     * @param id унікальний ідентифікатор елемента.
     * @param sourceId ідентифікатор початкового нейрона зв’язку.
     * @param targetId ідентифікатор кінцевого нейрона зв’язку.
     */
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

    /**
     * Створює напрямлений зв’язок, якщо пара нейронів допустима й такого зв’язку ще немає.
     *
     * @param sourceId ідентифікатор початкового нейрона зв’язку.
     * @param targetId ідентифікатор кінцевого нейрона зв’язку.
     */
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

    /**
     * Перевіряє наявність напрямленого зв’язку від початкового нейрона до цільового.
     *
     * @param sourceId ідентифікатор початкового нейрона зв’язку.
     * @param targetId ідентифікатор кінцевого нейрона зв’язку.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
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

    /**
     * Видаляє зв’язок за ідентифікатором і повертає видалений елемент.
     *
     * @param id унікальний ідентифікатор елемента.
     */
    public Connection removeConnection(String id) {
        return connections.remove(id);
    }

/**
 * Видаляє зв’язки між указаною парою нейронів і повертає кількість видалених зв’язків.
 *
 * @param firstNeuronId ідентифікатор елемента, над яким виконується дія.
 * @param secondNeuronId ідентифікатор елемента, над яким виконується дія.
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

    /**
     * Видаляє нейрон разом із його поданням, пов’язаними зв’язками та членством у групах.
     *
     * @param neuronId ідентифікатор нейрона.
     */
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

    /**
     * Додає групу з указаним ідентифікатором і набором учасників.
     *
     * @param id унікальний ідентифікатор елемента.
     * @param memberIds ідентифікатори нейронів, які мають увійти до групи.
     */
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

    /**
     * Знаходить нейрон за ідентифікатором або повертає null, якщо нейрона немає в моделі.
     *
     * @param id унікальний ідентифікатор елемента.
     */
    public Neuron neuron(String id) {
        return neurons.get(id);
    }

    /**
     * Знаходить візуальне подання нейрона за його ідентифікатором.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public NeuronPresentation presentation(String neuronId) {
        return presentations.get(neuronId);
    }

    /**
     * Повертає нейрони, що зараз містяться в моделі.
     *
     * @return нейрони, що зараз містяться в моделі.
     */
    public Collection<Neuron> neurons() {
        return Collections.unmodifiableCollection(
                neurons.values()
        );
    }

    /**
     * Повертає візуальні подання нейронів, які зберігає модель.
     *
     * @return візуальні подання нейронів, які зберігає модель.
     */
    public Collection<NeuronPresentation> presentations() {
        return Collections.unmodifiableCollection(
                presentations.values()
        );
    }

    /**
     * Повертає всі напрямлені зв’язки моделі.
     *
     * @return всі напрямлені зв’язки моделі.
     */
    public Collection<Connection> connections() {
        return Collections.unmodifiableCollection(
                connections.values()
        );
    }

    /**
     * Повертає всі групи нейронів моделі.
     *
     * @return всі групи нейронів моделі.
     */
    public Collection<NeuronGroup> groups() {
        return Collections.unmodifiableCollection(
                groups.values()
        );
    }

    /**
     * Знаходить групу, до якої належить вказаний нейрон.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public NeuronGroup groupContaining(String neuronId) {
        for (NeuronGroup group : groups.values()) {
            if (group.memberIds().contains(neuronId)) {
                return group;
            }
        }

        return null;
    }

    /**
     * Створює групу з указаних нейронів, якщо набір учасників допустимий.
     *
     * @param memberIds ідентифікатори нейронів, які мають увійти до групи.
     */
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

    /**
     * Прибирає вибраних нейронів із групування відповідно до поточного складу груп.
     *
     * @param selectedIds ідентифікатори вибраних нейронів.
     */
    public void ungroup(Set<String> selectedIds) {
        for (NeuronGroup group : groups.values()) {
            group.removeMembers(selectedIds);
        }

        groups.values().removeIf(
                group -> group.memberIds().size() < 2
        );
    }

    /**
     * Скидає поточні значення активації нейронів.
     */
    public void clearActivations() {
        for (Neuron neuron : neurons.values()) {
            neuron.setActivation(0);
        }
    }

    /**
     * Перевіряє, чи не містить модель нейронів або інших елементів карти.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean isEmpty() {
        return neurons.isEmpty()
                && connections.isEmpty()
                && groups.isEmpty();
    }

    /**
     * Очищає нейрони, подання, зв’язки й групи з моделі.
     */
    public void clear() {
        neurons.clear();
        presentations.clear();
        connections.clear();
        groups.clear();
    }

    /**
     * Вилучає вибрані нейрони з груп, зберігаючи решту учасників.
     *
     * @param selectedIds ідентифікатори вибраних нейронів.
     */
    private void removeMembersFromExistingGroups(
            Set<String> selectedIds
    ) {
        for (NeuronGroup group : groups.values()) {
            group.removeMembers(selectedIds);
        }
    }
}
