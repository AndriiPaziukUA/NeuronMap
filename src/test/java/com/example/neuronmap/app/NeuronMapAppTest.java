package com.example.neuronmap.app;

import com.example.neuronmap.persistence.FileSystemProjectCatalogRepository;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.ProjectCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє вибір початкового проєкту: останній відкритий, резервний збережений або новий тимчасовий.
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
        projectCatalog.markLastOpened(savedProject);

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
