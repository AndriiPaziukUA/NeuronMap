package com.example.neuronmap.service;

import com.example.neuronmap.model.NeuronGroup;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronMapModel;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Керує групами нейронів і забезпечує переміщення всіх учасників групи як одного об’єкта.
 */
public final class GroupService {

    private final NeuronMapModel model;

    /**
     * Створює екземпляр GroupService та зберігає передані залежності, потрібні для його роботи.
     *
     * @param model модель карти нейронів.
     */
    public GroupService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Створює  з переданих параметрів.
     *
     * @param memberIds ідентифікатори нейронів, які мають увійти до групи.
     */
    public void create(Set<String> memberIds) {
        if (memberIds == null) {
            return;
        }
        model.createGroup(new LinkedHashSet<>(memberIds));
    }

    /**
     * Вилучає вказані нейрони з групи та видаляє групу, якщо після цього вона спорожніла.
     *
     * @param memberIds ідентифікатори нейронів, які мають увійти до групи.
     */
    public void ungroup(Set<String> memberIds) {
        if (memberIds == null) {
            return;
        }
        model.ungroup(new LinkedHashSet<>(memberIds));
    }

    /**
     * Переміщує всі нейрони групи на однакове зміщення, якщо нейрон належить групі.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param dx значення «dx», яке використовується в цьому методі.
     * @param dy значення «dy», яке використовується в цьому методі.
     */
    public boolean moveContaining(String neuronId, double dx, double dy) {
        NeuronGroup group = containing(neuronId);
        if (group == null) {
            return false;
        }

        boolean changed = false;
        for (String memberId : group.memberIds()) {
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
     */
    public NeuronGroup containing(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return null;
        }
        return model.groupContaining(neuronId);
    }
}
