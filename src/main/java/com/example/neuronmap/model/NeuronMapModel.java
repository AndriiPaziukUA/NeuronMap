package com.example.neuronmap.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Є основною моделлю карти: керує нейронами, їхнім поданням, напрямленими зв’язками, групами та структурами.
 * Структури мають окремий життєвий цикл і правила членства, не пов'язані з мінімальним розміром груп.
 */
public final class NeuronMapModel {

    private final Map<String, Neuron> neurons = new LinkedHashMap<>();
    private final Map<String, NeuronPresentation> presentations = new LinkedHashMap<>();
    private final Map<String, Connection> connections = new LinkedHashMap<>();
    private final Map<String, NeuronGroup> groups = new LinkedHashMap<>();
    private final Map<String, NeuronStructure> structures = new LinkedHashMap<>();

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
     * Нейрон типу {@link NeuronType#STRUCTURE} також отримує окрему порожню сутність структури.
     *
     * @param type тип нейрона або елемента.
     * @param x координата X.
     * @param y координата Y.
     * @param rotationDegrees кут обертання в градусах.
     * @return створений нейрон із відповідним візуальним поданням.
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
     * Для структурного типу додатково реєструється окрема сутність-контейнер.
     *
     * @param id унікальний ідентифікатор елемента.
     * @param type тип нейрона або елемента.
     * @param activation значення активації нейрона.
     * @return доданий нейрон.
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
        if (type == NeuronType.STRUCTURE) {
            structures.put(id, new NeuronStructure(id));
        }

        return neuron;
    }

