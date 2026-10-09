package com.example.neuronmap.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Відкриває й налаштовує SQLite-з’єднання для сховища даних карти.
 */
public final class SqliteConnectionFactory {

    private SqliteConnectionFactory() {
    }

    /**
     * Відкриває SQLite-з’єднання для заданого файлу бази даних і застосовує необхідні параметри з’єднання.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     */
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

    /**
     * Установлює параметри SQLite-з’єднання, потрібні для стабільної роботи сховища.
     *
     * @param connection напрямлений зв’язок між нейронами.
     */
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
