package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє відновлення стану камери з SQLite, включно з діагностикою пошкоджених значень. */
final class SqliteCameraStateValidationTest {

    /** Перевіряє, що повністю відсутні налаштування камери повертають типовий стан без попередження. */
    @Test
    void usesDefaultCameraWithoutWarningWhenSettingsAreAbsent(@TempDir Path tempDir) throws Exception {
        Logger logger = Logger.getLogger(SqliteSettingsStore.class.getName());
        CapturingHandler handler = attach(logger);
        try (SqliteMapRepository repository = new SqliteMapRepository(tempDir.resolve("no-camera-settings.db"))) {
            assertEquals(CameraState.defaultState(), repository.loadCameraState());
            assertTrue(handler.records.isEmpty());
        } finally {
            detach(logger, handler);
        }
    }

    /** Перевіряє, що нечислові, нескінченні та позадіапазонні значення камери відновлюються й діагностуються. */
    @Test
    void fallsBackAndLogsForInvalidZoomAndPanValues(@TempDir Path tempDir) throws Exception {
        List<String[]> invalidStates = List.of(
                new String[]{"NaN", "0.0", "0.0"},
                new String[]{"Infinity", "0.0", "0.0"},
                new String[]{"-Infinity", "0.0", "0.0"},
                new String[]{"0.0", "0.0", "0.0"},
                new String[]{"-1.0", "0.0", "0.0"},
                new String[]{"0.1", "0.0", "0.0"},
                new String[]{"3.6", "0.0", "0.0"},
                new String[]{"1.0", "NaN", "0.0"},
                new String[]{"1.0", "Infinity", "0.0"},
                new String[]{"1.0", "-Infinity", "0.0"},
                new String[]{"1.0", "0.0", "NaN"},
                new String[]{"1.0", "0.0", "Infinity"},
                new String[]{"1.0", "0.0", "-Infinity"},
                new String[]{"not-a-number", "0.0", "0.0"}
        );
        Logger logger = Logger.getLogger(SqliteSettingsStore.class.getName());
        CapturingHandler handler = attach(logger);
        try {
            int index = 0;
            for (String[] values : invalidStates) {
                Path database = tempDir.resolve("invalid-camera-" + index++ + ".db");
                try (SqliteMapRepository repository = new SqliteMapRepository(database)) {
                    writeSettings(database, values[0], values[1], values[2]);
                    assertEquals(CameraState.defaultState(), repository.loadCameraState());
                }
            }
            assertEquals(invalidStates.size(), handler.records.size());
            assertTrue(handler.records.stream().allMatch(record -> record.getLevel() == Level.WARNING));
            assertTrue(handler.records.stream().allMatch(record -> record.getMessage().contains("стан камери")));
        } finally {
            detach(logger, handler);
        }
    }

    /** Перевіряє, що частково записаний стан окремо діагностується, а не сприймається як відсутні налаштування. */
    @Test
    void logsPartiallyMissingCameraSettings(@TempDir Path tempDir) throws Exception {
        Path database = tempDir.resolve("partial-camera.db");
        Logger logger = Logger.getLogger(SqliteSettingsStore.class.getName());
        CapturingHandler handler = attach(logger);
        try (SqliteMapRepository repository = new SqliteMapRepository(database)) {
            writeSettings(database, "1.0", null, "10.0");
            assertEquals(CameraState.defaultState(), repository.loadCameraState());
            assertEquals(1, handler.records.size());
            assertTrue(handler.records.getFirst().getMessage().contains("Неповний"));
            assertEquals(Level.WARNING, handler.records.getFirst().getLevel());
        } finally {
            detach(logger, handler);
        }
    }

    /**
     * Перевіряє, що SQL-помилка читання налаштувань не маскується типовим станом камери.
     *
     * @param tempDir ізольований тимчасовий каталог тесту.
     * @throws Exception якщо робота з тестовою базою даних завершується помилкою.
     */
    @Test
    void wrapsSqlFailureInPersistenceException(@TempDir Path tempDir) throws Exception {
        Path database = tempDir.resolve("missing-settings-table.db");
        try (SqliteMapRepository repository = new SqliteMapRepository(database);
             Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE settings");

            PersistenceException exception = assertThrows(
                    PersistenceException.class,
                    repository::loadCameraState
            );
            assertTrue(exception.getCause() instanceof SQLException);
        }
    }

    /** Зберігає тестові значення камери напряму, щоб імітувати дані старої або пошкодженої версії сховища. */
    private static void writeSettings(Path database, String zoom, String panX, String panY) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            writeSetting(connection, SqliteSettingsStore.CAMERA_ZOOM_KEY, zoom);
            writeSetting(connection, SqliteSettingsStore.CAMERA_PAN_X_KEY, panX);
            writeSetting(connection, SqliteSettingsStore.CAMERA_PAN_Y_KEY, panY);
        }
    }

    /** Записує одне тестове налаштування або видаляє його, якщо значення не задано. */
    private static void writeSetting(Connection connection, String key, String value) throws Exception {
        if (value == null) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM settings WHERE key = ?")) {
                statement.setString(1, key);
                statement.executeUpdate();
            }
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO settings (key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    /** Додає до журналу тестовий обробник і тимчасово вмикає попередження. */
    private static CapturingHandler attach(Logger logger) {
        CapturingHandler handler = new CapturingHandler();
        handler.previousLevel = logger.getLevel();
        handler.previousParentHandlers = logger.getUseParentHandlers();
        logger.addHandler(handler);
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);
        return handler;
    }

    /** Від’єднує тестовий обробник і повертає попередню конфігурацію журналу. */
    private static void detach(Logger logger, CapturingHandler handler) {
        logger.removeHandler(handler);
        logger.setLevel(handler.previousLevel);
        logger.setUseParentHandlers(handler.previousParentHandlers);
    }

    /** Збирає записи журналу для перевірки поведінки відновлення. */
    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();
        private Level previousLevel;
        private boolean previousParentHandlers;

        /** Зберігає запис журналу. */
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        /** Очищає внутрішній стан, оскільки обробник не використовує зовнішніх ресурсів. */
        @Override
        public void flush() {
        }

        /** Закриває обробник без додаткових дій. */
        @Override
        public void close() {
        }
    }
}
