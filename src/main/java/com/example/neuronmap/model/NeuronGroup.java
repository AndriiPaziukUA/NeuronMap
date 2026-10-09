package com.example.neuronmap.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Представляє групу нейронів і зберігає її склад та дані, спільні для групи.
 */
public final class NeuronGroup {

    private final String id;
    private final Set<String> memberIds;

    /**
     * Повертає результат операції «нейрон група».
     *
     * @param id ідентифікатор обʼєкта.
     *
     * @param memberIds значення, що визначає ідентифікатори для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronGroup(
            String id,
            Collection<String> memberIds
    ) {
        if (id == null || id.isBlank()) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
     * Повертає результат операції «ідентифікатор».
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    public String id() {
        return id;
    }

/**
 * Повертає результат операції «ідентифікатори».
 *
 * @return колекцію результатів; якщо елементів немає, колекція порожня.
 */
public Set<String> memberIds() {
        return Collections.unmodifiableSet(
                memberIds
        );
    }

    /**
     * Видаляє або скидає дані, повʼязані з «відповідну операцію».
     *
     * @param neuronId ідентифікатор нейрона.
     */
    void removeMember(String neuronId) {
        memberIds.remove(neuronId);
    }

    /**
     * Видаляє або скидає дані, повʼязані з «відповідну операцію».
     *
     * @param neuronIds ідентифікатори нейронів.
     */
    void removeMembers(Collection<String> neuronIds) {
        memberIds.removeAll(neuronIds);
    }
}
