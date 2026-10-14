package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.map.TransientProjectRepository;
import com.example.neuronmap.project.ProjectDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє створення фабрикою репозиторіїв для тимчасових і збережених проєктів.
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
     * Перевіряє створення SQLite-репозиторію для збереженого проєкту.
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
                java.time.Instant.now()
        );
        MapRepositoryFactory factory = new DefaultMapRepositoryFactory();

        try (MapRepository repository = factory.createSavedProjectRepository(project)) {
            assertInstanceOf(SqliteMapRepository.class, repository);
            assertEquals(databasePath.toAbsolutePath().normalize(), repository.databasePath());
            assertTrue(Files.isRegularFile(databasePath));
        }
    }
}
