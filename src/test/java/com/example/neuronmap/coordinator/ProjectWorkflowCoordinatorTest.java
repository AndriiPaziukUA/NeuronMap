package com.example.neuronmap.coordinator;

import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.project.FileSystemProjectCatalogRepository;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.map.TransientProjectRepository;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.ProjectCatalogService;
import com.example.neuronmap.testsupport.TransientRepositoryTestSupport;
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

    /**
     * Перевіряє, що координатор редактора більше не оголошує сценарії CRUD для проєктів.
     */
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

    /**
     * Перевіряє створення тимчасового проєкту й передавання його репозиторію активатору.
     *
     * @param tempDir тимчасовий каталог тесту.
     */
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
                testRepositoryFactory(),
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

    /**
     * Перевіряє, що null і повторне відкриття активного проєкту не запускають перемикання редактора.
     *
     * @param tempDir тимчасовий каталог тесту.
     */
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
                testRepositoryFactory(),
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

    /**
     * Перевіряє перейменування неактивного проєкту без збереження поточної карти.
     *
     * @param tempDir тимчасовий каталог тесту.
     * @throws Exception якщо не вдається підготувати тестові файли.
     */
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
                testRepositoryFactory(),
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

    /**
     * Перевіряє видалення неактивного проєкту без перемикання редактора.
     *
     * @param tempDir тимчасовий каталог тесту.
     * @throws Exception якщо не вдається підготувати тестові файли.
     */
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
                testRepositoryFactory(),
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

    /**
     * Створює тестову фабрику, яка надає тимчасове сховище й не відкриває SQLite до матеріалізації.
     *
     * @return фабрика репозиторіїв для ізольованих тестів сценаріїв проєктів.
     */
    private static MapRepositoryFactory testRepositoryFactory() {
        return new MapRepositoryFactory() {
            /**
             * Створює відкладений репозиторій тимчасового проєкту.
             *
             * @param project опис тимчасового проєкту.
             * @return тимчасовий репозиторій.
             */
            @Override
            public MapRepository createTransientProjectRepository(ProjectDescriptor project) {
                return TransientRepositoryTestSupport.create(project.databasePath());
            }

            /**
             * Відхиляє запит на збережений репозиторій, не потрібний у цьому тесті.
             *
             * @param project опис збереженого проєкту.
             * @return метод не повертає значення, оскільки сценарій не підтримується цим тестом.
             */
            @Override
            public MapRepository createSavedProjectRepository(ProjectDescriptor project) {
                throw new UnsupportedOperationException("Saved repository is not needed in this test");
            }
        };
    }

    /**
     * Створює службу каталогу на основі файлового адаптера для тестового каталогу.
     *
     * @param root кореневий каталог тестових проєктів.
     * @return службу каталогу, що використовує тимчасову файлову інфраструктуру.
     */
    private static ProjectCatalogService catalog(Path root) {
        return new ProjectCatalogService(new FileSystemProjectCatalogRepository(
                root,
                new GlobalSettingsStore(root.resolve("neuronmap-global.properties"))
        ));
    }

    /**
     * Створює службу карти з тимчасовим репозиторієм, який отримано через стандартну фабрику.
     *
     * @param databasePath шлях до бази даних активної карти.
     * @return службу карти.
     */
    private static MapService mapService(Path databasePath) {
        return new MapService(
                new NeuronMapModel(),
                TransientRepositoryTestSupport.create(databasePath)
        );
    }
}
