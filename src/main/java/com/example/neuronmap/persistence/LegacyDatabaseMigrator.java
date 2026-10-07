package com.example.neuronmap.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;

/** One-time migration of the legacy per-user SQLite database. */
public final class LegacyDatabaseMigrator {

    private LegacyDatabaseMigrator() {
    }

    public static void migrateIfNeeded(Path databasePath) {
        if (databasePath == null || Files.exists(databasePath)) {
            return;
        }

        Path legacyPath = Path.of(System.getProperty("user.home"))
                .resolve(".neuronmap")
                .resolve("neuronmap.db");

        if (!Files.exists(legacyPath)) {
            return;
        }

        try {
            try (java.sql.Connection legacyConnection =
                         DriverManager.getConnection(
                                 "jdbc:sqlite:" +
                                         legacyPath.toAbsolutePath()
                         );
                 Statement statement = legacyConnection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }

            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.copy(legacyPath, databasePath);
        } catch (Exception exception) {
            throw new PersistenceException(
                    "Не вдалося перенести стару SQLite базу.",
                    exception
            );
        }
    }
}
