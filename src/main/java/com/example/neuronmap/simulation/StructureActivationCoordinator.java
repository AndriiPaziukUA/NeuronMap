package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronStructure;
import com.example.neuronmap.model.NeuronType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Готує спільні центри приймальних структур для звичайних нейронів, що активувалися одночасно.
 * Центр належить лише поточній множині активованих джерел: додаткові вхідні зв'язки від інших нейронів
 * роблять наявного кандидата непридатним для повторного використання. Сама структура лишається контейнером.
 */
final class StructureActivationCoordinator {

    private final NeuronMapModel model;

    /**
     * Створює координатор структурних активацій для конкретної моделі карти.
     *
     * @param model модель, яка містить структури, нейрони та напрямлені зв'язки.
     * @throws NullPointerException якщо модель не задано.
     */
    StructureActivationCoordinator(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Забезпечує кожній цільовій структурі спільний внутрішній нейрон для всіх активованих джерел,
     * що надходять до неї через структурні зв'язки. Усі центри готуються до випуску вихідних сигналів.
     *
     * @param activatedNeuronIds унікальні звичайні нейрони, активовані в поточному такті.
     * @throws NullPointerException якщо множину активацій не задано.
     */
    void prepareSharedCenters(Set<String> activatedNeuronIds) {
        Objects.requireNonNull(activatedNeuronIds, "activatedNeuronIds");
        if (activatedNeuronIds.isEmpty()) {
            return;
        }

        Map<String, Set<String>> activatedSourcesByStructure = collectActivatedSourcesByStructure(
                activatedNeuronIds);
        Map<String, Set<String>> activatedSourcesByTarget = collectActivatedSourcesByTarget(
                activatedSourcesByStructure);
        Map<String, Set<String>> incomingSourcesByTarget = collectIncomingSourcesByTarget();
        for (Map.Entry<String, Set<String>> entry : activatedSourcesByTarget.entrySet()) {
            String targetStructureId = entry.getKey();
            Set<String> sourceIds = entry.getValue();
            if (sourceIds.isEmpty()
                    || findSharedCenter(targetStructureId, sourceIds, incomingSourcesByTarget) != null) {
                continue;
            }
            createSharedCenter(targetStructureId, sourceIds);
        }
    }

    /**
     * Визначає всі структури-предки, яким належать активовані звичайні нейрони.
     *
     * @param activatedNeuronIds активовані нейрони поточного такту.
     * @return мапа від ID структури до множини її активованих внутрішніх нейронів.
     */
    private Map<String, Set<String>> collectActivatedSourcesByStructure(Set<String> activatedNeuronIds) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String neuronId : activatedNeuronIds) {
            Neuron neuron = model.neuron(neuronId);
            if (isNonSignalNeuron(neuron)) {
                continue;
            }
            NeuronStructure owner = model.structureContaining(neuronId);
            while (owner != null) {
                result.computeIfAbsent(owner.id(), ignored -> new LinkedHashSet<>()).add(neuronId);
                owner = model.structureContaining(owner.id());
            }
        }
        return result;
    }

    /**
     * Об'єднує активовані джерела всіх структур, що мають вихідний структурний зв'язок до спільного приймача.
     *
     * @param activatedSourcesByStructure активовані нейрони за структурами-джерелами.
     * @return унікальні активовані джерела для кожної цільової структури.
     */
    private Map<String, Set<String>> collectActivatedSourcesByTarget(
            Map<String, Set<String>> activatedSourcesByStructure
    ) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Connection connection : model.connections()) {
            if (model.structure(connection.sourceId()) == null
                    || model.structure(connection.targetId()) == null) {
                continue;
            }
            Set<String> sourceIds = activatedSourcesByStructure.get(connection.sourceId());
            if (sourceIds == null || sourceIds.isEmpty()) {
                continue;
            }
            result.computeIfAbsent(connection.targetId(), ignored -> new LinkedHashSet<>()).addAll(sourceIds);
        }
        return result;
    }

    /**
     * Шукає звичайного учасника цільової структури, до якого вже ведуть зв'язки від усіх активованих джерел.
     *
     * @param targetStructureId ідентифікатор структури-приймача.
     * @param sourceIds унікальні активовані джерела, які мають спільно використовувати центр.
     * @param incomingSourcesByTarget усі джерела кожного нейрона-приймача, побудовані один раз за такт.
     * @return наявний спільний центр або {@code null}, якщо його немає.
     */
    private Neuron findSharedCenter(
            String targetStructureId,
            Set<String> sourceIds,
            Map<String, Set<String>> incomingSourcesByTarget
    ) {
        for (Neuron candidate : model.neurons()) {
            if (isNonSignalNeuron(candidate) || !belongsToStructure(candidate.id(), targetStructureId)) {
                continue;
            }
            boolean connectedFromEverySource = true;
            for (String sourceId : sourceIds) {
                if (!model.hasConnection(sourceId, candidate.id())) {
                    connectedFromEverySource = false;
                    break;
                }
            }
            if (connectedFromEverySource
                    && incomingSourcesByTarget.getOrDefault(candidate.id(), Set.of()).equals(sourceIds)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Індексує всі вхідні зв'язки, щоб перевірити належність кожного кандидата конкретній групі активації.
     * Враховуються й неактивні джерела: їх наявність забороняє повторно використовувати спільний центр.
     *
     * @return мапа від ID нейрона-приймача до множини всіх джерел його вхідних зв'язків.
     */
    private Map<String, Set<String>> collectIncomingSourcesByTarget() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Connection connection : model.connections()) {
            result.computeIfAbsent(connection.targetId(), ignored -> new LinkedHashSet<>())
                    .add(connection.sourceId());
        }
        return result;
    }

    /**
     * Додає до приймальної структури новий звичайний нейрон і під'єднує до нього всі активовані джерела.
     *
     * @param targetStructureId ідентифікатор структури-приймача.
     * @param sourceIds унікальні активовані внутрішні нейрони, що мають надіслати сигнал центру.
     * @throws IllegalStateException якщо модель не змогла додати учасника або створити один із зв'язків.
     */
    private void createSharedCenter(String targetStructureId, Set<String> sourceIds) {
        Neuron center = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        try {
            if (!model.addStructureMember(targetStructureId, center.id())) {
                throw new IllegalStateException(
                        "Could not add a generated shared center to structure " + targetStructureId);
            }
            for (String sourceId : sourceIds) {
                if (!model.createConnection(sourceId, center.id())) {
                    throw new IllegalStateException(
                            "Could not connect an activated source to shared center " + center.id());
                }
            }
        } catch (RuntimeException | Error failure) {
            model.removeNeuron(center.id());
            throw failure;
        }
    }

    /**
     * Перевіряє, чи є нейрон прямим або вкладеним учасником заданої структури.
     *
     * @param neuronId ідентифікатор нейрона-кандидата.
     * @param targetStructureId ідентифікатор структури-приймача.
     * @return {@code true}, якщо кандидат належить дереву структури-приймача.
     */
    private boolean belongsToStructure(String neuronId, String targetStructureId) {
        NeuronStructure owner = model.structureContaining(neuronId);
        while (owner != null) {
            if (owner.id().equals(targetStructureId)) {
                return true;
            }
            owner = model.structureContaining(owner.id());
        }
        return false;
    }

    /**
     * Перевіряє, чи відсутній нейрон або він не може брати участь у передаванні сигналів.
     *
     * @param neuron нейрон для перевірки або {@code null}, якщо його вже немає в моделі.
     * @return {@code true} для відсутнього нейрона або контейнера структури.
     */
    private boolean isNonSignalNeuron(Neuron neuron) {
        return neuron == null || !neuron.type().participatesInSignalPropagation();
    }
}
