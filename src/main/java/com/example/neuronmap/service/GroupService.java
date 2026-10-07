package com.example.neuronmap.service;

import com.example.neuronmap.model.NeuronGroup;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronMapModel;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Business operations for neuron groups. */
public final class GroupService {

    private final NeuronMapModel model;

    public GroupService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    public void create(Set<String> memberIds) {
        if (memberIds == null) {
            return;
        }
        model.createGroup(new LinkedHashSet<>(memberIds));
    }

    public void ungroup(Set<String> memberIds) {
        if (memberIds == null) {
            return;
        }
        model.ungroup(new LinkedHashSet<>(memberIds));
    }

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

    public NeuronGroup containing(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return null;
        }
        return model.groupContaining(neuronId);
    }
}
