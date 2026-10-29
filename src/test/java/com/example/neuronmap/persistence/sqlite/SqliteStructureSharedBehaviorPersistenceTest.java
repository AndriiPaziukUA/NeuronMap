package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.service.NeuronService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє збереження спільного типу й напрямку структурної області, включно з порожніми структурами. */
final class SqliteStructureSharedBehaviorPersistenceTest {

    /** Перевіряє єдине джерело правди для існуючих і майбутніх учасників після циклу SQLite. */
    @Test
    void restoresSharedTypeAndDirectionForExistingAndFutureMembers(@TempDir Path tempDir) throws Exception {
        Path database = tempDir.resolve("structure-shared-behavior.db");
        NeuronMapModel original = new NeuronMapModel();
        NeuronService originalService = new NeuronService(original);
        Neuron structure = originalService.create(NeuronType.STRUCTURE, 10.0, 20.0);
        String existingMemberId = original.structure(structure.id()).memberIds().iterator().next();

        assertTrue(originalService.toggleType(structure.id()));
        assertTrue(originalService.setDirectionReversed(structure.id(), true));
        try (MapRepository repository = new SqliteMapRepository(database)) {
            repository.save(original, CameraState.defaultState());
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT type FROM neurons WHERE id = '" + existingMemberId + "'")) {
            assertTrue(resultSet.next());
            assertEquals(NeuronType.EXCITATORY.name(), resultSet.getString("type"),
                    "Рядок нейрона має містити лише технічний тип, а не дубльований тип структури");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT member_type, direction_reversed FROM structures WHERE id = '" + structure.id() + "'")) {
            assertTrue(resultSet.next());
            assertEquals(NeuronType.INHIBITORY.name(), resultSet.getString("member_type"));
            assertEquals(1, resultSet.getInt("direction_reversed"),
                    "Спільний напрямок має зберігатися у власному рядку структури");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT direction_reversed FROM neuron_presentations WHERE neuron_id = '" + structure.id() + "'")) {
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt("direction_reversed"),
                    "Рядок подання структури не має бути джерелом її спільного напрямку");
        }

        NeuronMapModel restored = new NeuronMapModel();
        try (MapRepository repository = new SqliteMapRepository(database)) {
            repository.loadInto(restored);
        }
        NeuronService restoredService = new NeuronService(restored);
        assertEquals(NeuronType.INHIBITORY, restored.neuron(existingMemberId).type());
        assertTrue(restored.presentation(existingMemberId).directionReversed());
        assertTrue(restored.presentation(structure.id()).directionReversed());
        assertTrue(restoredService.ensureStructureMemberCount(structure.id(), 3));

