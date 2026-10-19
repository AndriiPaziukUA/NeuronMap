package com.example.neuronmap.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Перевіряє межі володіння, очищення вкладених каталогів і безпечну обробку символічних посилань. */
final class FileTreeOperationsTest {

    /**
     * Перевіряє, що очищення видаляє вкладений вміст, але залишає кореневий каталог проєкту.
     *
     * @param tempDir ізольований тимчасовий каталог тесту.
     * @throws IOException якщо підготовка або перевірка файлового дерева завершується помилкою.
     */
    @Test
    void clearsNestedContentsAndPreservesOwnedDirectory(@TempDir Path tempDir) throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("projects"));
        Path project = Files.createDirectory(root.resolve("Project"));
        Path nested = Files.createDirectories(project.resolve("nested/deeper"));
        Files.writeString(project.resolve("junk.txt"), "junk");
        Files.writeString(nested.resolve("data.txt"), "data");

        FileTreeOperations.clearOwnedDirectoryContents(root, project);

        assertTrue(Files.isDirectory(project));
        assertEquals(0L, countChildren(project));
    }

    /**
     * Перевіряє, що повне видалення каталогу не переходить за симлінком до зовнішнього дерева.
     *
     * @param tempDir ізольований тимчасовий каталог тесту.
     * @throws IOException якщо підготовка або перевірка файлового дерева завершується помилкою.
     */
    @Test
    void deletesOwnedTreeWithoutFollowingDirectorySymbolicLinks(@TempDir Path tempDir) throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("projects"));
        Path external = Files.createDirectories(tempDir.resolve("external/nested"));
        Path sentinel = Files.writeString(external.resolve("keep.txt"), "keep");
        Path project = Files.createDirectory(root.resolve("Project"));
        createDirectorySymbolicLink(project.resolve("external-link"), external.getParent());

        FileTreeOperations.deleteOwnedDirectoryTree(root, project);

        assertFalse(Files.exists(project));
        assertEquals("keep", Files.readString(sentinel));
        assertTrue(Files.isDirectory(external));
    }

    /**
     * Перевіряє, що API видалення дерева не видаляє файл, переданий замість каталогу.
     *
     * @param tempDir ізольований тимчасовий каталог тесту.
     * @throws IOException якщо підготовка або перевірка файла завершується помилкою.
     */
    @Test
    void refusesRegularFileAsOwnedDirectory(@TempDir Path tempDir) throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("projects"));
        Path file = Files.writeString(root.resolve("Project"), "keep");

        assertThrows(IOException.class, () -> FileTreeOperations.deleteOwnedDirectoryTree(root, file));
        assertEquals("keep", Files.readString(file));
    }

    /**
     * Перевіряє, що очищення не приймає каталог поза межами безпосереднього кореня володіння.
     *
     * @param tempDir ізольований тимчасовий каталог тесту.
     * @throws IOException якщо підготовка або перевірка файлового дерева завершується помилкою.
     */
    @Test
    void refusesDirectoriesOutsideOwnershipRoot(@TempDir Path tempDir) throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("projects"));
        Path external = Files.createDirectory(tempDir.resolve("external"));
        Path sentinel = Files.writeString(external.resolve("keep.txt"), "keep");

        assertThrows(
                IllegalArgumentException.class,
                () -> FileTreeOperations.clearOwnedDirectoryContents(root, external)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> FileTreeOperations.deleteOwnedDirectoryTree(root, external)
        );
        assertEquals("keep", Files.readString(sentinel));
    }

    /**
     * Перевіряє, що кореневий каталог-симлінк не дозволяє очистити його ціль.
     *
     * @param tempDir ізольований тимчасовий каталог тесту.
     * @throws IOException якщо підготовка або перевірка файлового дерева завершується помилкою.
     */
    @Test
    void refusesToClearSymbolicLinkAsOwnedDirectory(@TempDir Path tempDir) throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("projects"));
        Path external = Files.createDirectories(tempDir.resolve("external"));
        Path sentinel = Files.writeString(external.resolve("keep.txt"), "keep");
        Path link = root.resolve("Project");
        createDirectorySymbolicLink(link, external);

        assertThrows(IOException.class, () -> FileTreeOperations.clearOwnedDirectoryContents(root, link));
        assertThrows(IOException.class, () -> FileTreeOperations.deleteOwnedDirectoryTree(root, link));
        assertEquals("keep", Files.readString(sentinel));
    }

    /**
     * Перевіряє, що символічне посилання замість довіреного кореня не дозволяє очищати зовнішній каталог.
     *
     * @param tempDir ізольований тимчасовий каталог тесту.
     * @throws IOException якщо підготовка або перевірка файлового дерева завершується помилкою.
     */
    @Test
    void refusesSymbolicLinkAsOwnershipRoot(@TempDir Path tempDir) throws IOException {
        Path externalRoot = Files.createDirectory(tempDir.resolve("external-root"));
        Path externalProject = Files.createDirectory(externalRoot.resolve("Project"));
        Path sentinel = Files.writeString(externalProject.resolve("keep.txt"), "keep");
        Path rootLink = tempDir.resolve("projects-link");
        createDirectorySymbolicLink(rootLink, externalRoot);

        assertThrows(
                IOException.class,
                () -> FileTreeOperations.clearOwnedDirectoryContents(rootLink, rootLink.resolve("Project"))
        );
        assertEquals("keep", Files.readString(sentinel));
    }

    /**
     * Рахує безпосередні дочірні елементи каталогу для перевірки результату очищення.
     *
     * @param directory каталог, кількість елементів якого потрібно визначити.
     * @return кількість безпосередніх дочірніх елементів.
     * @throws IOException якщо каталог не вдалося прочитати.
     */
    private static long countChildren(Path directory) throws IOException {
        try (var children = Files.list(directory)) {
            return children.count();
        }
    }

    /**
     * Створює симлінк каталогу, пропускаючи тест лише на системах, де операція недоступна.
     *
     * @param link шлях, за яким створюється символічне посилання.
     * @param target каталог, на який посилатиметься посилання.
     * @throws IOException якщо посилання не вдалося створити; недоступні можливості платформи пропускають тест.
     */
    private static void createDirectorySymbolicLink(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException exception) {
            assumeTrue(false, "Symbolic links are not supported in this environment");
        } catch (IOException exception) {
            assumeTrue(false, "Symbolic links cannot be created in this environment");
        }
    }
}
