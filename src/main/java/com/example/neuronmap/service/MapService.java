package com.example.neuronmap.service;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.CameraState;
import com.example.neuronmap.persistence.MapRepository;

import java.nio.file.Path;
import java.util.Objects;

/** Persistence-facing service for the current map and editor camera state. */
public final class MapService {

    private final NeuronMapModel model;
    private MapRepository repository;

    public MapService(
            NeuronMapModel model,
            MapRepository repository
    ) {
        this.model = Objects.requireNonNull(model, "model");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public NeuronMapModel model() {
        return model;
    }

    public void load() {
        repository.loadInto(model);
    }

    public boolean isEmpty() {
        return model.isEmpty();
    }

    public boolean isPersistent() {
        return repository.isPersistent();
    }

    public CameraState loadCameraState() {
        return repository.loadCameraState();
    }

    public double loadSimulationTickMillis(double fallbackMillis) {
        return repository.loadSimulationTickMillis(fallbackMillis);
    }

    public Path databasePath() {
        return repository.databasePath();
    }

    public void save(EditorState state) {
        Objects.requireNonNull(state, "state");
        repository.save(
                model,
                new CameraState(
                        state.zoom(),
                        state.panX(),
                        state.panY()
                )
        );
    }

    public void saveSimulationTickMillis(double millis) {
        repository.saveSimulationTickMillis(millis);
    }

    /** Replaces the backing project without replacing the in-memory domain model. */
    public void switchRepository(MapRepository newRepository) {
        Objects.requireNonNull(newRepository, "newRepository");

        MapRepository oldRepository = repository;
        repository = newRepository;
        oldRepository.close();
    }

    public void close() {
        repository.close();
    }
}
