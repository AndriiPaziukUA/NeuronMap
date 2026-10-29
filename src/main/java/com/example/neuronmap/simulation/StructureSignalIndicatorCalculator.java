package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronStructure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Обчислює агреговані індикатори структур на основі фактичної активності їхніх звичайних нейронів. */
final class StructureSignalIndicatorCalculator {

    private final NeuronMapModel model;

    /**
     * Створює калькулятор похідного стану структур для конкретної моделі карти.
     *
     * @param model модель, у якій визначено дерева структур і зв'язки між нейронами.
     */
    StructureSignalIndicatorCalculator(NeuronMapModel model) {
        this.model = model;
    }

    /**
     * Обчислює кількість активованих входів, випромінювачів і структурних зв'язків для анімації.
     *
     * @param nextActivatedNeuronIds звичайні нейрони, чиї вхідні індикатори засвітяться від сигналів у дорозі.
     * @param emittingNeuronIds звичайні нейрони, які випустили власний сигнал у поточному такті.
     * @return незмінний знімок індикаторів структур для відображення в результаті такту.
     */
    Summary calculate(Set<String> nextActivatedNeuronIds, Set<String> emittingNeuronIds) {
        Map<String, Set<String>> memberIdsByStructure = collectMemberIdsByStructure();
        Map<String, Integer> inputCounts = new LinkedHashMap<>();
        Map<String, Integer> emissionCounts = new LinkedHashMap<>();
        memberIdsByStructure.forEach((structureId, memberIds) -> {
            inputCounts.put(structureId, countIntersection(memberIds, nextActivatedNeuronIds));
            emissionCounts.put(structureId, countIntersection(memberIds, emittingNeuronIds));
        });
        Set<String> structureConnectionIds = collectStructureConnectionsToAnimate(
                emittingNeuronIds, memberIdsByStructure);
        return new Summary(inputCounts, emissionCounts, structureConnectionIds);
    }

