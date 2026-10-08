package com.example.neuronmap.persistence;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TransientProjectRepositoryTest {

    @Test
    void emptyProjectDoesNotCreateFolder(@TempDir Path tempDir) {
        Path path = tempDir.resolve("Project").resolve("project.db");
        TransientProjectRepository repository =
                new TransientProjectRepository(path);

        repository.save(new NeuronMapModel(), CameraState.defaultState());

        assertFalse(Files.exists(path.getParent()));
        assertFalse(repository.isPersistent());
        repository.close();
    }

    @Test
    void firstElementMaterializesProjectAndCleansJunk(@TempDir Path tempDir)
            throws Exception {
        Path directory = tempDir.resolve("Project");
        Path path = directory.resolve("project.db");
        Files.createDirectories(directory.resolve("old"));
        Files.writeString(directory.resolve("junk.txt"), "junk");
        Files.writeString(directory.resolve("old").resolve("junk.txt"), "junk");

        TransientProjectRepository repository =
                new TransientProjectRepository(path);
        NeuronMapModel model = new NeuronMapModel();
        model.createNeuron(NeuronType.EXCITATORY, 20.0, 30.0);

        repository.save(model, new CameraState(1.5, 20, -10));

        assertTrue(repository.isPersistent());
        assertTrue(Files.isRegularFile(path));
        assertFalse(Files.exists(directory.resolve("junk.txt")));
        assertFalse(Files.exists(directory.resolve("old")));
        repository.close();
    }

    @Test
    void emptyProjectRetainsPendingToolbarAndCameraSettings(@TempDir Path tempDir) {
        Path path = tempDir.resolve("Project").resolve("project.db");
        TransientProjectRepository repository =
                new TransientProjectRepository(path);

        CameraState camera = new CameraState(1.5, 20, -10);
        repository.save(new NeuronMapModel(), camera);
        repository.saveSimulationTickMillis(275.0);

        assertEquals(camera, repository.loadCameraState());
        assertEquals(275.0, repository.loadSimulationTickMillis(650.0));
        assertFalse(repository.isPersistent());
        repository.close();
    }

    @Test
    void existingProjectDatabaseIsNeverOverwritten(@TempDir Path tempDir)
            throws Exception {
        Path directory = tempDir.resolve("Project");
        Path path = directory.resolve("project.db");
        Files.createDirectories(directory);
        Files.writeString(path, "existing");

        TransientProjectRepository repository =
                new TransientProjectRepository(path);
        NeuronMapModel model = new NeuronMapModel();
        model.createNeuron(NeuronType.EXCITATORY, 1, 2);

        org.junit.jupiter.api.Assertions.assertThrows(
                PersistenceException.class,
                () -> repository.save(model, CameraState.defaultState())
        );
        assertEquals("existing", Files.readString(path));
        repository.close();
    }
}
