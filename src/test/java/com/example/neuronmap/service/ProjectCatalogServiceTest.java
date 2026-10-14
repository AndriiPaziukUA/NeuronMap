package com.example.neuronmap.service;

import com.example.neuronmap.persistence.project.FileSystemProjectCatalogRepository;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.project.ProjectDescriptor;
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

    /** Перевіряє, що каталог повертає лише проєкти з файлом бази даних. */
    @Test
    void listsOnlyDirectoriesContainingProjectDatabase(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        GlobalSettingsStore settings = new GlobalSettingsStore(
                root.resolve("neuronmap-global.properties")
        );
        ProjectCatalogService catalog = catalog(root, settings);

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

    /** Перевіряє, що створення тимчасового проєкту повторно використовує назву незбереженого каталогу. */
    @Test
    void generatedNamesReuseFoldersWithoutProjectDatabase(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        ProjectCatalogService catalog = catalog(root);

        Files.createDirectories(root.resolve("Project"));
        Files.writeString(root.resolve("Project").resolve("junk.txt"), "junk");

        ProjectDescriptor project = catalog.createTransientProject("Project");

        assertEquals("Project", project.name());
        assertTrue(Files.exists(root.resolve("Project").resolve("junk.txt")));
        assertFalse(catalog.listProjects().stream()
                .anyMatch(saved -> saved.name().equals("Project")));
    }

    /** Перевіряє вибір першого вільного суфікса для назви нового проєкту. */
    @Test
    void generatedNamesUseFirstAvailableSuffix(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        ProjectCatalogService catalog = catalog(root);
        Files.createDirectories(root.resolve("Project"));
        Files.writeString(root.resolve("Project/project.db"), "1");
        Files.createDirectories(root.resolve("Project 1"));
        Files.writeString(root.resolve("Project 1/project.db"), "2");

        ProjectDescriptor next = catalog.createTransientProject("Project");

        assertEquals("Project 2", next.name());
    }

    /** Перевіряє очищення каталогу-призначення без бази даних під час перейменування. */
    @Test
    void renameReusesAndCleansDirectoryWithoutProjectDatabase(
            @TempDir Path tempDir
    ) throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        ProjectCatalogService catalog = catalog(root);
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

    /** Перевіряє перенесення каталогу проєкту й збереження файла бази даних під час перейменування. */
    @Test
    void renameMovesTheProjectFolderAndKeepsDatabase(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        ProjectCatalogService catalog = catalog(root);
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

    /** Перевіряє виявлення наявного проєкту без окремих метаданих каталогу. */
    @Test
    void externalProjectIsDiscoverableWithoutCatalogMetadata(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        ProjectCatalogService catalog = catalog(root);
        Path database = root.resolve("Copied").resolve("project.db");
        Files.createDirectories(database.getParent());
        Files.writeString(database, "copied");

        assertEquals("Copied", catalog.listProjects().getFirst().name());
    }

    /** Перевіряє сортування проєктів за часом останньої зміни. */
    @Test
    void projectsAreOrderedByModificationTime(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        ProjectCatalogService catalog = catalog(root);
        Path older = root.resolve("Older").resolve("project.db");
        Path newer = root.resolve("Newer").resolve("project.db");
        Files.createDirectories(older.getParent());
        Files.createDirectories(newer.getParent());
        Files.writeString(older, "old");
        Thread.sleep(20);
        Files.writeString(newer, "new");

        assertEquals("Newer", catalog.listProjects().getFirst().name());
    }

    /** Перевіряє збереження та повторне читання назви останнього відкритого проєкту. */
    @Test
    void lastOpenedProjectIsStoredOutsideProjectFiles(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        GlobalSettingsStore settings = new GlobalSettingsStore(
                root.resolve("settings.properties")
        );
        ProjectCatalogService catalog = catalog(root, settings);
        Path database = root.resolve("Experiment").resolve("project.db");
        Files.createDirectories(database.getParent());
        Files.writeString(database, "db");
        ProjectDescriptor project = catalog.listProjects().getFirst();

        catalog.markLastOpened(project);

        ProjectCatalogService reloaded = catalog(root, settings);
        assertEquals("Experiment", reloaded.lastOpenedProject().name());
    }

    /**
     * Створює службу каталогу з новим файловим адаптером і стандартним файлом налаштувань.
     *
     * @param root кореневий каталог тестових проєктів.
     * @return службу каталогу для тестового файлового сховища.
     */
    private static ProjectCatalogService catalog(Path root) {
        return catalog(root, new GlobalSettingsStore(root.resolve("settings.properties")));
    }

    /**
     * Створює службу каталогу з переданим сховищем глобальних налаштувань.
     *
     * @param root кореневий каталог тестових проєктів.
     * @param settings тестове сховище глобальних налаштувань.
     * @return службу каталогу для тестового файлового сховища.
     */
    private static ProjectCatalogService catalog(Path root, GlobalSettingsStore settings) {
        return new ProjectCatalogService(new FileSystemProjectCatalogRepository(root, settings));
    }
}
