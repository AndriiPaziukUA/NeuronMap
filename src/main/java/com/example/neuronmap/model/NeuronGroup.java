package com.example.neuronmap.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class NeuronGroup {

    private final String id;
    private final Set<String> memberIds;

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

    public String id() {
        return id;
    }

    /**
     * Read-only view. Group membership must be changed through NeuronMapModel.
     */
    public Set<String> memberIds() {
        return Collections.unmodifiableSet(
                memberIds
        );
    }

    void removeMember(String neuronId) {
        memberIds.remove(neuronId);
    }

    void removeMembers(Collection<String> neuronIds) {
        memberIds.removeAll(neuronIds);
    }
}
