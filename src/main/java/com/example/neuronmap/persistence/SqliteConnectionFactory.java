package com.example.neuronmap.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteConnectionFactory {

    private SqliteConnectionFactory() {
    }

    public static Connection open(Path databasePath) {
        try {
            Path parent = databasePath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Class.forName("org.sqlite.JDBC");

            Connection connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + databasePath.toAbsolutePath()
            );

            configure(connection);

            return connection;
        } catch (SQLException
                 | ClassNotFoundException
                 | IOException exception) {

            throw new PersistenceException(
                    "Не вдалося відкрити SQLite: "
                            + databasePath,
                    exception
            );
        }
    }

    private static void configure(
            Connection connection
    ) throws SQLException {
        try (Statement statement =
                     connection.createStatement()) {

            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }
}
