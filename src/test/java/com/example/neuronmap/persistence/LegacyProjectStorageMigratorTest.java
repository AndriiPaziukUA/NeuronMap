package com.example.neuronmap.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє сумісність перенесення даних зі старого розташування.
 */
final class LegacyProjectStorageMigratorTest {

    /**
     * Перевіряє очікувану поведінку: переносить старий формат і база даних.
     *
     * @param tempDir значення, що визначає відповідну операцію для цієї операції.
     */
    @Test
    void migratesLegacyCatalogAndRootDatabase(@TempDir Path tempDir)
            throws Exception {
        Path storage = tempDir.resolve("NeuronMap");
        Path legacyRoot = tempDir.resolve("legacy");
        Path legacyProjects = legacyRoot.resolve("neuronmap-projects");
        Path legacyCatalog = legacyRoot.resolve("neuronmap-projects.properties");
        Path legacyDatabase = legacyRoot.resolve("neuronmap.db");
        Path experimentDatabase = legacyProjects.resolve("abc.db");
        Files.createDirectories(legacyProjects);
        Files.writeString(experimentDatabase, "experiment");
        Files.writeString(legacyDatabase, "legacy");

        Properties properties = new Properties();
        properties.setProperty("project.abc.name", "Experiment");
        properties.setProperty("project.abc.file", "abc.db");
        properties.setProperty("project.abc.modified", "1");
        properties.setProperty("last_project_id", "abc");
        try (var output = Files.newOutputStream(legacyCatalog)) {
            properties.store(output, "legacy");
        }

        GlobalSettingsStore settings = new GlobalSettingsStore(
                storage.resolve("global.properties")
        );
        LegacyProjectStorageMigrator.migrateIfNeeded(
                storage,
                legacyDatabase,
                legacyProjects,
                legacyCatalog,
                "NeuronMap",
                settings
        );

        assertTrue(Files.isRegularFile(
                storage.resolve("Experiment").resolve("project.db")
        ));
        assertTrue(Files.isRegularFile(
                storage.resolve("NeuronMap").resolve("project.db")
        ));
        assertEquals("Experiment", settings.load("last_project_name"));
        assertTrue(Files.exists(storage.resolve(".legacy-storage-migrated")));
    }
}
