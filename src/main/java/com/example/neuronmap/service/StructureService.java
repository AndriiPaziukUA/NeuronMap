package com.example.neuronmap.service;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronStructure;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Надає операції читання та зміни членства структур, делегуючи перевірку цілісності моделі карти.
 * Служба не керує поданнями JavaFX і не виконує операції збереження безпосередньо.
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

    /**
     * Повертає всі структури моделі.
     *
     * @return незмінна колекція структур.
     */
    public Collection<NeuronStructure> structures() {
        return model.structures();
    }

    /**
     * Знаходить структуру за ідентифікатором її структурного елемента.
     *
     * @param structureId ідентифікатор структури.
     * @return структура або порожній результат, якщо ідентифікатор некоректний чи структури немає.
     */
    public Optional<NeuronStructure> find(String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(model.structure(structureId));
    }

    /**
     * Знаходить структуру, яка безпосередньо містить указаний нейрон.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return структура-власник або порожній результат, якщо нейрон не міститься в структурі.
     */
    public Optional<NeuronStructure> containing(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(model.structureContaining(neuronId));
    }

    /**
     * Додає нейрон до структури, якщо обидва елементи існують і не порушуються правила вкладеності.
     *
     * @param structureId ідентифікатор структури-приймача.
     * @param neuronId ідентифікатор нейрона, який потрібно додати.
     * @return {@code true}, якщо учасника додано; інакше {@code false}.
     */
    public boolean addMember(String structureId, String neuronId) {
        return model.addStructureMember(structureId, neuronId);
    }

    /**
     * Вилучає нейрон зі структури, не видаляючи самого нейрона з карти.
     *
     * @param structureId ідентифікатор структури.
     * @param neuronId ідентифікатор учасника, якого потрібно вилучити.
     * @return {@code true}, якщо учасника вилучено; інакше {@code false}.
     */
    public boolean removeMember(String structureId, String neuronId) {
        return model.removeStructureMember(structureId, neuronId);
    }
}