    /**
     * Формує для кожної структури набір усіх її звичайних нащадків, не рахуючи контейнери.
     *
     * @return мапа від ID структури до множини ID її звичайних нащадків.
     */
    private Map<String, Set<String>> collectMemberIdsByStructure() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (NeuronStructure structure : model.structures()) {
            Set<String> members = new LinkedHashSet<>();
            collectDescendantNeuronIds(structure.id(), new LinkedHashSet<>(), members);
            result.put(structure.id(), members);
        }
        return result;
    }

    /**
     * Обходить дерево структури, включаючи звичайних нащадків вкладених контейнерів.
     *
     * @param structureId ID контейнера, учасників якого потрібно обійти.
     * @param visitedStructureIds контейнери, уже відвідані під час поточного обходу.
     * @param descendantNeuronIds набір, до якого додаються звичайні нащадки.
     */
    private void collectDescendantNeuronIds(
            String structureId,
            Set<String> visitedStructureIds,
            Set<String> descendantNeuronIds
    ) {
        if (!visitedStructureIds.add(structureId)) {
            return;
        }
        NeuronStructure structure = model.structure(structureId);
        if (structure == null) {
            return;
        }
        for (String memberId : structure.memberIds()) {
            Neuron member = model.neuron(memberId);
            if (member == null) {
                continue;
            }
            if (!member.type().participatesInSignalPropagation()) {
                collectDescendantNeuronIds(memberId, visitedStructureIds, descendantNeuronIds);
            } else {
                descendantNeuronIds.add(memberId);
            }
        }
    }

    /**
     * Рахує спільні ідентифікатори між членами структури та набором активних нейронів.
     *
     * @param memberIds ідентифікатори звичайних нейронів структури.
     * @param activeNeuronIds ідентифікатори активних або випромінюючих нейронів.
     * @return кількість ідентифікаторів, що присутні в обох наборах.
     */
    private int countIntersection(Set<String> memberIds, Set<String> activeNeuronIds) {
        int count = 0;
        for (String memberId : memberIds) {
            if (activeNeuronIds.contains(memberId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Визначає структурні зв'язки, які відповідають фактичним сигналам між звичайними нейронами-нащадками.
     * Індекси членства та вихідних структурних зв'язків запобігають повторному повному обходу ребер для кожного сигналу.
     *
     * @param emittingNeuronIds звичайні нейрони, які передають сигнал у поточному такті.
     * @param memberIdsByStructure повні набори звичайних нащадків кожної структури.
     * @return ID структурних зв'язків, для яких є відповідне внутрішнє передавання.
     */
    private Set<String> collectStructureConnectionsToAnimate(
            Set<String> emittingNeuronIds,
            Map<String, Set<String>> memberIdsByStructure
    ) {
        Set<String> result = new LinkedHashSet<>();
        if (emittingNeuronIds.isEmpty()) {
            return result;
        }

        Map<String, Set<String>> structureIdsByMember = indexStructuresByMember(memberIdsByStructure);
        Map<String, Map<String, String>> connectionIdsBySourceAndTarget = indexStructureConnections();
        for (Connection memberConnection : model.connections()) {
            String sourceMemberId = memberConnection.sourceId();
            String targetMemberId = memberConnection.targetId();
            Neuron sourceMember = model.neuron(sourceMemberId);
            Neuron targetMember = model.neuron(targetMemberId);
            if (!emittingNeuronIds.contains(sourceMemberId)
                    || sourceMember == null || targetMember == null
                    || !sourceMember.type().participatesInSignalPropagation()
                    || !targetMember.type().participatesInSignalPropagation()) {
                continue;
            }

            Set<String> sourceStructureIds = structureIdsByMember.getOrDefault(sourceMemberId, Set.of());
            Set<String> targetStructureIds = structureIdsByMember.getOrDefault(targetMemberId, Set.of());
            for (String sourceStructureId : sourceStructureIds) {
                Map<String, String> targetConnections = connectionIdsBySourceAndTarget.get(sourceStructureId);
                if (targetConnections == null) {
                    continue;
                }
                for (String targetStructureId : targetStructureIds) {
                    String connectionId = targetConnections.get(targetStructureId);
                    if (connectionId != null) {
                        result.add(connectionId);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Інвертує множини членства, щоб швидко отримувати всі структури-предки звичайного нейрона.
     *
     * @param memberIdsByStructure повні набори звичайних нащадків для кожної структури.
     * @return мапа від ID звичайного нейрона до ID усіх структур, що його містять.
     */
    private Map<String, Set<String>> indexStructuresByMember(Map<String, Set<String>> memberIdsByStructure) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        memberIdsByStructure.forEach((structureId, memberIds) -> {
            for (String memberId : memberIds) {
                result.computeIfAbsent(memberId, ignored -> new LinkedHashSet<>()).add(structureId);
            }
        });
        return result;
    }

    /**
     * Індексує структурні зв'язки за ID вихідної та цільової структур.
     *
     * @return ID зв'язку для кожної пари вихідної та цільової структур.
     */
    private Map<String, Map<String, String>> indexStructureConnections() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Connection connection : model.connections()) {
            if (model.structure(connection.sourceId()) == null
                    || model.structure(connection.targetId()) == null) {
                continue;
            }
            result.computeIfAbsent(connection.sourceId(), ignored -> new LinkedHashMap<>())
                    .put(connection.targetId(), connection.id());
        }
        return result;
    }

    /** Зберігає незмінні лічильники структур і перелік їхніх зв'язків, що потрібно анімувати. */
    record Summary(
            Map<String, Integer> structureInputActivationCounts,
            Map<String, Integer> structureEmittingNeuronCounts,
            Set<String> structureConnectionIdsToAnimate
    ) {
        /**
         * Створює незмінний знімок трьох наборів індикаторного стану.
         *
         * @param structureInputActivationCounts кількість активованих внутрішніх входів за ID структури.
         * @param structureEmittingNeuronCounts кількість випромінювачів усередині кожної структури.
         * @param structureConnectionIdsToAnimate ID структурних зв'язків, що відображають реальне передавання.
         */
        Summary {
            structureInputActivationCounts = Collections.unmodifiableMap(
                    new LinkedHashMap<>(structureInputActivationCounts));
            structureEmittingNeuronCounts = Collections.unmodifiableMap(
                    new LinkedHashMap<>(structureEmittingNeuronCounts));
            structureConnectionIdsToAnimate = Collections.unmodifiableSet(
                    new LinkedHashSet<>(structureConnectionIdsToAnimate));
        }
    }
}
