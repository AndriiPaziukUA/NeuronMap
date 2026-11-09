package com.example.neuronmap.persistence.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє міграцію старої схеми SQLite до зберігання типів сигналу нейронів і структур. */
final class SqliteSignalTypeSchemaTest {

    /** Перевіряє додавання signal_type до бази версії 15 зі статичним значенням для наявних нейронів. */
    @Test
    void migratesVersionFifteenDatabaseWithStaticSignalDefaults(@TempDir Path tempDir) throws Exception {
        Path database = tempDir.resolve("version-fifteen.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE neurons (id TEXT PRIMARY KEY, type TEXT NOT NULL, activation INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE structures (id TEXT PRIMARY KEY, expanded INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("INSERT INTO neurons (id, type, activation) VALUES ('neuron-1', 'EXCITATORY', 0)");
            statement.executeUpdate("INSERT INTO structures (id, expanded) VALUES ('structure-1', 0)");
            statement.execute("PRAGMA user_version = 15");

            SqliteSchema.migrate(connection);

            assertEquals(Set.of("id", "type", "activation", "signal_strength", "activation_threshold",
                            "active_ticks", "delay_ticks", "name", "activation_mode", "signal_type"),
                    columnNames(statement, "neurons"));
            assertTrue(columnNames(statement, "structures").contains("signal_type"));
            // IDE не може вивести колонку зі статичного DDL: вона створюється міграцією SqliteSchema.
            try (ResultSet resultSet = statement.executeQuery("SELECT * FROM neurons WHERE id = 'neuron-1'")) {
                assertTrue(resultSet.next());
                assertEquals("STATIC", resultSet.getString("signal_type"));
            }
            try (ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(resultSet.next());
                assertEquals(16, resultSet.getInt(1));
            }
        }
    }

    /** Повертає імена стовпців таблиці для перевірки фактичної схеми після міграції. */
    private static Set<String> columnNames(Statement statement, String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (resultSet.next()) {
                names.add(resultSet.getString("name"));
            }
        }
        return Set.copyOf(names);
    }
}
