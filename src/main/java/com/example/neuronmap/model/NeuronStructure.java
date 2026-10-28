package com.example.neuronmap.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Представляє структуру карти, зберігаючи впорядкованих учасників і стан її шухлядки.
 * Стан відкритості є частиною моделі, тому відновлюється разом із картою та історією змін.
 */
public final class NeuronStructure {

    private final String id;
    private final Set<String> memberIds = new LinkedHashSet<>();
    private boolean expanded;

    /**
     * Створює порожню закриту структуру, пов'язану з ідентифікатором структурного елемента карти.
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
     * Повертає збережений стан шухлядки структури.
     *
     * @return {@code true}, якщо шухлядка відкрита.
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * Встановлює стан шухлядки, який має переживати збереження та відновлення карти.
     *
     * @param expanded {@code true}, щоб шухлядка залишалася відкритою.
     */
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
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
