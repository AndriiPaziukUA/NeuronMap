package com.example.neuronmap.app;

import com.example.neuronmap.persistence.project.FileSystemProjectCatalogRepository;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.ProjectCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє вибір початкового проєкту та узгоджене завершення життєвого циклу головного вікна.
 */
final class NeuronMapAppTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Перевіряє, що під час запуску перевага надається останньому відкритому доступному проєкту.
     *
     * @throws IOException якщо тест не може підготувати файл збереженого проєкту.
     */
    @Test
    void prefersLastOpenedProject() throws IOException {
        Path storageDirectory = temporaryDirectory.resolve("projects");
        createSavedProject(storageDirectory, "Last opened");
        ProjectCatalogService projectCatalog = createProjectCatalog(storageDirectory);
        ProjectDescriptor savedProject = projectCatalog.listProjects().getFirst();
        projectCatalog.markLastOpened(Optional.of(savedProject));

        ProjectDescriptor result = NeuronMapApp.resolveInitialProject(
                projectCatalog,
                "Default project"
        );

        assertEquals(savedProject, result);
    }

    /**
     * Перевіряє, що коли останній відкритий проєкт уже недоступний, вибирається перший проєкт каталогу.
     *
     * @throws IOException якщо тест не може підготувати файли збережених проєктів.
     */
    @Test
    void fallsBackToFirstSavedProjectWhenLastOpenedProjectIsUnavailable() throws IOException {
        Path storageDirectory = temporaryDirectory.resolve("projects");
        createSavedProject(storageDirectory, "Alpha");
        createSavedProject(storageDirectory, "Beta");
        new GlobalSettingsStore(temporaryDirectory.resolve("global-settings.properties"))
                .save("last_project_name", "Missing project");
        ProjectCatalogService projectCatalog = createProjectCatalog(storageDirectory);
        ProjectDescriptor expected = projectCatalog.listProjects().getFirst();

        ProjectDescriptor result = NeuronMapApp.resolveInitialProject(
                projectCatalog,
                "Default project"
        );

        assertEquals(expected, result);
    }

    /**
     * Перевіряє, що порожній каталог призводить до створення тимчасового проєкту з переданою типовою назвою.
     */
    @Test
    void createsTransientProjectWhenCatalogIsEmpty() {
        ProjectCatalogService projectCatalog = createProjectCatalog(
                temporaryDirectory.resolve("projects")
        );

        ProjectDescriptor result = NeuronMapApp.resolveInitialProject(
                projectCatalog,
                "Default project"
        );

        assertEquals("Default project", result.name());
        assertFalse(result.isPersisted());
        assertTrue(projectCatalog.listProjects().isEmpty());
    }

    /**
     * Перевіряє, що під час завершення спочатку зберігається геометрія вікна, а потім зупиняється редактор.
     * Повторний запит завершення не повинен дублювати жодну з операцій.
     */
    @Test
    void persistsWindowStateBeforeShutdownAndRunsOnlyOnce() {
        List<String> operations = new ArrayList<>();
        WindowLifecycleCoordinator coordinator = new WindowLifecycleCoordinator(
                () -> operations.add("persist"),
                () -> operations.add("shutdown")
        );

        coordinator.shutdown();
        coordinator.shutdown();

        assertEquals(List.of("persist", "shutdown"), operations);
    }

    /**
     * Перевіряє, що редактор завершується навіть тоді, коли збереження стану вікна завершується помилкою.
     * Помилка збереження передається виклику, а повторне завершення не запускає очищення знову.
     */
    @Test
    void shutsDownEditorWhenPersistingWindowStateFails() {
        IllegalStateException persistenceFailure = new IllegalStateException("persistence failed");
        AtomicInteger shutdownCalls = new AtomicInteger();
        WindowLifecycleCoordinator coordinator = new WindowLifecycleCoordinator(
                () -> {
                    throw persistenceFailure;
                },
                shutdownCalls::incrementAndGet
        );

        assertSame(persistenceFailure, assertThrows(IllegalStateException.class, coordinator::shutdown));
        coordinator.shutdown();

        assertEquals(1, shutdownCalls.get());
    }

    /**
     * Перевіряє, що помилка завершення редактора не маскує первинну помилку збереження стану вікна.
     * Другий збій додається до первинного як suppressed exception.
     */
    @Test
    void preservesPersistenceFailureWhenEditorShutdownAlsoFails() {
        IllegalStateException persistenceFailure = new IllegalStateException("persistence failed");
        IllegalArgumentException shutdownFailure = new IllegalArgumentException("shutdown failed");
        WindowLifecycleCoordinator coordinator = new WindowLifecycleCoordinator(
                () -> {
                    throw persistenceFailure;
                },
                () -> {
                    throw shutdownFailure;
                }
        );

        assertSame(persistenceFailure, assertThrows(IllegalStateException.class, coordinator::shutdown));

        assertEquals(1, persistenceFailure.getSuppressed().length);
        assertSame(shutdownFailure, persistenceFailure.getSuppressed()[0]);
    }

    /**
     * Створює службу каталогу, ізольовану у тимчасовому каталозі поточного тесту.
     *
     * @param storageDirectory кореневий каталог збережених проєктів.
     * @return службу каталогу з файловим репозиторієм і тимчасовим сховищем глобальних налаштувань.
     */
    private ProjectCatalogService createProjectCatalog(Path storageDirectory) {
        GlobalSettingsStore globalSettings = new GlobalSettingsStore(
                temporaryDirectory.resolve("global-settings.properties")
        );
        return new ProjectCatalogService(
                new FileSystemProjectCatalogRepository(storageDirectory, globalSettings)
        );
    }

    /**
     * Створює мінімальну структуру збереженого проєкту, яку розпізнає файловий каталог.
     *
     * @param storageDirectory кореневий каталог проєктів.
     * @param projectName назва каталогу проєкту.
     * @throws IOException якщо не вдалося створити каталог або файл бази даних-заглушку.
     */
    private static void createSavedProject(Path storageDirectory, String projectName) throws IOException {
        Path projectDirectory = storageDirectory.resolve(projectName);
        Files.createDirectories(projectDirectory);
        Files.createFile(projectDirectory.resolve("project.db"));
    }
}
