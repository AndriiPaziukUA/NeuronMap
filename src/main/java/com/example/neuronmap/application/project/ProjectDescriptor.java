package com.example.neuronmap.application.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/** Immutable metadata describing a saved or not-yet-persisted project. */
public record ProjectDescriptor(
        String id,
        String name,
        Path databasePath,
        Instant modifiedAt
) {
    public ProjectDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("project id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("project name must not be blank");
        }
        databasePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
    }

    public boolean isPersisted() {
        return modifiedAt != null;
    }
}
