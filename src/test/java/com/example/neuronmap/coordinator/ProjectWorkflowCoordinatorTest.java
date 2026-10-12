package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.TransientProjectRepository;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.ProjectCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє сценарії керування проєктами, винесені з координатора редактора.
 */
final class ProjectWorkflowCoordinatorTest {

    @Test
    void editorCoordinatorNoLongerDeclaresProjectCrudWorkflows() {
        java.util.Set<String> methodNames = java.util.Arrays.stream(
                MapEditorCoordinator.class.getDeclaredMethods()
        ).map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet());

        assertFalse(methodNames.contains("createNewProject"));
        assertFalse(methodNames.contains("openProject"));
        assertFalse(methodNames.contains("renameProject"));
        assertFalse(methodNames.contains("deleteProject"));
    }

    @Test
    void createsTransientProjectAndDelegatesActivation(@TempDir Path tempDir) {
        ProjectCatalogService catalog = catalog(tempDir.resolve("projects"));
        MapService mapService = mapService(tempDir.resolve("active/project.db"));
        AtomicReference<ProjectDescriptor> current = new AtomicReference<>();
        AtomicReference<ProjectDescriptor> activated = new AtomicReference<>();
        AtomicReference<MapRepository> repository = new AtomicReference<>();
        AtomicBoolean loadExisting = new AtomicBoolean(true);
        AtomicInteger prepared = new AtomicInteger();

        ProjectWorkflowCoordinator coordinator = new ProjectWorkflowCoordinator(
                catalog,
                new LocalizationService(Locale.ENGLISH),
                mapService,
                current::get,
                current::set,
                prepared::incrementAndGet,
                () -> { },
                (project, selectedRepository, load) -> {
                    activated.set(project);
                    repository.set(selectedRepository);
                    loadExisting.set(load);
                    current.set(project);
                }
        );

        coordinator.createNewProject();

        assertEquals(1, prepared.get());
        assertNotNull(activated.get());
        assertEquals("Project", activated.get().name());
        assertFalse(activated.get().isPersisted());
        assertFalse(loadExisting.get());
        assertInstanceOf(TransientProjectRepository.class, repository.get());

        repository.get().close();
        mapService.close();
    }

    @Test
    void ignoresNullAndAlreadyActiveProjects(@TempDir Path tempDir) {
        ProjectCatalogService catalog = catalog(tempDir.resolve("projects"));
        MapService mapService = mapService(tempDir.resolve("active/project.db"));
        ProjectDescriptor current = new ProjectDescriptor(
                "active",
                "Active",
                tempDir.resolve("active/project.db"),
                null
        );
        AtomicReference<ProjectDescriptor> active = new AtomicReference<>(current);
        AtomicInteger prepared = new AtomicInteger();
        AtomicInteger activations = new AtomicInteger();
        ProjectWorkflowCoordinator coordinator = new ProjectWorkflowCoordinator(
                catalog,
                new LocalizationService(Locale.ENGLISH),
                mapService,
                active::get,
                active::set,
                prepared::incrementAndGet,
                () -> { },
                (project, repository, load) -> activations.incrementAndGet()
        );

        coordinator.openProject(null);
        coordinator.openProject(current);

        assertEquals(0, prepared.get());
        assertEquals(0, activations.get());
        mapService.close();
    }

    @Test
    void renamesInactiveProjectThroughCatalog(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("projects");
        Path database = root.resolve("Old").resolve("project.db");
        Files.createDirectories(database.getParent());
        Files.writeString(database, "project-data");
        ProjectCatalogService catalog = catalog(root);
        ProjectDescriptor existing = catalog.listProjects().getFirst();
        ProjectDescriptor active = new ProjectDescriptor(
                "active",
                "Active",
                tempDir.resolve("active/project.db"),
                null
        );
        MapService mapService = mapService(tempDir.resolve("active/project.db"));
        AtomicReference<ProjectDescriptor> current = new AtomicReference<>(active);
        AtomicInteger saves = new AtomicInteger();
        ProjectWorkflowCoordinator coordinator = new ProjectWorkflowCoordinator(
                catalog,
                new LocalizationService(Locale.ENGLISH),
                mapService,
                current::get,
                current::set,
                () -> { },
                saves::incrementAndGet,
                (project, repository, load) -> { }
        );

        ProjectDescriptor renamed = coordinator.renameProject(existing, "Renamed");

        assertEquals("Renamed", renamed.name());
        assertTrue(Files.isRegularFile(root.resolve("Renamed/project.db")));
        assertEquals(0, saves.get());
        mapService.close();
    }

    @Test
    void deletesInactiveProjectWithoutSwitchingEditor(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("projects");
        Path database = root.resolve("ToDelete").resolve("project.db");
        Files.createDirectories(database.getParent());
        Files.writeString(database, "project-data");
        ProjectCatalogService catalog = catalog(root);
        ProjectDescriptor toDelete = catalog.listProjects().getFirst();
        ProjectDescriptor active = new ProjectDescriptor(
                "active",
                "Active",
                tempDir.resolve("active/project.db"),
                null
        );
        MapService mapService = mapService(tempDir.resolve("active/project.db"));
        AtomicReference<ProjectDescriptor> current = new AtomicReference<>(active);
        AtomicInteger prepared = new AtomicInteger();
        AtomicInteger activations = new AtomicInteger();
        ProjectWorkflowCoordinator coordinator = new ProjectWorkflowCoordinator(
                catalog,
                new LocalizationService(Locale.ENGLISH),
                mapService,
                current::get,
                current::set,
                prepared::incrementAndGet,
                () -> { },
                (project, repository, load) -> activations.incrementAndGet()
        );

        coordinator.deleteProject(toDelete);

        assertFalse(Files.exists(database.getParent()));
        assertEquals(0, prepared.get());
        assertEquals(0, activations.get());
        assertEquals("Active", current.get().name());
        mapService.close();
    }

    private static ProjectCatalogService catalog(Path root) {
        return new ProjectCatalogService(
                root,
                new GlobalSettingsStore(root.resolve("neuronmap-global.properties"))
        );
    }

    private static MapService mapService(Path databasePath) {
        return new MapService(
                new NeuronMapModel(),
                new TransientProjectRepository(databasePath)
        );
    }
}
