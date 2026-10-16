package com.example.neuronmap.persistence.project;

import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.project.ProjectDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє відновлення каталогу проєктів, коли файлову часову мітку неможливо прочитати.
 */
final class FileSystemProjectCatalogMetadataTest {

    /**
     * Перевіряє, що помилка читання часу залишає дату відсутньою, зберігає проєкт у каталозі та реєструє причину.
     *
     * @param tempDir ізольований тимчасовий каталог тесту.
     * @throws IOException якщо тестові файли неможливо підготувати.
     */
    @Test
    void retainsProjectWithoutInventedDateAndLogsMetadataFailure(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("projects");
        Path database = root.resolve("Saved/project.db");
        Files.createDirectories(database.getParent());
        Files.writeString(database, "db");

        IOException failure = new IOException("metadata unavailable");
        FileSystemProjectCatalogRepository repository = new FileSystemProjectCatalogRepository(
                root,
                new GlobalSettingsStore(root.resolve("settings.properties")),
                path -> {
                    throw failure;
                }
        );
        Logger logger = Logger.getLogger(FileSystemProjectCatalogRepository.class.getName());
        CapturingHandler handler = attach(logger);
        try {
            List<ProjectDescriptor> projects = repository.listSavedProjects();

            assertEquals(1, projects.size());
            assertEquals("Saved", projects.getFirst().name());
            assertNull(projects.getFirst().modifiedAt());
            assertEquals(1, handler.records.size());
            LogRecord record = handler.records.getFirst();
            assertEquals(Level.WARNING, record.getLevel());
            assertTrue(record.getMessage().contains(database.toString()));
            assertEquals(failure, record.getThrown());
        } finally {
            detach(logger, handler);
        }
    }

    /**
     * Додає обробник для перевірки журналювання й запам’ятовує попередні налаштування журналу.
     *
     * @param logger журнал тестованого класу.
     * @return обробник записів журналу.
     */
    private static CapturingHandler attach(Logger logger) {
        CapturingHandler handler = new CapturingHandler();
        handler.previousLevel = logger.getLevel();
        handler.previousParentHandlers = logger.getUseParentHandlers();
        logger.addHandler(handler);
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);
        return handler;
    }

    /**
     * Від’єднує тестовий обробник і відновлює початкову конфігурацію журналу.
     *
     * @param logger журнал тестованого класу.
     * @param handler обробник, який потрібно прибрати.
     */
    private static void detach(Logger logger, CapturingHandler handler) {
        logger.removeHandler(handler);
        logger.setLevel(handler.previousLevel);
        logger.setUseParentHandlers(handler.previousParentHandlers);
    }

    /**
     * Зберігає журнальні записи в пам’яті для перевірок тесту.
     */
    private static final class CapturingHandler extends Handler {
        private final java.util.ArrayList<LogRecord> records = new java.util.ArrayList<>();
        private Level previousLevel;
        private boolean previousParentHandlers;

        /**
         * Додає отриманий запис до тестового списку.
         *
         * @param record запис журналу.
         */
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        /**
         * Виконує порожнє очищення, оскільки зовнішні ресурси не використовуються.
         */
        @Override
        public void flush() {
        }

        /**
         * Закриває обробник без зовнішніх побічних ефектів.
         */
        @Override
        public void close() {
        }
    }
}
