package com.example.neuronmap.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Перевіряє контракт схеми SQLite для таблиці нейронів.
 */
final class SqliteNeuronSchemaContractTest {

    @Test
    void neuronTableContainsAllPersistedNeuronState(
            @TempDir Path tempDir
    ) throws Exception {
        Path database = tempDir.resolve("schema-contract.db");

        try (SqliteMapRepository ignored =
                     new SqliteMapRepository(database);
             Connection connection = DriverManager.getConnection(
                     "jdbc:sqlite:" + database.toAbsolutePath()
             );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "PRAGMA table_info(neurons)"
             )) {

            List<String> columns = new ArrayList<>();
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }

            assertEquals(
                    List.of(
                            "id",
                            "type",
                            "activation",
                            "signal_strength",
                            "activation_threshold"
                    ),
                    columns
            );
        }
    }
}
