package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.MapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє міграцію старої бази та сумісне значення затримки за замовчуванням. */
final class SqliteNeuronDelayMigrationTest {

    /** Перевіряє, що міграція додає затримку до наявного нейрона зі значенням в один такт. */
    @Test
    void addsOneTickDelayToExistingNeurons(@TempDir Path tempDir) throws Exception {
        Path database = tempDir.resolve("old-delay-schema.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE neurons (
                        id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        activation INTEGER NOT NULL DEFAULT 0,
                        signal_strength INTEGER NOT NULL DEFAULT 1,
                        activation_threshold INTEGER NOT NULL DEFAULT 1,
                        active_ticks INTEGER NOT NULL DEFAULT 1,
                        name TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE neuron_presentations (
                        neuron_id TEXT PRIMARY KEY,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        rotation REAL NOT NULL DEFAULT 0
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE connections (
                        id TEXT PRIMARY KEY,
                        source_id TEXT NOT NULL,
                        target_id TEXT NOT NULL,
                        UNIQUE (source_id, target_id),
                        CHECK (source_id <> target_id)
                    )
                    """);
            statement.executeUpdate("CREATE TABLE groups (id TEXT PRIMARY KEY)");
            statement.executeUpdate("""
                    CREATE TABLE group_members (
                        group_id TEXT NOT NULL,
                        neuron_id TEXT NOT NULL,
                        PRIMARY KEY (group_id, neuron_id)
                    )
                    """);
            statement.executeUpdate("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.executeUpdate("INSERT INTO neurons (id, type) VALUES ('legacy-neuron', 'EXCITATORY')");
            statement.executeUpdate("PRAGMA user_version = 6");
        }

        try (MapRepository repository = new SqliteMapRepository(database)) {
            NeuronMapModel restored = new NeuronMapModel();
            repository.loadInto(restored);
            var restoredNeuron = restored.neuron("legacy-neuron");
            assertNotNull(restoredNeuron);
            assertEquals(1, restoredNeuron.delayTicks());
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT * FROM neurons WHERE id = 'legacy-neuron'")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt("delay_ticks"));
        }
    }
}
