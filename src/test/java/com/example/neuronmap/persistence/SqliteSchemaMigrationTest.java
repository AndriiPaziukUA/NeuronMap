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
 * Перевіряє перенесення координат зі старої структури таблиць.
 */
class SqliteSchemaMigrationTest {

    /**
     * Перевіряє перенесення координат нейронів зі старих стовпців до таблиці представлень.
     *
     * @param tempDir значення, що визначає відповідну операцію для цієї операції.
     */
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
            // Opening the repository performs the migration.
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

    /**
     * Перевіряє, чи виконується умова «стовпець».
     *
     * @param statement SQL-оператор.
     *
     * @param table значення, що визначає таблиця для цієї операції.
     *
     * @param column значення, що визначає стовпець для цієї операції.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
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
