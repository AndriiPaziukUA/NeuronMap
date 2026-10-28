package com.example.neuronmap.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Представляє окрему структуру карти та зберігає впорядкований набір ідентифікаторів її нейронів.
 * На відміну від групи, структура може бути порожньою й не має мінімальної кількості учасників.
 */
public final class NeuronStructure {

    private final String id;
    private final Set<String> memberIds = new LinkedHashSet<>();

    /**
     * Створює порожню структуру, пов'язану з ідентифікатором структурного елемента карти.
     *
     * @param id ідентифікатор нейрона, що представляє структуру.
     * @throws IllegalArgumentException якщо ідентифікатор порожній.
     */
    public NeuronStructure(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Structure id must not be blank.");
        }
        this.id = id;
    }

    /**
     * Повертає ідентифікатор нейрона, який представляє цю структуру на карті.
     *
     * @return ідентифікатор структури.
     */
    public String id() {
        return id;
    }

    /**
     * Повертає ідентифікатори учасників у порядку їх додавання до структури.
     *
     * @return незмінний впорядкований набір ідентифікаторів учасників.
     */
    public Set<String> memberIds() {
        return Collections.unmodifiableSet(memberIds);
    }

    /**
     * Додає учасника без дублювання та без дозволу структури містити саму себе.
     * Перевірка існування нейрона й належності до інших структур виконується моделлю карти.
     *
     * @param neuronId ідентифікатор нейрона-кандидата.
     * @return {@code true}, якщо учасника додано.
     */
    boolean addMember(String neuronId) {
        if (neuronId == null || neuronId.isBlank() || id.equals(neuronId)) {
            return false;
        }
        return memberIds.add(neuronId);
    }

    /**
     * Вилучає вказаного учасника зі структури.
     *
     * @param neuronId ідентифікатор нейрона, який потрібно вилучити.
     * @return {@code true}, якщо учасник був у структурі.
     */
    boolean removeMember(String neuronId) {
        return memberIds.remove(neuronId);
    }
}