    /**
     * Додає нейрон із заданим ідентифікатором до моделі та створює його подання.
     *
     * @param id унікальний ідентифікатор елемента.
     * @param type тип нейрона або елемента.
     * @param activation значення активації нейрона.
     * @param x координата X.
     * @param y координата Y.
     * @param rotationDegrees кут обертання в градусах.
     * @return доданий нейрон.
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
     * Додає імпортований напрямлений зв’язок лише після перевірки всіх правил сумісності його кінців.
     *
     * @param id унікальний ідентифікатор зв’язку.
     * @param sourceId ідентифікатор початкового нейрона зв’язку.
     * @param targetId ідентифікатор кінцевого нейрона зв’язку.
     */
    public void addConnection(
            String id,
            String sourceId,
            String targetId
    ) {
        if (id == null || id.isBlank()
                || validateConnection(sourceId, targetId) != ConnectionValidationResult.VALID) {
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
     * Перевіряє, чи дозволено створити напрямлений зв’язок між указаними нейронами.
     * Ураховує самозв’язки, дублікати, межі структур і внутрішні зв’язки в межах спільної структури.
     *
     * @param sourceId ідентифікатор початкового нейрона.
     * @param targetId ідентифікатор кінцевого нейрона.
     * @return причина відмови або {@link ConnectionValidationResult#VALID}, якщо зв’язок дозволено.
     */
    public ConnectionValidationResult validateConnection(String sourceId, String targetId) {
        if (sourceId == null || targetId == null
                || neuron(sourceId) == null || neuron(targetId) == null) {
            return ConnectionValidationResult.INVALID_ENDPOINT;
        }
        if (sourceId.equals(targetId)) {
            return ConnectionValidationResult.SELF_CONNECTION;
        }
        if (hasConnection(sourceId, targetId)) {
            return ConnectionValidationResult.ALREADY_EXISTS;
        }
        if (sharesStructureScope(sourceId, targetId)) {
            return ConnectionValidationResult.SAME_STRUCTURE_SCOPE;
        }

        boolean sourceIsStructure = structures.containsKey(sourceId);
        boolean targetIsStructure = structures.containsKey(targetId);
        if (sourceIsStructure != targetIsStructure) {
            return ConnectionValidationResult.STRUCTURE_ENDPOINT_TYPE_MISMATCH;
        }

        return ConnectionValidationResult.VALID;
    }

    /**
     * Створює напрямлений зв’язок, якщо пара нейронів відповідає всім правилам карти.
     *
     * @param sourceId ідентифікатор початкового нейрона.
     * @param targetId ідентифікатор кінцевого нейрона.
     * @return {@code true}, якщо зв’язок створено; інакше {@code false}.
     */
    public boolean createConnection(
            String sourceId,
            String targetId
    ) {
        if (validateConnection(sourceId, targetId) != ConnectionValidationResult.VALID) {
            return false;
        }

        String connectionId = UUID.randomUUID().toString();
        connections.put(
                connectionId,
                new Connection(
                        connectionId,
                        sourceId,
                        targetId
                )
        );
        return true;
    }

    /**
     * Перевіряє наявність напрямленого зв’язку від початкового нейрона до цільового.
     *
     * @param sourceId ідентифікатор початкового нейрона зв’язку.
     * @param targetId ідентифікатор кінцевого нейрона зв’язку.
     * @return {@code true}, якщо зв’язок існує; інакше {@code false}.
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
     * @param id унікальний ідентифікатор зв’язку.
     * @return видалений зв’язок або {@code null}, якщо його не знайдено.
     */
    public Connection removeConnection(String id) {
        return connections.remove(id);
    }

    /**
     * Видаляє зв’язки між указаною парою нейронів і повертає кількість видалених зв’язків.
     *
     * @param firstNeuronId ідентифікатор першого нейрона.
     * @param secondNeuronId ідентифікатор другого нейрона.
     * @return кількість видалених зв’язків.
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
     * Видаляє нейрон разом із його поданням, зв’язками, членством у групах і членством у структурах.
     * Якщо видалений нейрон представляв структуру, її учасники залишаються на карті без цього контейнера.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return видалений нейрон або {@code null}, якщо його не знайдено.
     */
    public Neuron removeNeuron(String neuronId) {
        Neuron removed = neurons.remove(neuronId);

        if (removed == null) {
            return null;
        }

        presentations.remove(neuronId);
        structures.remove(neuronId);

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
        for (NeuronStructure structure : structures.values()) {
            structure.removeMember(neuronId);
        }

        return removed;
    }

    /**
     * Додає групу з указаним ідентифікатором і набором учасників.
     *
     * @param id унікальний ідентифікатор групи.
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
     * Реєструє сутність структури, пов'язану з наявним нейроном.
     * Метод ідемпотентний, щоб завантажувач міг відновити окремі записи таблиці структур.
     *
     * @param structureId ідентифікатор нейрона-контейнера.
     * @return {@code true}, якщо структура вже існує або її вдалося створити.
     */
    public boolean addStructure(String structureId) {
        if (structureId == null || structureId.isBlank() || !neurons.containsKey(structureId)) {
            return false;
        }
        structures.computeIfAbsent(structureId, NeuronStructure::new);
        return true;
    }

    /**
     * Додає нейрон до структури з перевіркою посилань, єдиного власника та відсутності циклів вкладеності.
     *
     * @param structureId ідентифікатор структури-приймача.
     * @param neuronId ідентифікатор нейрона, який потрібно додати.
     * @return {@code true}, якщо членство створено; інакше {@code false}.
     */
    public boolean addStructureMember(String structureId, String neuronId) {
        NeuronStructure structure = structures.get(structureId);
        if (structure == null || neuron(neuronId) == null || structureId.equals(neuronId)) {
            return false;
        }

        NeuronStructure currentOwner = structureContaining(neuronId);
        if (currentOwner != null && !currentOwner.id().equals(structureId)) {
            return false;
        }
        if (structures.containsKey(neuronId)
                && containsMemberRecursively(neuronId, structureId, new LinkedHashSet<>())) {
            return false;
        }

        return structure.addMember(neuronId);
    }

    /**
     * Вилучає нейрон із заданої структури, не змінюючи сам нейрон або структуру.
     *
     * @param structureId ідентифікатор структури.
     * @param neuronId ідентифікатор учасника.
     * @return {@code true}, якщо членство існувало й було вилучене.
     */
    public boolean removeStructureMember(String structureId, String neuronId) {
        NeuronStructure structure = structures.get(structureId);
        return structure != null && structure.removeMember(neuronId);
    }

    /**
     * Знаходить нейрон за ідентифікатором або повертає null, якщо нейрона немає в моделі.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @return знайдений нейрон або {@code null}.
     */
    public Neuron neuron(String id) {
        return neurons.get(id);
    }

    /**
     * Знаходить візуальне подання нейрона за його ідентифікатором.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return подання нейрона або {@code null}.
     */
    public NeuronPresentation presentation(String neuronId) {
        return presentations.get(neuronId);
    }

    /**
     * Знаходить сутність структури за ідентифікатором її контейнера.
     *
     * @param structureId ідентифікатор нейрона-контейнера.
     * @return сутність структури або {@code null}, якщо структуру не зареєстровано.
     */
    public NeuronStructure structure(String structureId) {
        return structures.get(structureId);
    }

    /**
     * Повертає нейрони, що зараз містяться в моделі.
     *
     * @return незмінна колекція нейронів.
     */
    public Collection<Neuron> neurons() {
        return Collections.unmodifiableCollection(
                neurons.values()
        );
    }

    /**
     * Повертає візуальні подання нейронів, які зберігає модель.
     *
     * @return незмінна колекція подань нейронів.
     */
    public Collection<NeuronPresentation> presentations() {
        return Collections.unmodifiableCollection(
                presentations.values()
        );
    }

    /**
     * Повертає всі напрямлені зв’язки моделі.
     *
     * @return незмінна колекція зв’язків.
     */
    public Collection<Connection> connections() {
        return Collections.unmodifiableCollection(
                connections.values()
        );
    }

    /**
     * Повертає всі групи нейронів моделі.
     *
     * @return незмінна колекція груп.
     */
    public Collection<NeuronGroup> groups() {
        return Collections.unmodifiableCollection(
                groups.values()
        );
    }

    /**
     * Повертає всі сутності структур моделі, включно з порожніми контейнерами.
     *
     * @return незмінна колекція структур.
     */
    public Collection<NeuronStructure> structures() {
        return Collections.unmodifiableCollection(structures.values());
    }

    /**
     * Знаходить групу, до якої належить вказаний нейрон.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return відповідна група або {@code null}.
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
     * Знаходить структуру, яка безпосередньо містить указаний нейрон.
     *
     * @param neuronId ідентифікатор учасника.
     * @return структура-власник або {@code null}, якщо учасник не належить структурі.
     */
    public NeuronStructure structureContaining(String neuronId) {
        for (NeuronStructure structure : structures.values()) {
            if (structure.memberIds().contains(neuronId)) {
                return structure;
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
     * @return {@code true}, якщо модель порожня; інакше {@code false}.
     */
    public boolean isEmpty() {
        return neurons.isEmpty()
                && connections.isEmpty()
                && groups.isEmpty()
                && structures.isEmpty();
    }

    /**
     * Очищає нейрони, подання, зв’язки, групи та структури з моделі.
     */
    public void clear() {
        neurons.clear();
        presentations.clear();
        connections.clear();
        groups.clear();
        structures.clear();
    }

    /**
     * Замінює весь вміст моделі даними вже підготовленої моделі, зберігаючи ідентичність цього екземпляра.
     * Цей метод дає службам змогу спершу повністю завантажити новий стан, а потім замінити вміст спільної моделі без створення нового екземпляра.
     *
     * @param source модель із підготовленим вмістом.
     * @throws NullPointerException якщо підготовлену модель не передано.
     */
    public void replaceContentsFrom(NeuronMapModel source) {
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }

        neurons.clear();
        neurons.putAll(source.neurons);
        presentations.clear();
        presentations.putAll(source.presentations);
        connections.clear();
        connections.putAll(source.connections);
        groups.clear();
        groups.putAll(source.groups);
        structures.clear();
        structures.putAll(source.structures);
    }

    /**
     * Перевіряє, чи входять обидва нейрони до одного дерева структури, ураховуючи вкладені контейнери.
     *
     * @param firstNeuronId ідентифікатор першого нейрона.
     * @param secondNeuronId ідентифікатор другого нейрона.
     * @return {@code true}, якщо обидва елементи належать спільній структурній області.
     */
    private boolean sharesStructureScope(String firstNeuronId, String secondNeuronId) {
        for (NeuronStructure structure : structures.values()) {
            if (isInStructureScope(structure.id(), firstNeuronId)
                    && isInStructureScope(structure.id(), secondNeuronId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Перевіряє, чи є нейрон самою структурою або її прямим чи вкладеним учасником.
     *
     * @param structureId ідентифікатор структури, яка визначає область.
     * @param neuronId ідентифікатор нейрона для перевірки.
     * @return {@code true}, якщо нейрон належить цій області.
     */
    private boolean isInStructureScope(String structureId, String neuronId) {
        return structureId.equals(neuronId)
                || containsMemberRecursively(structureId, neuronId, new LinkedHashSet<>());
    }

    /**
     * Перевіряє, чи містить структура вказаного учасника безпосередньо або через вкладені структури.
     *
     * @param candidateStructureId ідентифікатор структури-кандидата.
     * @param memberId ідентифікатор очікуваного учасника.
     * @param visited структури, які вже відвідано під час обходу.
     * @return {@code true}, якщо учасник входить до дерева структури.
     */
    private boolean containsMemberRecursively(
            String candidateStructureId,
            String memberId,
            Set<String> visited
    ) {
        if (!visited.add(candidateStructureId)) {
            return false;
        }
        NeuronStructure candidate = structures.get(candidateStructureId);
        if (candidate == null) {
            return false;
        }
        for (String candidateMemberId : candidate.memberIds()) {
            if (memberId.equals(candidateMemberId)) {
                return true;
            }
            if (structures.containsKey(candidateMemberId)
                    && containsMemberRecursively(candidateMemberId, memberId, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Вилучає вибраних нейронів із груп, зберігаючи решту учасників.
     *
     * @param selectedIds ідентифікатори нейронів, які потрібно вилучити з груп.
     */
    private void removeMembersFromExistingGroups(
            Set<String> selectedIds
    ) {
        for (NeuronGroup group : groups.values()) {
            group.removeMembers(selectedIds);
        }
    }
}
