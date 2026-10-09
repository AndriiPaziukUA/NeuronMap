package com.example.neuronmap.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє сповіщення про зміни папок проєктів і завершення спостереження.
 */
final class ProjectDirectoryWatcherTest {

    /**
     * Перевіряє очікувану поведінку: новий проєкт папка і база даних.
     *
     * @param tempDir значення, що визначає відповідну операцію для цієї операції.
     */
    @Test
    void detectsNewProjectFolderAndItsDatabase(@TempDir Path tempDir)
            throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        CountDownLatch changed = new CountDownLatch(1);

        try (ProjectDirectoryWatcher watcher =
                     new ProjectDirectoryWatcher(root, changed::countDown)) {
            watcher.start();

            Path projectDirectory = root.resolve("Copied Project");
            Files.createDirectories(projectDirectory);
            Files.writeString(projectDirectory.resolve("project.db"), "db");

            assertTrue(changed.await(5, TimeUnit.SECONDS));
        }
    }

    /**
     * Перевіряє очікувану поведінку: закриття.
     *
     * @param tempDir значення, що визначає відповідну операцію для цієї операції.
     */
    @Test
    void closeIsIdempotent(@TempDir Path tempDir) {
        Path root = tempDir.resolve("NeuronMap");
        ProjectDirectoryWatcher watcher =
                new ProjectDirectoryWatcher(root, () -> { });

        watcher.start();
        watcher.close();
        watcher.close();
    }
}
