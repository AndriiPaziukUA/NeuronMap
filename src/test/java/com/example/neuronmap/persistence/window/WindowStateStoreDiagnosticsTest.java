package com.example.neuronmap.persistence.window;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє діагностику пошкоджених файлів геометрії вікна за збереження безпечного fallback.
 */
final class WindowStateStoreDiagnosticsTest {

    /**
     * Перевіряє, що пошкоджений escape у файлі властивостей не перериває відновлення та реєструється в журналі.
     *
     * @param tempDir ізольований тимчасовий каталог тесту.
     * @throws IOException якщо тестовий файл неможливо створити.
     */
    @Test
    void logsMalformedPropertiesEscapeAndReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("window.properties");
        String malformedEscape = "width=" + "\\" + "uZZZZ\nheight=600\nx=10\ny=20\n";
        Files.writeString(file, malformedEscape);

        Logger logger = Logger.getLogger(WindowStateStore.class.getName());
        CapturingHandler handler = attach(logger);
        try {
            assertEquals(Optional.empty(), new WindowStateStore(file).load());
            assertEquals(1, handler.records.size());
            LogRecord record = handler.records.getFirst();
            assertEquals(Level.WARNING, record.getLevel());
            assertTrue(record.getMessage().contains(file.toString()));
            assertTrue(record.getThrown() instanceof IllegalArgumentException);
        } finally {
            detach(logger, handler);
        }
    }

    /**
     * Перевіряє, що некоректне число діагностується, але пошкоджена геометрія не перешкоджає запуску.
     *
     * @param tempDir ізольований тимчасовий каталог тесту.
     * @throws IOException якщо тестовий файл неможливо створити.
     */
    @Test
    void logsMalformedNumberAndReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("window.properties");
        Files.writeString(file, "width=broken\nheight=600\nx=10\ny=20\n");

        Logger logger = Logger.getLogger(WindowStateStore.class.getName());
        CapturingHandler handler = attach(logger);
        try {
            assertTrue(new WindowStateStore(file).load().isEmpty());
            assertEquals(1, handler.records.size());
            LogRecord record = handler.records.getFirst();
            assertEquals(Level.WARNING, record.getLevel());
            assertTrue(record.getMessage().contains(file.toString()));
            assertTrue(record.getThrown() instanceof NumberFormatException);
        } finally {
            detach(logger, handler);
        }
    }

    /**
     * Перевіряє, що відсутність необов’язкового файла геометрії не створює хибного попередження.
     *
     * @param tempDir ізольований тимчасовий каталог тесту.
     */
    @Test
    void doesNotLogMissingOptionalFile(@TempDir Path tempDir) {
        Logger logger = Logger.getLogger(WindowStateStore.class.getName());
        CapturingHandler handler = attach(logger);
        try {
            assertTrue(new WindowStateStore(tempDir.resolve("missing.properties")).load().isEmpty());
            assertTrue(handler.records.isEmpty());
        } finally {
            detach(logger, handler);
        }
    }

    /**
     * Додає тестовий обробник і запам’ятовує поточні параметри журналу.
     *
     * @param logger журнал тестованого класу.
     * @return обробник накопичення записів.
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
     * Від’єднує тестовий обробник і відновлює попередні параметри журналу.
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
     * Зберігає записи журналу в пам’яті, не звертаючись до зовнішніх ресурсів.
     */
    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();
        private Level previousLevel;
        private boolean previousParentHandlers;

        /**
         * Додає запис журналу до тестового списку.
         *
         * @param record запис журналу.
         */
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        /**
         * Виконує порожнє очищення обробника.
         */
        @Override
        public void flush() {
        }

        /**
         * Закриває обробник без додаткових операцій.
         */
        @Override
        public void close() {
        }
    }
}
