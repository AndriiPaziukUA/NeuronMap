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
 * Є основною моделлю карти: зберігає нейрони, їхні представлення, звʼязки та групи й підтримує цілісність цих даних.
 */
public final class NeuronMapModel {

    private final Map<String, Neuron> neurons = new LinkedHashMap<>();
    private final Map<String, NeuronPresentation> presentations = new LinkedHashMap<>();
    private final Map<String, Connection> connections = new LinkedHashMap<>();
    private final Map<String, NeuronGroup> groups = new LinkedHashMap<>();

    /**
     * Створює обʼєкт із переданих даних «нейрон».
     *
     * @param type тип обʼєкта.
     *
     * @param x координата по горизонталі.
     *
     * @param y координата по вертикалі.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Створює обʼєкт із переданих даних «нейрон».
     *
     * @param type тип обʼєкта.
     *
     * @param x координата по горизонталі.
     *
     * @param y координата по вертикалі.
     *
     * @param rotationDegrees значення, що визначає обертання градуси для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Повертає результат операції «додати нейрон».
     *
     * @param id ідентифікатор обʼєкта.
     *
     * @param type тип обʼєкта.
     *
     * @param activation значення активації нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Повертає результат операції «додати нейрон».
     *
     * @param id ідентифікатор обʼєкта.
     *
     * @param type тип обʼєкта.
     *
     * @param activation значення активації нейрона.
     *
     * @param x координата по горизонталі.
     *
     * @param y координата по вертикалі.
     *
     * @param rotationDegrees значення, що визначає обертання градуси для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Виконує операцію «додати звʼязок».
     *
     * @param id ідентифікатор обʼєкта.
     *
     * @param sourceId значення, що визначає джерело ідентифікатор для цієї операції.
     *
     * @param targetId значення, що визначає кінцевий ідентифікатор для цієї операції.
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
     * Створює обʼєкт із переданих даних «звʼязок».
     *
     * @param sourceId значення, що визначає джерело ідентифікатор для цієї операції.
     *
     * @param targetId значення, що визначає кінцевий ідентифікатор для цієї операції.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
     * Перевіряє, чи виконується умова «звʼязок».
     *
     * @param sourceId значення, що визначає джерело ідентифікатор для цієї операції.
     *
     * @param targetId значення, що визначає кінцевий ідентифікатор для цієї операції.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
     * Видаляє або скидає дані, повʼязані з «звʼязок».
     *
     * @param id ідентифікатор обʼєкта.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Connection removeConnection(String id) {
        return connections.remove(id);
    }

/**
 * Видаляє або скидає дані, повʼязані з «звʼязки».
 *
 * @param firstNeuronId значення, що визначає перший нейрон ідентифікатор для цієї операції.
 *
 * @param secondNeuronId значення, що визначає нейрон ідентифікатор для цієї операції.
 *
 * @return числове значення, визначене методом.
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
     * Видаляє або скидає дані, повʼязані з «нейрон».
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Виконує операцію «додати група».
     *
     * @param id ідентифікатор обʼєкта.
     *
     * @param memberIds значення, що визначає ідентифікатори для цієї операції.
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
     * Повертає результат операції «нейрон».
     *
     * @param id ідентифікатор обʼєкта.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Neuron neuron(String id) {
        return neurons.get(id);
    }

    /**
     * Повертає результат операції «представлення».
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronPresentation presentation(String neuronId) {
        return presentations.get(neuronId);
    }

    /**
     * Повертає результат операції «нейрони».
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public Collection<Neuron> neurons() {
        return Collections.unmodifiableCollection(
                neurons.values()
        );
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public Collection<NeuronPresentation> presentations() {
        return Collections.unmodifiableCollection(
                presentations.values()
        );
    }

    /**
     * Повертає результат операції «звʼязки».
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public Collection<Connection> connections() {
        return Collections.unmodifiableCollection(
                connections.values()
        );
    }

    /**
     * Повертає результат операції «групи».
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public Collection<NeuronGroup> groups() {
        return Collections.unmodifiableCollection(
                groups.values()
        );
    }

    /**
     * Повертає результат операції «група».
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Створює обʼєкт із переданих даних «група».
     *
     * @param memberIds значення, що визначає ідентифікатори для цієї операції.
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
     * Виконує операцію «відповідну операцію».
     *
     * @param selectedIds ідентифікатори вибраних обʼєктів.
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
     * Видаляє або скидає дані, повʼязані з «відповідну операцію».
     */
    public void clearActivations() {
        for (Neuron neuron : neurons.values()) {
            neuron.setActivation(0);
        }
    }

    /**
     * Перевіряє, чи виконується умова «порожній».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isEmpty() {
        return neurons.isEmpty()
                && connections.isEmpty()
                && groups.isEmpty();
    }

    /**
     * Видаляє або скидає дані, повʼязані з «потрібні дані».
     */
    public void clear() {
        neurons.clear();
        presentations.clear();
        connections.clear();
        groups.clear();
    }

    /**
     * Видаляє або скидає дані, повʼязані з «із наявний групи».
     *
     * @param selectedIds ідентифікатори вибраних обʼєктів.
     */
    private void removeMembersFromExistingGroups(
            Set<String> selectedIds
    ) {
        for (NeuronGroup group : groups.values()) {
            group.removeMembers(selectedIds);
        }
    }
}
