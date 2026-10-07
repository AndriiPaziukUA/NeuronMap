package com.example.neuronmap.persistence;

import com.example.neuronmap.model.NeuronMapModel;

import java.nio.file.Path;

public interface MapRepository extends AutoCloseable {

    Path databasePath();

    CameraState loadCameraState();

    double loadSimulationTickMillis(double fallbackMillis);

    void loadInto(NeuronMapModel model);

    void saveSimulationTickMillis(double millis);

    void save(
            NeuronMapModel model,
            CameraState cameraState
    );

    @Override
    void close();
}
