package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє міграції старих схем SQLite та відсутність записів при повторному відкритті актуальної схеми. */
class SqliteSchemaMigrationTest {

    /**
     * Перевіряє перенесення координат нейронів зі старої таблиці до таблиці подань.
     *
     * @param tempDir тимчасовий каталог для тестової бази даних.
     * @throws Exception якщо створення БД або перевірка результату завершується помилкою.
     */
    @Test
    void migratesLegacyNeuronCoordinates(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("legacy.db");

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + db.toAbsolutePath()
        ); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE neurons (
                        id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        activation INTEGER NOT NULL DEFAULT 0,
                        x REAL NOT NULL,
                        y REAL NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO neurons (
                        id, type, activation, x, y
                    ) VALUES (
                        'legacy', 'EXCITATORY', 4, 123, 456
                    )
                    """);
        }

        try (MapRepository ignored = new SqliteMapRepository(db)) {
            // Створення репозиторію запускає міграцію через виробничий шлях.
        }

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + db.toAbsolutePath()
        ); Statement statement = connection.createStatement()) {
            assertFalse(hasColumn(statement, "neurons", "x"));
            assertFalse(hasColumn(statement, "neurons", "y"));

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT neuron_id, x, y FROM neuron_presentations"
            )) {
                assertTrue(resultSet.next());
                assertEquals("legacy", resultSet.getString("neuron_id"));
                assertEquals(123.0, resultSet.getDouble("x"));
                assertEquals(456.0, resultSet.getDouble("y"));
            }
        }
    }

    /**
     * Перевіряє, що повторна перевірка актуальної схеми не змінює час модифікації файла БД.
     *
     * @param tempDir тимчасовий каталог для тестової бази даних.
     * @throws Exception якщо створення БД, читання атрибутів чи перевірка завершується помилкою.
     */
    @Test
    void migratingCurrentSchemaDoesNotRewriteDatabaseFile(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("current.db");
        try (MapRepository ignored = new SqliteMapRepository(db)) {
            // Підготовлює файл з усіма таблицями та актуальною версією схеми.
        }

        int currentVersion;
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + db.toAbsolutePath()
        )) {
            currentVersion = readVersion(connection);
        }
        assertTrue(currentVersion > 0);

        FileTime marker = FileTime.fromMillis(946_684_800_000L);
        Files.setLastModifiedTime(db, marker);
        FileTime expectedModifiedTime = Files.getLastModifiedTime(db);

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + db.toAbsolutePath()
        )) {
            SqliteSchema.migrate(connection);
        }

        assertEquals(expectedModifiedTime, Files.getLastModifiedTime(db));
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + db.toAbsolutePath()
        )) {
            assertEquals(currentVersion, readVersion(connection));
        }
    }

    /**
     * Перевіряє, що повторне відкриття чинного репозиторію не змінює час файла без операцій запису.
     *
     * @param tempDir тимчасовий каталог для тестової бази даних.
     * @throws Exception якщо відкриття БД або перевірка атрибутів файла завершується помилкою.
     */
    @Test
    void reopeningCurrentProjectDoesNotUpdateDatabaseTimestamp(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("reopened.db");
        try (MapRepository ignored = new SqliteMapRepository(db)) {
            // Створює повністю ініціалізований файл проєкту.
        }

        FileTime marker = FileTime.fromMillis(946_684_800_000L);
        Files.setLastModifiedTime(db, marker);
        FileTime expectedModifiedTime = Files.getLastModifiedTime(db);

        try (MapRepository ignored = new SqliteMapRepository(db)) {
            // Повторне відкриття не повинно записувати актуальну версію схеми ще раз.
        }

        assertEquals(expectedModifiedTime, Files.getLastModifiedTime(db));
    }

    /**
     * Перевіряє, що схема новішої версії відхиляється без зміни файла та його номера версії.
     *
     * @param tempDir тимчасовий каталог для тестової бази даних.
     * @throws Exception якщо створення БД або перевірка її стану завершується помилкою.
     */
    @Test
    void rejectsNewerSchemaWithoutModifyingDatabase(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("future.db");
        try (MapRepository ignored = new SqliteMapRepository(db)) {
            // Створює валідну базу, з якої тест моделює файл майбутньої версії.
        }

        int futureVersion;
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + db.toAbsolutePath()
        ); Statement statement = connection.createStatement()) {
            futureVersion = readVersion(connection) + 1;
            statement.execute("PRAGMA user_version = " + futureVersion);
        }

        FileTime marker = FileTime.fromMillis(946_684_800_000L);
        Files.setLastModifiedTime(db, marker);
        FileTime expectedModifiedTime = Files.getLastModifiedTime(db);

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + db.toAbsolutePath()
        )) {
            assertThrows(PersistenceException.class, () -> SqliteSchema.migrate(connection));
        }

        assertEquals(expectedModifiedTime, Files.getLastModifiedTime(db));
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + db.toAbsolutePath()
        )) {
            assertEquals(futureVersion, readVersion(connection));
        }
    }

    /** Читає поточну версію схеми для регресійних перевірок без зміни бази.
     *
     * @param connection відкрите з'єднання з базою SQLite.
     * @return номер збереженої версії схеми.
     * @throws Exception якщо запит версії не вдається виконати.
     */
    private static int readVersion(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    /** Перевіряє, чи містить таблиця стовпець із заданою назвою.
     *
     * @param statement оператор для запиту метаданих SQLite.
     * @param table назва таблиці.
     * @param column назва стовпця.
     * @return {@code true}, якщо стовпець існує.
     * @throws Exception якщо читання метаданих не вдається.
     */
    private static boolean hasColumn(Statement statement, String table, String column)
            throws Exception {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
