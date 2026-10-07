package com.example.neuronmap.application;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.CameraState;
import com.example.neuronmap.persistence.MapRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class NeuronMapApplicationServiceTest {

    @Test
    void createsNeuronAndSavesCameraState() {
        FakeRepository repository = new FakeRepository();
        NeuronMapApplicationService service =
                new NeuronMapApplicationService(new NeuronMapModel(), repository);

        service.load();

        Neuron neuron = service.createNeuron(
                NeuronType.EXCITATORY,
                100,
                200
        );

        NeuronMapModel model = service.model();

        assertNotNull(neuron);
        assertSame(model, service.model());
        assertEquals(1, model.neurons().size());

        EditorState state = new EditorState(1.5, -10, 25);
        service.save(state);

        assertNotNull(repository.savedModel);
        assertEquals(
                new CameraState(1.5, -10, 25),
                repository.savedCamera
        );
    }

    @Test
    void delegatesSimulationTickPersistence() {
        FakeRepository repository = new FakeRepository();
        NeuronMapApplicationService service =
                new NeuronMapApplicationService(new NeuronMapModel(), repository);

        assertEquals(123.0, service.loadSimulationTickMillis(123.0));

        service.saveSimulationTickMillis(75.0);

        assertEquals(75.0, repository.savedSimulationTickMillis);
    }

    private static final class FakeRepository implements MapRepository {

        private NeuronMapModel savedModel;
        private CameraState savedCamera;
        private double savedSimulationTickMillis = Double.NaN;

        @Override
        public Path databasePath() {
            return Path.of("test.db");
        }

        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return fallbackMillis;
        }

        @Override
        public void saveSimulationTickMillis(double millis) {
            savedSimulationTickMillis = millis;
        }

        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        @Override
        public void loadInto(NeuronMapModel model) {
        }

        @Override
        public void save(
                NeuronMapModel model,
                CameraState cameraState
        ) {
            savedModel = model;
            savedCamera = cameraState;
        }

        @Override
        public void close() {
        }
    }
}
