package com.example.neuronmap.service;

import com.example.neuronmap.model.NeuronGroup;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronMapModel;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Виконує операції над групами нейронів.
 */
public final class GroupService {

    private final NeuronMapModel model;

    /**
     * Повертає результат операції «група служба».
     *
     * @param model модель карти нейронів.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public GroupService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Створює обʼєкт із переданих даних «потрібні дані».
     *
     * @param memberIds значення, що визначає ідентифікатори для цієї операції.
     */
    public void create(Set<String> memberIds) {
        if (memberIds == null) {
            return;
        }
        model.createGroup(new LinkedHashSet<>(memberIds));
    }

    /**
     * Виконує операцію «відповідну операцію».
     *
     * @param memberIds значення, що визначає ідентифікатори для цієї операції.
     */
    public void ungroup(Set<String> memberIds) {
        if (memberIds == null) {
            return;
        }
        model.ungroup(new LinkedHashSet<>(memberIds));
    }

    /**
     * Переміщує обʼєкт «відповідну операцію» відповідно до переданого зміщення.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @param dx зміщення по горизонталі.
     *
     * @param dy зміщення по вертикалі.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
     * Повертає результат операції «відповідну операцію».
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronGroup containing(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return null;
        }
        return model.groupContaining(neuronId);
    }
}
