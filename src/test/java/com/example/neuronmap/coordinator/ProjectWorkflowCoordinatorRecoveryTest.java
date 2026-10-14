package com.example.neuronmap.coordinator;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.project.FileSystemProjectCatalogRepository;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.map.TransientProjectRepository;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.ProjectCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє діагностичну поведінку координатора, коли перейменування й відновлення сховища завершуються помилками.
 */
final class ProjectWorkflowCoordinatorRecoveryTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Переконується, що помилка повторного підключення не приховує первісну помилку перейменування проєкту.
     *
     * @throws IOException якщо не вдається створити тестові файли збережених проєктів.
     */
    @Test
    void retainsRenameFailureAndAttachesRepositoryRecoveryFailure() throws IOException {
        Path projectsDirectory = temporaryDirectory.resolve("projects");
        ProjectDescriptor sourceProject = createSavedProject(projectsDirectory, "Source");

        ProjectCatalogService catalog = new ProjectCatalogService(
                new FileSystemProjectCatalogRepository(
                        projectsDirectory,
                        new GlobalSettingsStore(temporaryDirectory.resolve("global.properties"))
                )
        );
        ProjectDescriptor activeProject = catalog.listProjects().stream()
                .filter(project -> project.name().equals(sourceProject.name()))
                .findFirst()
                .orElseThrow();
        AtomicReference<ProjectDescriptor> currentProject = new AtomicReference<>(activeProject);
        MapService mapService = new MapService(
                new NeuronMapModel(),
                new TransientProjectRepository(temporaryDirectory.resolve("active/project.db"))
        );
        RuntimeException originalFailure = new IllegalStateException("Project state update failed");
        RuntimeException recoveryFailure = new IllegalStateException("Repository recovery failed");
        AtomicInteger savedRepositoryCreations = new AtomicInteger();
        MapRepositoryFactory repositoryFactory = new MapRepositoryFactory() {
            /**
             * Створює тимчасове сховище для задоволення контракту фабрики в цьому тесті.
             *
             * @param project опис тимчасового проєкту.
             * @return тимчасове сховище з відповідним шляхом до бази даних.
             */
            @Override
            public MapRepository createTransientProjectRepository(ProjectDescriptor project) {
                return new TransientProjectRepository(project.databasePath());
            }

            /**
             * Імітує помилку повторного підключення попереднього проєкту.
             *
             * @param project опис проєкту, сховище якого потрібно відновити.
             * @return метод завжди завершується винятком, тому значення не повертається.
             */
            @Override
            public MapRepository createSavedProjectRepository(ProjectDescriptor project) {
                if (savedRepositoryCreations.getAndIncrement() == 0) {
                    return new TransientProjectRepository(project.databasePath());
                }
                throw recoveryFailure;
            }
        };
        ProjectWorkflowCoordinator coordinator = new ProjectWorkflowCoordinator(
                catalog,
                new LocalizationService(Locale.ENGLISH),
                mapService,
                repositoryFactory,
                currentProject::get,
                project -> {
                    throw originalFailure;
                },
                () -> { },
                () -> { },
                (project, repository, loadExisting) -> { }
        );

        try {
            IllegalStateException renameFailure = assertThrows(
                    IllegalStateException.class,
                    () -> coordinator.renameProject(activeProject, "Renamed")
            );

            assertSame(originalFailure, renameFailure);
            assertSame(recoveryFailure, renameFailure.getSuppressed()[0]);
        } finally {
            mapService.close();
        }
    }

    /**
     * Створює тестовий збережений проєкт, який розпізнає файловий каталог.
     *
     * @param root кореневий каталог проєктів.
     * @param name назва проєкту.
     * @return опис створеного проєкту.
     * @throws IOException якщо не вдається створити каталог або файл бази даних.
     */
    private ProjectDescriptor createSavedProject(Path root, String name) throws IOException {
        Path projectDirectory = root.resolve(name);
        Files.createDirectories(projectDirectory);
        Files.createFile(projectDirectory.resolve("project.db"));
        return new ProjectDescriptor(
                projectDirectory.toString(),
                name,
                projectDirectory.resolve("project.db"),
                Files.getLastModifiedTime(projectDirectory.resolve("project.db")).toInstant()
        );
    }
}
