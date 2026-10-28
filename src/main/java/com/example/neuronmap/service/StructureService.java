package com.example.neuronmap.service;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronStructure;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Надає операції читання та зміни членства й стану шухлядок структур, делегуючи цілісність моделі.
 * Внутрішні нейрони не мають самостійного розміщення на дошці: розміщенням керує шухлядка.
 */
public final class StructureService {

    private final NeuronMapModel model;

    /**
     * Створює службу структур для заданої моделі карти.
     *
     * @param model модель карти, яка володіє структурами та нейронами.
     * @throws NullPointerException якщо модель не задано.
     */
    public StructureService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /** Повертає всі структури моделі.
     * @return незмінна колекція структур.
     */
    public Collection<NeuronStructure> structures() {
        return model.structures();
    }

    /** Знаходить структуру за ідентифікатором її структурного елемента.
     * @param structureId ідентифікатор нейрона-контейнера.
     * @return структура або порожній результат, якщо її не знайдено.
     */
    public Optional<NeuronStructure> find(String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(model.structure(structureId));
    }

    /** Знаходить структуру, яка безпосередньо містить указаний нейрон.
     * @param neuronId ідентифікатор нейрона.
     * @return структура-власник або порожній результат.
     */
    public Optional<NeuronStructure> containing(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(model.structureContaining(neuronId));
    }

    /** Додає нейрон до структури та скидає його координати дошки: розміщенням керує шухлядка.
     * @param structureId ідентифікатор структури-приймача.
     * @param neuronId ідентифікатор нейрона-кандидата.
     * @return {@code true}, якщо учасника додано.
     */
    public boolean addMember(String structureId, String neuronId) {
        if (!model.addStructureMember(structureId, neuronId)) {
            return false;
        }
        var presentation = model.presentation(neuronId);
        if (presentation != null) {
            presentation.setPosition(0.0, 0.0);
        }
        return true;
    }

    /** Вилучає нейрон зі структури, не видаляючи самого нейрона з карти.
     * @param structureId ідентифікатор структури.
     * @param neuronId ідентифікатор учасника.
     * @return {@code true}, якщо членство було вилучено.
     */
    public boolean removeMember(String structureId, String neuronId) {
        return model.removeStructureMember(structureId, neuronId);
    }

    /**
     * Змінює стан шухлядки наявної структури, не створюючи зайвих кроків, якщо значення не змінилося.
     *
     * @param structureId ідентифікатор нейрона-контейнера.
     * @param expanded {@code true}, якщо шухлядка має бути відкрита.
     * @return {@code true}, якщо стан наявної структури справді змінився.
     */
    public boolean setExpanded(String structureId, boolean expanded) {
        Optional<NeuronStructure> structure = find(structureId);
        if (structure.isEmpty() || structure.orElseThrow().isExpanded() == expanded) {
            return false;
        }
        structure.orElseThrow().setExpanded(expanded);
        return true;
    }
}
