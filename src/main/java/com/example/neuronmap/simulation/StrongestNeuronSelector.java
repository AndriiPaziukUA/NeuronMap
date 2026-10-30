package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronStructure;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Обмежує автоматичну активацію найсильнішим кандидатом у кожному дереві структури з відповідним режимом.
 * Кандидати поза такими деревами не змінюються, а рівні суми розв'язуються на користь першого учасника за порядком.
 */
final class StrongestNeuronSelector {

    private final NeuronMapModel model;

    /**
     * Створює селектор, який читає актуальний порядок членства та спільні налаштування з моделі.
     *
     * @param model модель карти з нейронами та структурами.
     * @throws NullPointerException якщо модель не задано.
     */
    StrongestNeuronSelector(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Повертає найсильнішого серед кандидатів кожного дерева, які вже пройшли власний поріг активації.
     * Отримувач із більшою сумою, але без виконаного порога, не бере участі в конкуренції та не блокує інших.
     *
     * @param inputSums суми вхідних сигналів поточного кроку.
     * @param eligibleNeuronIds кандидати, які пройшли поріг активації.
     * @return нова впорядкована множина нейронів, що можуть активуватися автоматично.
     */
    Set<String> selectWinners(Map<String, Integer> inputSums, Set<String> eligibleNeuronIds) {
        Objects.requireNonNull(inputSums, "inputSums");
        Objects.requireNonNull(eligibleNeuronIds, "eligibleNeuronIds");
        Set<String> winners = new LinkedHashSet<>(eligibleNeuronIds);
        for (NeuronStructure structure : model.structures()) {
            if (model.structureContaining(structure.id()) != null
                    || !structure.settings().strongestNeuronOnly()) {
                continue;
            }
            Set<String> members = collectSignalMemberIds(structure.id(), new LinkedHashSet<>());
            retainStrongestEligibleSignal(inputSums, winners, members);
        }
        return winners;
    }

    /**
     * Збирає звичайних нащадків у порядку додавання, обходячи кожну вкладену структуру один раз.
     *
     * @param structureId ідентифікатор контейнера, дерево якого потрібно обійти.
     * @param visitedStructureIds контейнери, уже відвідані в поточному обході.
     * @return впорядкована множина звичайних нейронів дерева без контейнерів.
     */
    private Set<String> collectSignalMemberIds(String structureId, Set<String> visitedStructureIds) {
        Set<String> result = new LinkedHashSet<>();
        if (!visitedStructureIds.add(structureId)) {
            return result;
        }
        NeuronStructure structure = model.structure(structureId);
        if (structure == null) {
            return result;
        }
        for (String memberId : structure.memberIds()) {
            Neuron member = model.neuron(memberId);
            if (member == null) {
                continue;
            }
            NeuronStructure nestedStructure = model.structure(memberId);
            if (nestedStructure != null) {
                result.addAll(collectSignalMemberIds(memberId, visitedStructureIds));
            } else if (member.type().participatesInSignalPropagation()) {
                result.add(memberId);
            }
        }
        return result;
    }

    /**
     * Залишає лише найсильнішого серед учасників, які вже пройшли власний поріг активації.
     * Учасники з отриманим, але недостатнім сигналом не впливають на вибір переможця.
     */
    private void retainStrongestEligibleSignal(
            Map<String, Integer> inputSums,
            Set<String> eligibleNeuronIds,
            Set<String> orderedMemberIds
    ) {
        String strongestEligibleId = null;
        int strongestEligibleSum = Integer.MIN_VALUE;
        for (String memberId : orderedMemberIds) {
            if (!eligibleNeuronIds.contains(memberId)) {
                continue;
            }
            Integer sum = inputSums.get(memberId);
            if (sum != null && (strongestEligibleId == null || sum > strongestEligibleSum)) {
                strongestEligibleId = memberId;
                strongestEligibleSum = sum;
            }
        }
        if (strongestEligibleId == null) {
            return;
        }
        for (String memberId : orderedMemberIds) {
            if (!memberId.equals(strongestEligibleId)) {
                eligibleNeuronIds.remove(memberId);
            }
        }
    }
}
