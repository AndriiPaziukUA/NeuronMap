package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronStructure;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.model.StructureNeuronCreationMode;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Готує спільні центри приймальних структур для звичайних нейронів, які фактично випускають сигнали одночасно.
 * Центр належить лише поточній множині випромінювачів: додаткові вхідні зв'язки від інших нейронів
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
     * Забезпечує кожній цільовій структурі спільний внутрішній нейрон для всіх випромінювачів,
     * що надходять до неї через структурні зв'язки. Центри готуються до передавання сигналів цього такту.
     *
     * @param emittingNeuronIds унікальні звичайні нейрони, які фактично випускають сигнал у поточному такті.
     * @throws NullPointerException якщо множину випромінювачів не задано.
     */
    void prepareSharedCenters(Set<String> emittingNeuronIds) {
        Objects.requireNonNull(emittingNeuronIds, "emittingNeuronIds");
        if (emittingNeuronIds.isEmpty()) {
            return;
        }

        Map<String, Set<String>> emittingSourcesByStructure = collectEmittingSourcesByStructure(
                emittingNeuronIds);
        Map<String, Set<String>> emittingSourcesByTarget = collectEmittingSourcesByTarget(
                emittingSourcesByStructure);
        Map<String, Set<String>> incomingSourcesByTarget = collectIncomingSourcesByTarget();
        for (Map.Entry<String, Set<String>> entry : emittingSourcesByTarget.entrySet()) {
            String targetStructureId = entry.getKey();
            Set<String> sourceIds = entry.getValue();
            if (sourceIds.isEmpty()
                    || !allConnectedSourceStructuresAreEmitting(targetStructureId, emittingSourcesByStructure)
                    || findSharedCenter(targetStructureId, sourceIds, incomingSourcesByTarget) != null) {
                continue;
            }
            createSharedCenter(targetStructureId, sourceIds);
        }
    }

    /**
     * Визначає всі структури-предки, яким належать нейрони, що випускають сигнал у поточному такті.
     *
     * @param emittingNeuronIds нейрони-випромінювачі поточного такту.
     * @return мапа від ID структури до множини її випромінювачів.
     */
    private Map<String, Set<String>> collectEmittingSourcesByStructure(Set<String> emittingNeuronIds) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String neuronId : emittingNeuronIds) {
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
     * Перевіряє умову режиму «За кількістю структур» для кожного прямого структурного входу приймача.
     * У режимі за кількістю нейронів достатньо будь-якої непорожньої групи випромінювачів.
     *
     * @param targetStructureId ідентифікатор структури, яка може створити спільний центр.
     * @param emittingSourcesByStructure нейрони-випромінювачі, згруповані за всіма структурами-предками.
     * @return {@code true}, якщо вибраний режим дозволяє створення центру в цьому такті.
     */
    private boolean allConnectedSourceStructuresAreEmitting(
            String targetStructureId,
            Map<String, Set<String>> emittingSourcesByStructure
    ) {
        NeuronStructure target = model.structure(targetStructureId);
        if (target == null
                || target.settings().neuronCreationMode() == StructureNeuronCreationMode.NEURON_COUNT) {
            return target != null;
        }

        boolean hasConnectedSource = false;
        for (Connection connection : model.connections()) {
            if (!targetStructureId.equals(connection.targetId())
                    || model.structure(connection.sourceId()) == null) {
                continue;
            }
            hasConnectedSource = true;
            if (emittingSourcesByStructure.getOrDefault(connection.sourceId(), Set.of()).isEmpty()) {
                return false;
            }
        }
        return hasConnectedSource;
    }

    /**
     * Об'єднує випромінювачів усіх структур, що мають вихідний структурний зв'язок до спільного приймача.
     *
     * @param emittingSourcesByStructure випромінювачі за структурами-джерелами.
     * @return унікальні нейрони-випромінювачі для кожної цільової структури.
     */
    private Map<String, Set<String>> collectEmittingSourcesByTarget(
            Map<String, Set<String>> emittingSourcesByStructure
    ) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Connection connection : model.connections()) {
            if (model.structure(connection.sourceId()) == null
                    || model.structure(connection.targetId()) == null) {
                continue;
            }
            Set<String> sourceIds = emittingSourcesByStructure.get(connection.sourceId());
            if (sourceIds == null || sourceIds.isEmpty()) {
                continue;
            }
            result.computeIfAbsent(connection.targetId(), ignored -> new LinkedHashSet<>()).addAll(sourceIds);
        }
        return result;
    }

    /**
     * Шукає звичайного учасника цільової структури, до якого вже ведуть зв'язки від усіх поточних випромінювачів.
     *
     * @param targetStructureId ідентифікатор структури-приймача.
     * @param sourceIds унікальні нейрони-випромінювачі, які мають спільно використовувати центр.
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
     * Індексує всі вхідні зв'язки, щоб перевірити належність кожного кандидата конкретній групі випромінювачів.
     * Враховуються й нейрони, які не випускають сигналу в поточному такті:
     * їхні зв'язки виключають повторне використання.
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
     * Додає до приймальної структури новий звичайний нейрон і під'єднує до нього всіх поточних випромінювачів.
     *
     * @param targetStructureId ідентифікатор структури-приймача.
     * @param sourceIds унікальні внутрішні нейрони, що фактично надсилають сигнал у цьому такті.
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
                            "Could not connect an emitting source to shared center " + center.id());
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
