package com.example.neuronmap.persistence;

import com.example.neuronmap.project.ProjectDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє файлові операції репозиторію каталогу проєктів та ізоляцію каталогу зберігання.
 */
final class FileSystemProjectCatalogRepositoryTest {

    /**
     * Перевіряє, що репозиторій повертає лише каталоги зі збереженим файлом бази даних.
     *
     * @param tempDir тимчасовий каталог тесту.
     * @throws Exception якщо підготовка тестових файлів завершується помилкою.
     */
    @Test
    void listsOnlyDirectoriesContainingProjectDatabase(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("projects");
        FileSystemProjectCatalogRepository repository = repository(root);
        Files.createDirectories(root.resolve("Saved"));
        Files.writeString(root.resolve("Saved/project.db"), "db");
        Files.createDirectories(root.resolve("Empty"));

        assertEquals(1, repository.listSavedProjects().size());
        assertEquals("Saved", repository.listSavedProjects().getFirst().name());
    }

    /**
     * Перевіряє перейменування каталогу проєкту через репозиторій.
     *
     * @param tempDir тимчасовий каталог тесту.
     * @throws Exception якщо підготовка тестових файлів завершується помилкою.
     */
    @Test
    void renamesProjectDirectory(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("projects");
        FileSystemProjectCatalogRepository repository = repository(root);
        Path database = root.resolve("Original/project.db");
        Files.createDirectories(database.getParent());
        Files.writeString(database, "db");
        ProjectDescriptor original = repository.listSavedProjects().getFirst();

        ProjectDescriptor renamed = repository.renameProject(original, "Renamed");

        assertEquals("Renamed", renamed.name());
        assertTrue(Files.isRegularFile(root.resolve("Renamed/project.db")));
        assertFalse(Files.exists(root.resolve("Original")));
    }

    /**
     * Перевіряє, що репозиторій не дозволяє видаляти проєкт поза кореневим каталогом.
     *
     * @param tempDir тимчасовий каталог тесту.
     * @throws Exception якщо підготовка тестових файлів завершується помилкою.
     */
    @Test
    void refusesToDeleteProjectOutsideStorageRoot(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("projects");
        FileSystemProjectCatalogRepository repository = repository(root);
        Path externalDatabase = tempDir.resolve("external/project.db");
        Files.createDirectories(externalDatabase.getParent());
        Files.writeString(externalDatabase, "db");
        ProjectDescriptor external = repository.descriptorForDatabasePath(externalDatabase);

        assertThrows(IllegalArgumentException.class, () -> repository.deleteProjectFiles(external));
        assertTrue(Files.exists(externalDatabase));
    }

    /**
     * Створює файловий репозиторій ізольованих тестових проєктів.
     *
     * @param root кореневий каталог тестового сховища.
     * @return файловий репозиторій каталогу проєктів.
     */
    private static FileSystemProjectCatalogRepository repository(Path root) {
        return new FileSystemProjectCatalogRepository(
                root,
                new GlobalSettingsStore(root.resolve("settings.properties"))
        );
    }
}
