package com.example.neuronmap.persistence;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TransientProjectRepositoryTest {

    @Test
    void untouchedProjectDoesNotMaterialize(@TempDir Path tempDir) {
        Path path = tempDir.resolve("project.db");
        AtomicInteger materialized = new AtomicInteger();
        TransientProjectRepository repository =
                new TransientProjectRepository(path, materialized::incrementAndGet);
        NeuronMapModel model = new NeuronMapModel();

        repository.save(model, CameraState.defaultState());

        assertFalse(repository.isPersistent());
        assertFalse(Files.exists(path));
        assertEquals(0, materialized.get());

        repository.close();
    }

    @Test
    void firstBoardElementMaterializesExactlyOnce(@TempDir Path tempDir) {
        Path path = tempDir.resolve("project.db");
        AtomicInteger materialized = new AtomicInteger();
        TransientProjectRepository repository =
                new TransientProjectRepository(path, materialized::incrementAndGet);
        NeuronMapModel model = new NeuronMapModel();
        model.createNeuron(NeuronType.EXCITATORY, 20, 30);

        repository.save(model, CameraState.defaultState());
        repository.save(model, CameraState.defaultState());

        assertTrue(repository.isPersistent());
        assertTrue(Files.isRegularFile(path));
        assertEquals(1, materialized.get());

        repository.close();
        assertFalse(repository.isPersistent());
    }

    @Test
    void emptyBoardKeepsToolbarAndCameraChangesTransient(@TempDir Path tempDir) {
        Path path = tempDir.resolve("project.db");
        AtomicInteger materialized = new AtomicInteger();
        TransientProjectRepository repository =
                new TransientProjectRepository(path, materialized::incrementAndGet);
        NeuronMapModel model = new NeuronMapModel();
        CameraState camera = new CameraState(1.5, 20, -10);

        repository.save(model, camera);
        assertEquals(650.0, repository.loadSimulationTickMillis(650.0));

        repository.saveSimulationTickMillis(275.0);

        assertFalse(repository.isPersistent());
        assertFalse(Files.exists(path));
        assertEquals(camera, repository.loadCameraState());
        assertEquals(275.0, repository.loadSimulationTickMillis(650.0));

        model.createNeuron(NeuronType.EXCITATORY, 20, 30);
        repository.save(model, camera);

        assertTrue(repository.isPersistent());
        assertEquals(1, materialized.get());
        assertTrue(Files.isRegularFile(path));

        repository.close();
    }
}
