package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє міграції старих схем SQLite та відсутність зайвих записів при повторному відкритті. */
class SqliteSchemaMigrationTest {

    /** Перевіряє перенесення координат нейронів зі старої таблиці до таблиці подань. */
    @Test
    void migratesLegacyNeuronCoordinates(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE neurons (
                        id TEXT PRIMARY KEY, type TEXT NOT NULL, activation INTEGER NOT NULL DEFAULT 0,
                        x REAL NOT NULL, y REAL NOT NULL
                    )
                    """);
            // This insert intentionally targets the legacy schema created above.
            //noinspection SqlResolve
            statement.executeUpdate("INSERT INTO neurons (id, type, activation, x, y) VALUES ('legacy', 'EXCITATORY', 4, 123, 456)");
        }
        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.loadInto(new NeuronMapModel());
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            assertFalse(hasNeuronColumn(statement, "x"));
            assertFalse(hasNeuronColumn(statement, "y"));
            try (ResultSet resultSet = statement.executeQuery("SELECT neuron_id, x, y FROM neuron_presentations")) {
                assertTrue(resultSet.next());
                assertEquals("legacy", resultSet.getString("neuron_id"));
                assertEquals(123.0, resultSet.getDouble("x"));
                assertEquals(456.0, resultSet.getDouble("y"));
            }
        }
    }

    /** Перевіряє, що повторна перевірка актуальної схеми не змінює час модифікації файла БД. */
    @Test
    void migratingCurrentSchemaDoesNotRewriteDatabaseFile(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("current.db");
        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.loadInto(new NeuronMapModel());
        }
        int currentVersion;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            currentVersion = readVersion(connection);
        }
        assertTrue(currentVersion > 0);
        FileTime marker = FileTime.fromMillis(946_684_800_000L);
        Files.setLastModifiedTime(db, marker);
        FileTime expectedModifiedTime = Files.getLastModifiedTime(db);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            SqliteSchema.migrate(connection);
        }
        assertEquals(expectedModifiedTime, Files.getLastModifiedTime(db));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            assertEquals(currentVersion, readVersion(connection));
        }
    }

    /** Перевіряє, що повторне відкриття чинного репозиторію не змінює час файла без операцій запису. */
    @Test
    void reopeningCurrentProjectDoesNotUpdateDatabaseTimestamp(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("reopened.db");
        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.loadInto(new NeuronMapModel());
        }
        FileTime marker = FileTime.fromMillis(946_684_800_000L);
        Files.setLastModifiedTime(db, marker);
        FileTime expectedModifiedTime = Files.getLastModifiedTime(db);
        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.loadInto(new NeuronMapModel());
        }
        assertEquals(expectedModifiedTime, Files.getLastModifiedTime(db));
    }

    /** Перевіряє, що схема новішої версії відхиляється без зміни файла та номера версії. */
    @Test
    void rejectsNewerSchemaWithoutModifyingDatabase(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("future.db");
        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.loadInto(new NeuronMapModel());
        }
        int futureVersion;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            futureVersion = readVersion(connection) + 1;
            statement.execute("PRAGMA user_version = " + futureVersion);
        }
        FileTime marker = FileTime.fromMillis(946_684_800_000L);
        Files.setLastModifiedTime(db, marker);
        FileTime expectedModifiedTime = Files.getLastModifiedTime(db);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            assertThrows(PersistenceException.class, () -> SqliteSchema.migrate(connection));
        }
        assertEquals(expectedModifiedTime, Files.getLastModifiedTime(db));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            assertEquals(futureVersion, readVersion(connection));
        }
    }

    /** Перевіряє, що старі нейрони отримують один активний такт і NULL у новому полі назви. */
    @Test
    void migratesExistingNeuronRowsWithDefaultActiveTicksAndNullName(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("legacy-active-ticks.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE neurons (
                        id TEXT PRIMARY KEY, type TEXT NOT NULL,
                        activation INTEGER NOT NULL DEFAULT 0,
                        signal_strength INTEGER NOT NULL DEFAULT 1,
                        activation_threshold INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            statement.executeUpdate("INSERT INTO neurons (id, type) VALUES ('old-neuron', 'EXCITATORY')");
        }
        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.loadInto(new NeuronMapModel());
        }
        // SqliteSchema.migrate() adds both columns before this query is executed.
        //noinspection SqlResolve
        String sql = "SELECT active_ticks, name FROM neurons WHERE id = 'old-neuron'";
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt("active_ticks"));
            assertNull(resultSet.getString("name"));
        }
    }

    /** Перевіряє збереження та відновлення налаштованої тривалості активності нейрона. */
    @Test
    void savesAndRestoresConfiguredActiveTicks(@TempDir Path tempDir) {
        Path db = tempDir.resolve("active-ticks.db");
        NeuronMapModel original = new NeuronMapModel();
        Neuron neuron = original.createNeuron(NeuronType.EXCITATORY, 20, 30);
        neuron.setActiveTicks(4);
        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.save(original, CameraState.defaultState());
        }
        NeuronMapModel restored = new NeuronMapModel();
        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.loadInto(restored);
        }
        assertEquals(4, restored.neuron(neuron.id()).activeTicks());
    }

    /** Читає поточну версію схеми для перевірок без зміни бази даних. */
    private static int readVersion(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    /** Перевіряє, чи містить таблиця стовпець із заданою назвою. */
    private static boolean hasNeuronColumn(Statement statement, String column) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(neurons)")) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
