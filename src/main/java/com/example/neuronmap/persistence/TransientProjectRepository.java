package com.example.neuronmap.persistence;

import com.example.neuronmap.model.NeuronMapModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Keeps a new project in memory until its first real board element is persisted. */
public final class TransientProjectRepository implements MapRepository {

    private final Path databasePath;
    private final Runnable onMaterialized;
    private MapRepository delegate;
    private CameraState pendingCameraState = CameraState.defaultState();
    private Double pendingSimulationTickMillis;

    public TransientProjectRepository(
            Path databasePath,
            Runnable onMaterialized
    ) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
        this.onMaterialized = Objects.requireNonNull(
                onMaterialized,
                "onMaterialized"
        );
    }

    @Override
    public Path databasePath() {
        return databasePath;
    }

    @Override
    public boolean isPersistent() {
        return delegate != null && delegate.isPersistent();
    }

    @Override
    public CameraState loadCameraState() {
        return delegate == null
                ? pendingCameraState
                : delegate.loadCameraState();
    }

    @Override
    public double loadSimulationTickMillis(double fallbackMillis) {
        if (delegate == null) {
            return pendingSimulationTickMillis == null
                    ? fallbackMillis
                    : pendingSimulationTickMillis;
        }
        return delegate.loadSimulationTickMillis(fallbackMillis);
    }

    @Override
    public void loadInto(NeuronMapModel model) {
        if (delegate == null) {
            model.clear();
            return;
        }
        delegate.loadInto(model);
    }

    @Override
    public void saveSimulationTickMillis(double millis) {
        if (delegate == null) {
            pendingSimulationTickMillis = millis;
            return;
        }
        delegate.saveSimulationTickMillis(millis);
    }

    @Override
    public void save(
            NeuronMapModel model,
            CameraState cameraState
    ) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        pendingCameraState = Objects.requireNonNull(
                cameraState,
                "cameraState"
        );

        if (delegate == null && model.isEmpty()) {
            return;
        }

        boolean wasTransient = delegate == null;
        MapRepository active = materialize();
        active.save(model, pendingCameraState);

        if (pendingSimulationTickMillis != null) {
            active.saveSimulationTickMillis(pendingSimulationTickMillis);
        }

        if (wasTransient) {
            notifyMaterialized();
        }
    }

    @Override
    public void close() {
        if (delegate != null) {
            delegate.close();
            delegate = null;
        }
        pendingCameraState = CameraState.defaultState();
        pendingSimulationTickMillis = null;
    }

    private MapRepository materialize() {
        if (delegate != null) {
            return delegate;
        }

        try {
            delegate = new SqliteMapRepository(databasePath);
            return delegate;
        } catch (RuntimeException exception) {
            deleteDatabaseQuietly();
            throw exception;
        }
    }

    private void notifyMaterialized() {
        onMaterialized.run();
    }

    private void deleteDatabaseQuietly() {
        try {
            Files.deleteIfExists(databasePath);
        } catch (IOException ignored) {
            // Preserve the original persistence failure.
        }
    }
}
