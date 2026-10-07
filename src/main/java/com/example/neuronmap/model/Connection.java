package com.example.neuronmap.model;

import java.util.Objects;

/**
 * Directed semantic connection: source neuron -> target neuron.
 */
public record Connection(
        String id,
        String sourceId,
        String targetId
) {
    public Connection {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Connection id must not be blank."
            );
        }

        sourceId = Objects.requireNonNull(
                sourceId,
                "sourceId"
        );
        targetId = Objects.requireNonNull(
                targetId,
                "targetId"
        );

        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException(
                    "A neuron cannot connect to itself."
            );
        }
    }
}
