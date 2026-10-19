package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.map.TransientProjectRepository;
import com.example.neuronmap.project.ProjectDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє створення репозиторіїв фабрикою та використання її постійного делегата під час матеріалізації.
 */
final class DefaultMapRepositoryFactoryTest {

    /**
     * Перевіряє створення тимчасового репозиторію без негайного створення каталогу проєкту.
     *
     * @param tempDir тимчасовий каталог тесту.
     */
    @Test
    void createsTransientRepositoryWithoutMaterializingProject(@TempDir Path tempDir) {
        Path databasePath = tempDir.resolve("Project").resolve("project.db");
        ProjectDescriptor project = new ProjectDescriptor(
                "project-id",
                "Project",
                databasePath,
                null
        );
        MapRepositoryFactory factory = new DefaultMapRepositoryFactory();

        try (MapRepository repository = factory.createTransientProjectRepository(project)) {
            assertInstanceOf(TransientProjectRepository.class, repository);
            assertEquals(databasePath.toAbsolutePath().normalize(), repository.databasePath());
            assertFalse(Files.exists(databasePath.getParent()));
        }
    }

    /**
     * Перевіряє, що після появи даних фабрика створює постійний делегат і проєкт зберігається у SQLite.
     *
     * @param tempDir тимчасовий каталог тесту.
     */
    @Test
    void materializesTransientProjectThroughFactoryDelegate(@TempDir Path tempDir) {
        Path databasePath = tempDir.resolve("Project").resolve("project.db");
        ProjectDescriptor project = new ProjectDescriptor(
                "project-id",
                "Project",
                databasePath,
                null
        );
        MapRepositoryFactory factory = new DefaultMapRepositoryFactory();
        NeuronMapModel model = new NeuronMapModel();
        model.createNeuron(NeuronType.EXCITATORY, 20.0, 30.0);

        try (MapRepository repository = factory.createTransientProjectRepository(project)) {
            repository.save(model, new CameraState(1.25, 10.0, -5.0));

            assertTrue(repository.isPersistent());
            assertTrue(Files.isRegularFile(databasePath));
        }
    }

    /**
     * Перевіряє створення SQLite-репозиторію для наявного збереженого проєкту.
     *
     * @param tempDir тимчасовий каталог тесту.
     */
    @Test
    void createsSqliteRepositoryForSavedProject(@TempDir Path tempDir) {
        Path databasePath = tempDir.resolve("Project").resolve("project.db");
        ProjectDescriptor project = new ProjectDescriptor(
                "project-id",
                "Project",
                databasePath,
                Instant.now()
        );
        MapRepositoryFactory factory = new DefaultMapRepositoryFactory();

        try (MapRepository repository = factory.createSavedProjectRepository(project)) {
            assertInstanceOf(SqliteMapRepository.class, repository);
            assertEquals(databasePath.toAbsolutePath().normalize(), repository.databasePath());
            assertTrue(Files.isRegularFile(databasePath));
        }
    }
}
