package com.example.neuronmap.service;

import com.example.neuronmap.model.NeuronGroup;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronPresentation;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Керує групами нейронів і забезпечує переміщення всіх учасників групи як одного об’єкта.
 */
public final class GroupService {

    private final NeuronMapModel model;

    /**
     * Створює службу груп для заданої моделі карти.
     *
     * @param model модель карти нейронів.
     * @throws NullPointerException якщо модель не задано.
     */
    public GroupService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Створює групу з переданих ідентифікаторів нейронів.
     *
     * @param memberIds ідентифікатори нейронів, які мають увійти до групи.
     * @throws NullPointerException якщо набір ідентифікаторів не задано.
     */
    public void create(Set<String> memberIds) {
        model.createGroup(new LinkedHashSet<>(Objects.requireNonNull(memberIds, "memberIds")));
    }

    /**
     * Вилучає вказаних нейронів із груп, видаляючи групи, у яких залишилося менше двох учасників.
     *
     * @param memberIds ідентифікатори нейронів, які потрібно вилучити з груп.
     * @throws NullPointerException якщо набір ідентифікаторів не задано.
     */
    public void ungroup(Set<String> memberIds) {
        model.ungroup(new LinkedHashSet<>(Objects.requireNonNull(memberIds, "memberIds")));
    }

    /**
     * Переміщує всі нейрони групи на однакове зміщення, якщо вказаний нейрон належить групі.
     *
     * @param neuronId ідентифікатор нейрона, групу якого потрібно перемістити.
     * @param dx горизонтальна складова зміщення.
     * @param dy вертикальна складова зміщення.
     * @return {@code true}, якщо позицію принаймні одного учасника змінено; інакше {@code false}.
     */
    public boolean moveContaining(String neuronId, double dx, double dy) {
        Optional<NeuronGroup> group = containing(neuronId);
        if (group.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (String memberId : group.orElseThrow().memberIds()) {
            NeuronPresentation presentation = model.presentation(memberId);
            if (presentation == null) {
                continue;
            }
            presentation.moveBy(dx, dy);
            changed = true;
        }
        return changed;
    }

    /**
     * Знаходить групу, яка містить указаний нейрон.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return група, що містить нейрон, або порожній результат для некоректного ідентифікатора чи відсутньої групи.
     */
    public Optional<NeuronGroup> containing(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(model.groupContaining(neuronId));
    }
}
