package com.example.neuronmap.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевіряє міграції старих версій схеми SQLite до актуальної структури.
 */
class SqliteSchemaMigrationTest {

    @Test
    void migratesLegacyNeuronCoordinates(
            @TempDir Path tempDir
    ) throws Exception {
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

        try (MapRepository ignored =
                     new SqliteMapRepository(db)) {

        }

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + db.toAbsolutePath()
        ); Statement statement = connection.createStatement()) {

            assertFalse(
                    hasColumn(
                            statement,
                            "neurons",
                            "x"
                    )
            );
            assertFalse(
                    hasColumn(
                            statement,
                            "neurons",
                            "y"
                    )
            );

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT neuron_id, x, y "
                            + "FROM neuron_presentations"
            )) {
                assertTrue(resultSet.next());
                assertEquals("legacy", resultSet.getString("neuron_id"));
                assertEquals(123.0, resultSet.getDouble("x"));
                assertEquals(456.0, resultSet.getDouble("y"));
            }
        }
    }

    private static boolean hasColumn(
            Statement statement,
            String table,
            String column
    ) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(
                "PRAGMA table_info(" + table + ")"
        )) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(
                        resultSet.getString("name")
                )) {
                    return true;
                }
            }
        }
        return false;
    }
}
