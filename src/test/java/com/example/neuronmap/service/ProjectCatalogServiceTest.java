package com.example.neuronmap.service;

import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє керування каталогом проєктів, зокрема унікальність назв і операції над метаданими проєктів.
 */
final class ProjectCatalogServiceTest {

    @Test
    void listsOnlyDirectoriesContainingProjectDatabase(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        GlobalSettingsStore settings = new GlobalSettingsStore(
                root.resolve("neuronmap-global.properties")
        );
        ProjectCatalogService catalog = new ProjectCatalogService(root, settings);

        Files.createDirectories(root.resolve("Visible"));
        Files.writeString(root.resolve("Visible").resolve("project.db"), "db");
        Files.createDirectories(root.resolve("Empty"));
        Files.writeString(root.resolve("ignored.txt"), "ignored");

        List<ProjectDescriptor> projects = catalog.listProjects();

        assertEquals(1, projects.size());
        assertEquals("Visible", projects.getFirst().name());
        assertFalse(projects.stream().anyMatch(
                project -> project.name().equals("Empty")
        ));
    }

    @Test
    void generatedNamesReuseFoldersWithoutProjectDatabase(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        ProjectCatalogService catalog = new ProjectCatalogService(
                root,
                new GlobalSettingsStore(root.resolve("settings.properties"))
        );

        Files.createDirectories(root.resolve("Project"));
        Files.writeString(root.resolve("Project").resolve("junk.txt"), "junk");

        ProjectDescriptor project = catalog.createTransientProject("Project");

        assertEquals("Project", project.name());
        assertTrue(Files.exists(root.resolve("Project").resolve("junk.txt")));
        assertFalse(catalog.listProjects().stream()
                .anyMatch(saved -> saved.name().equals("Project")));
    }

    @Test
    void generatedNamesUseFirstAvailableSuffix(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        ProjectCatalogService catalog = new ProjectCatalogService(
                root,
                new GlobalSettingsStore(root.resolve("settings.properties"))
        );
        Files.createDirectories(root.resolve("Project"));
        Files.writeString(root.resolve("Project/project.db"), "1");
        Files.createDirectories(root.resolve("Project 1"));
        Files.writeString(root.resolve("Project 1/project.db"), "2");

        ProjectDescriptor next = catalog.createTransientProject("Project");

        assertEquals("Project 2", next.name());
    }

    @Test
    void renameReusesAndCleansDirectoryWithoutProjectDatabase(
            @TempDir Path tempDir
    ) throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        ProjectCatalogService catalog = new ProjectCatalogService(
                root,
                new GlobalSettingsStore(root.resolve("settings.properties"))
        );
        Path source = root.resolve("Project");
        Path target = root.resolve("Experiment");
        Files.createDirectories(source);
        Files.writeString(source.resolve("project.db"), "db");
        Files.createDirectories(target);
        Files.writeString(target.resolve("junk.txt"), "junk");

        ProjectDescriptor renamed = catalog.rename(
                catalog.listProjects().getFirst(),
                "Experiment"
        );

        assertEquals("Experiment", renamed.name());
        assertTrue(Files.isRegularFile(target.resolve("project.db")));
        assertFalse(Files.exists(target.resolve("junk.txt")));
    }

    @Test
    void renameMovesTheProjectFolderAndKeepsDatabase(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        ProjectCatalogService catalog = new ProjectCatalogService(
                root,
                new GlobalSettingsStore(root.resolve("settings.properties"))
        );
        Path source = root.resolve("Project");
        Files.createDirectories(source);
        Files.writeString(source.resolve("project.db"), "db");

        ProjectDescriptor project = catalog.listProjects().getFirst();
        ProjectDescriptor renamed = catalog.rename(project, "Experiment");

        assertEquals("Experiment", renamed.name());
        assertTrue(Files.isRegularFile(
                root.resolve("Experiment").resolve("project.db")
        ));
        assertFalse(Files.exists(source));
    }

    @Test
    void externalProjectIsDiscoverableWithoutCatalogMetadata(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        ProjectCatalogService catalog = new ProjectCatalogService(
                root,
                new GlobalSettingsStore(root.resolve("settings.properties"))
        );
        Path database = root.resolve("Copied").resolve("project.db");
        Files.createDirectories(database.getParent());
        Files.writeString(database, "copied");

        assertEquals("Copied", catalog.listProjects().getFirst().name());
    }

    @Test
    void projectsAreOrderedByModificationTime(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        ProjectCatalogService catalog = new ProjectCatalogService(
                root,
                new GlobalSettingsStore(root.resolve("settings.properties"))
        );
        Path older = root.resolve("Older").resolve("project.db");
        Path newer = root.resolve("Newer").resolve("project.db");
        Files.createDirectories(older.getParent());
        Files.createDirectories(newer.getParent());
        Files.writeString(older, "old");
        Thread.sleep(20);
        Files.writeString(newer, "new");

        assertEquals("Newer", catalog.listProjects().getFirst().name());
    }

    @Test
    void lastOpenedProjectIsStoredOutsideProjectFiles(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        GlobalSettingsStore settings = new GlobalSettingsStore(
                root.resolve("settings.properties")
        );
        ProjectCatalogService catalog = new ProjectCatalogService(root, settings);
        Path database = root.resolve("Experiment").resolve("project.db");
        Files.createDirectories(database.getParent());
        Files.writeString(database, "db");
        ProjectDescriptor project = catalog.listProjects().getFirst();

        catalog.markLastOpened(project);

        ProjectCatalogService reloaded = new ProjectCatalogService(root, settings);
        assertEquals("Experiment", reloaded.lastOpenedProject().name());
    }
}
