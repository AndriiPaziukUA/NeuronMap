package com.example.neuronmap.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Представляє групу нейронів і зберігає ідентифікатори її учасників.
 */
public final class NeuronGroup {

    private final String id;
    private final Set<String> memberIds;

    /**
     * Створює екземпляр NeuronGroup та зберігає передані залежності, потрібні для його роботи.
     *
     * @param id унікальний ідентифікатор елемента.
     * @param memberIds ідентифікатори нейронів, які мають увійти до групи.
     */
    public NeuronGroup(
            String id,
            Collection<String> memberIds
    ) {
        if (id == null || id.isBlank()) {

            throw new IllegalArgumentException(
                    "Group id must not be blank."
            );
        }

        this.id = id;
        this.memberIds = new LinkedHashSet<>(
                memberIds
        );
    }

    /**
     * Повертає ідентифікатор об’єкта.
     *
     * @return ідентифікатор об’єкта.
     */
    public String id() {
        return id;
    }

/**
 * Повертає ідентифікатори учасників групи.
 *
 * @return ідентифікатори учасників групи.
 */
public Set<String> memberIds() {
        return Collections.unmodifiableSet(
                memberIds
        );
    }

    /**
     * Видаляє member з поточної моделі або подання.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    void removeMember(String neuronId) {
        memberIds.remove(neuronId);
    }

    /**
     * Видаляє members з поточної моделі або подання.
     *
     * @param neuronIds ідентифікатори нейронів, які потрібно обробити.
     */
    void removeMembers(Collection<String> neuronIds) {
        memberIds.removeAll(neuronIds);
    }
}
