package com.example.neuronmap.service;

import com.example.neuronmap.application.project.ProjectDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProjectCatalogServiceTest {

    @Test
    void catalogTracksNamesAndLastOpenedProjects(@TempDir Path tempDir) throws Exception {
        Path legacy = tempDir.resolve("neuronmap.db");
        Files.writeString(legacy, "legacy");

        ProjectCatalogService catalog = new ProjectCatalogService(legacy);
        catalog.ensureLegacyProject("NeuronMap");

        List<ProjectDescriptor> initial = catalog.listProjects();
        assertEquals(1, initial.size());
        assertEquals("NeuronMap", initial.get(0).name());

        ProjectDescriptor second = catalog.createTransientProject("Project");
        Files.createDirectories(second.databasePath().getParent());
        Files.writeString(second.databasePath(), "project");
        ProjectDescriptor persisted = catalog.register(second);
        catalog.markLastOpened(persisted);

        assertEquals(persisted.id(), catalog.lastOpenedProject().id());

        ProjectDescriptor renamed = catalog.rename(persisted, "Experiment");
        assertEquals("Experiment", renamed.name());
        assertEquals("Experiment", catalog.findProject(second.id()).orElseThrow().name());
        assertNotNull(catalog.findProject(second.id()).orElseThrow().modifiedAt());

        List<ProjectDescriptor> projects = catalog.listProjects();
        assertTrue(projects.stream().anyMatch(p -> "Experiment".equals(p.name())));

        catalog.delete(renamed);
        assertFalse(Files.exists(second.databasePath()));
        assertTrue(catalog.findProject(second.id()).isEmpty());
    }

    @Test
    void generatedNamesUseProjectThenFirstAvailableSuffix(@TempDir Path tempDir) throws Exception {
        Path legacy = tempDir.resolve("neuronmap.db");
        Files.writeString(legacy, "legacy");
        ProjectCatalogService catalog = new ProjectCatalogService(legacy);

        ProjectDescriptor first = catalog.createTransientProject("Project");
        materialize(catalog, first);
        ProjectDescriptor second = catalog.createTransientProject("Project");
        materialize(catalog, second);
        ProjectDescriptor third = catalog.createTransientProject("Project");

        assertEquals("Project", first.name());
        assertEquals("Project 1", second.name());
        assertEquals("Project 2", third.name());
    }

    @Test
    void renamedProjectMovesToTopByModificationTime(@TempDir Path tempDir) throws Exception {
        Path legacy = tempDir.resolve("neuronmap.db");
        Files.writeString(legacy, "legacy");
        ProjectCatalogService catalog = new ProjectCatalogService(legacy);
        catalog.ensureLegacyProject("Legacy");

        ProjectDescriptor first = catalog.createTransientProject("Project");
        ProjectDescriptor second = catalog.createTransientProject("Project");
        first = materialize(catalog, first);
        second = materialize(catalog, second);

        assertEquals(second.id(), catalog.listProjects().get(0).id());

        catalog.rename(first, "Renamed");
        assertEquals(first.id(), catalog.listProjects().get(0).id());
    }

    private static ProjectDescriptor materialize(
            ProjectCatalogService catalog,
            ProjectDescriptor project
    ) throws Exception {
        Files.createDirectories(project.databasePath().getParent());
        Files.writeString(project.databasePath(), "project-" + project.id());
        return catalog.register(project);
    }
}