        for (String memberId : restored.structure(structure.id()).memberIds()) {
            assertEquals(NeuronType.INHIBITORY, restored.neuron(memberId).type());
            assertTrue(restored.presentation(memberId).directionReversed());
        }
    }

    /** Перевіряє міграцію з версії 11: тип кореня визначається впорядкованими учасниками його дерева. */
    @Test
    void migratesExistingStructureMemberTypeFromPersistedMembers(@TempDir Path tempDir) throws Exception {
        Path database = tempDir.resolve("legacy-structure-member-type.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE neurons (
                        id TEXT PRIMARY KEY, type TEXT NOT NULL, activation INTEGER NOT NULL DEFAULT 0,
                        signal_strength INTEGER NOT NULL DEFAULT 1, activation_threshold INTEGER NOT NULL DEFAULT 1,
                        active_ticks INTEGER NOT NULL DEFAULT 1, delay_ticks INTEGER NOT NULL DEFAULT 1,
                        activation_mode TEXT NOT NULL DEFAULT 'SIGNAL_STRENGTH', name TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE structures (
                        id TEXT PRIMARY KEY, expanded INTEGER NOT NULL DEFAULT 0, name TEXT,
                        signal_strength INTEGER NOT NULL DEFAULT 1, activation_threshold INTEGER NOT NULL DEFAULT 1,
                        active_ticks INTEGER NOT NULL DEFAULT 1, delay_ticks INTEGER NOT NULL DEFAULT 1,
                        activation_mode TEXT NOT NULL DEFAULT 'SIGNAL_STRENGTH'
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE structure_members (
                        structure_id TEXT NOT NULL, neuron_id TEXT NOT NULL UNIQUE, position_index INTEGER NOT NULL,
                        PRIMARY KEY (structure_id, neuron_id)
                    )
                    """);
            statement.executeUpdate("INSERT INTO neurons (id, type) VALUES ('root', 'STRUCTURE')");
            statement.executeUpdate("INSERT INTO neurons (id, type) VALUES ('leaf-a', 'INHIBITORY')");
            statement.executeUpdate("INSERT INTO neurons (id, type) VALUES ('leaf-b', 'INHIBITORY')");
            statement.executeUpdate("INSERT INTO structures (id) VALUES ('root')");
            statement.executeUpdate("INSERT INTO structure_members VALUES ('root', 'leaf-a', 0)");
            statement.executeUpdate("INSERT INTO structure_members VALUES ('root', 'leaf-b', 1)");
            statement.execute("PRAGMA user_version = 11");
            SqliteSchema.migrate(connection);

            // Стовпець додається SqliteSchema.migrate() під час тестованої міграції з версії 11.
            //noinspection SqlResolve
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT member_type FROM structures WHERE id = 'root'")) {
                assertTrue(resultSet.next());
                assertEquals(NeuronType.INHIBITORY.name(), resultSet.getString("member_type"));
            }
            try (ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(resultSet.next());
                assertEquals(13, resultSet.getInt(1));
            }
        }
    }

    /** Перевіряє перенесення напрямку в таблицю структури під час оновлення бази версії 12. */
    @Test
    void migratesSharedDirectionFromStructurePresentation(@TempDir Path tempDir) throws Exception {
        Path database = tempDir.resolve("legacy-structure-direction.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE neurons (
                        id TEXT PRIMARY KEY, type TEXT NOT NULL, activation INTEGER NOT NULL DEFAULT 0,
                        signal_strength INTEGER NOT NULL DEFAULT 1, activation_threshold INTEGER NOT NULL DEFAULT 1,
                        active_ticks INTEGER NOT NULL DEFAULT 1, delay_ticks INTEGER NOT NULL DEFAULT 1,
                        activation_mode TEXT NOT NULL DEFAULT 'SIGNAL_STRENGTH', name TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE neuron_presentations (
                        neuron_id TEXT PRIMARY KEY, x REAL NOT NULL, y REAL NOT NULL,
                        rotation REAL NOT NULL DEFAULT 0, direction_reversed INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE structures (
                        id TEXT PRIMARY KEY, expanded INTEGER NOT NULL DEFAULT 0, name TEXT,
                        signal_strength INTEGER NOT NULL DEFAULT 1, activation_threshold INTEGER NOT NULL DEFAULT 1,
                        active_ticks INTEGER NOT NULL DEFAULT 1, delay_ticks INTEGER NOT NULL DEFAULT 1,
                        activation_mode TEXT NOT NULL DEFAULT 'SIGNAL_STRENGTH', member_type TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE structure_members (
                        structure_id TEXT NOT NULL, neuron_id TEXT NOT NULL UNIQUE, position_index INTEGER NOT NULL,
                        PRIMARY KEY (structure_id, neuron_id)
                    )
                    """);
            statement.executeUpdate("INSERT INTO neurons (id, type) VALUES ('root', 'STRUCTURE')");
            statement.executeUpdate("INSERT INTO neurons (id, type) VALUES ('member', 'INHIBITORY')");
            statement.executeUpdate("INSERT INTO neuron_presentations (neuron_id, x, y, direction_reversed) "
                    + "VALUES ('root', 0, 0, 1)");
            statement.executeUpdate("INSERT INTO neuron_presentations (neuron_id, x, y, direction_reversed) "
                    + "VALUES ('member', 0, 0, 1)");
            // Стовпець member_type існує у схемі версії 12, що моделюється цим тестом.
            //noinspection SqlResolve
            statement.executeUpdate("INSERT INTO structures (id, member_type) VALUES ('root', 'INHIBITORY')");
            statement.executeUpdate("INSERT INTO structure_members VALUES ('root', 'member', 0)");
            statement.execute("PRAGMA user_version = 12");

            SqliteSchema.migrate(connection);

            // direction_reversed додає SqliteSchema.migrate() під час переходу з версії 12 на 13.
            //noinspection SqlResolve
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT member_type, direction_reversed FROM structures WHERE id = 'root'")) {
                assertTrue(resultSet.next());
                assertEquals(NeuronType.INHIBITORY.name(), resultSet.getString("member_type"),
                        "Міграція напрямку не повинна перезаписувати вже збережений спільний тип");
                assertEquals(1, resultSet.getInt("direction_reversed"));
            }
            try (ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(resultSet.next());
                assertEquals(13, resultSet.getInt(1));
            }
        }
    }

}
