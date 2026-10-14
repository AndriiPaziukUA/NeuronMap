package com.example.neuronmap.persistence.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє виявлення змін каталогів проєктів і стійкість файлового спостерігача до помилок слухача.
 */
final class ProjectDirectoryWatcherTest {

    /**
     * Перевіряє виявлення нового каталогу проєкту та створення його файла бази даних.
     *
     * @param tempDir тимчасовий каталог, наданий JUnit.
     * @throws Exception якщо файлові операції або очікування події не вдалися.
     */
    @Test
    void detectsNewProjectFolderAndItsDatabase(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        CountDownLatch changed = new CountDownLatch(1);

        try (ProjectDirectoryWatcher watcher = new ProjectDirectoryWatcher(root, changed::countDown)) {
            watcher.start();

            Path projectDirectory = root.resolve("Copied Project");
            Files.createDirectories(projectDirectory);
            Files.writeString(projectDirectory.resolve("project.db"), "db");

            assertTrue(changed.await(5, TimeUnit.SECONDS));
        }
    }

    /**
     * Перевіряє, що повторне закриття спостерігача не спричиняє помилки.
     *
     * @param tempDir тимчасовий каталог, наданий JUnit.
     */
    @Test
    void closeIsIdempotent(@TempDir Path tempDir) {
        Path root = tempDir.resolve("NeuronMap");
        ProjectDirectoryWatcher watcher = new ProjectDirectoryWatcher(root, () -> { });

        watcher.start();
        watcher.close();
        watcher.close();
    }

    /**
     * Перевіряє, що виняток слухача не зупиняє відстеження наступних файлових змін.
     *
     * @param tempDir тимчасовий каталог, наданий JUnit.
     * @throws Exception якщо файлові операції або очікування подій не вдалися.
     */
    @Test
    void listenerFailureDoesNotStopWatching(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        AtomicInteger listenerCalls = new AtomicInteger();
        CountDownLatch firstCallback = new CountDownLatch(1);
        CountDownLatch callbackAfterFailure = new CountDownLatch(1);

        try (ProjectDirectoryWatcher watcher = new ProjectDirectoryWatcher(root, () -> {
            int call = listenerCalls.incrementAndGet();
            if (call == 1) {
                firstCallback.countDown();
                throw new IllegalStateException("Expected test listener failure");
            }
            callbackAfterFailure.countDown();
        })) {
            watcher.start();
            Path projectDirectory = root.resolve("Resilient Project");
            Files.createDirectories(projectDirectory);

            assertTrue(firstCallback.await(5, TimeUnit.SECONDS));
            Files.writeString(projectDirectory.resolve("project.db"), "db");

            assertTrue(callbackAfterFailure.await(5, TimeUnit.SECONDS));
            assertTrue(listenerCalls.get() >= 2);
        }
    }

    /**
     * Перевіряє, що після закриття спостерігач можна запустити повторно без витоку попереднього потоку.
     *
     * @param tempDir тимчасовий каталог, наданий JUnit.
     * @throws Exception якщо файлові операції або очікування події не вдалися.
     */
    @Test
    void canRestartAfterClose(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("NeuronMap");
        CountDownLatch changed = new CountDownLatch(1);
        ProjectDirectoryWatcher watcher = new ProjectDirectoryWatcher(root, changed::countDown);

        watcher.start();
        watcher.close();
        watcher.start();
        try {
            Files.createDirectories(root.resolve("Restarted Project"));
            assertTrue(changed.await(5, TimeUnit.SECONDS));
        } finally {
            watcher.close();
        }
    }
}
