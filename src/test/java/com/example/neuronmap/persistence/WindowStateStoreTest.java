package com.example.neuronmap.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє збереження, відновлення та обробку помилок файла геометрії вікна.
 */
class WindowStateStoreTest {

    /**
     * Перевіряє запис і відновлення геометрії вікна без зміни її значень.
     */
    @Test
    void savesAndLoadsWindowGeometry(@TempDir Path tempDir) {
        Path file = tempDir.resolve("window.properties");
        WindowStateStore store = new WindowStateStore(file);

        WindowState expected = new WindowState(
                1440.0,
                900.0,
                125.0,
                80.0
        );

        store.save(expected);

        assertEquals(expected, store.load().orElseThrow());
    }

    /**
     * Перевіряє, що відсутній або некоректний файл стану не перешкоджає запуску з типовою геометрією вікна.
     *
     * @throws IOException якщо не вдалося створити тестовий файл.
     */
    @Test
    void missingOrInvalidStateFallsBackToEmpty(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("window.properties");
        WindowStateStore store = new WindowStateStore(file);

        assertTrue(store.load().isEmpty());

        Files.writeString(
                file,
                "width=broken\nheight=900\nx=10\ny=20\n"
        );

        assertTrue(store.load().isEmpty());
    }

    /**
     * Перевіряє, що помилка запису геометрії реєструється, але не перериває завершення роботи вікна.
     *
     * @throws IOException якщо не вдалося створити тестові файлові умови.
     */
    @Test
    void logsWriteFailureWithoutThrowing(@TempDir Path tempDir) throws IOException {
        Path parentFile = tempDir.resolve("not-a-directory");
        Files.writeString(parentFile, "blocker");
        Path stateFile = parentFile.resolve("window.properties");

        Logger logger = Logger.getLogger(WindowStateStore.class.getName());
        CapturingHandler handler = new CapturingHandler();
        Level previousLevel = logger.getLevel();
        boolean previousParentHandlers = logger.getUseParentHandlers();
        logger.addHandler(handler);
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);

        try {
            new WindowStateStore(stateFile).save(new WindowState(800.0, 600.0, 10.0, 20.0));

            assertNotNull(handler.record);
            assertEquals(Level.WARNING, handler.record.getLevel());
            assertTrue(handler.record.getThrown() instanceof IOException);
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParentHandlers);
        }
    }

    /**
     * Зберігає останній запис журналу, щоб тест міг перевірити повідомлення про помилку збереження.
     */
    private static final class CapturingHandler extends Handler {

        private LogRecord record;

        /**
         * Зберігає запис журналу, який надійшов від тестованого сховища.
         *
         * @param logRecord запис журналу.
         */
        @Override
        public void publish(LogRecord logRecord) {
            record = logRecord;
        }

        /** Завершує запис без додаткових дій, оскільки дані зберігаються в пам’яті. */
        @Override
        public void flush() {
        }

        /** Закриває тестовий обробник без звільнення зовнішніх ресурсів. */
        @Override
        public void close() {
        }
    }
}
