package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Створює та мігрує схему SQLite, додаючи потрібні таблиці й стовпці для актуальної версії даних.
 */
public final class SqliteSchema {

    private static final int CURRENT_VERSION = 4;

    /** Не дозволяє створювати екземпляри утиліти для керування схемою. */
    private SqliteSchema() {
    }

    /**
     * Перевіряє версію схеми й застосовує необхідні зміни, не записуючи версію повторно для актуальної бази.
     * Бази, створені новішою версією застосунку, відхиляються до будь-якої модифікації схеми.
     *
     * @param connection відкрите з'єднання з базою SQLite.
     * @throws PersistenceException якщо версія схеми несумісна або підготовка бази не вдалася.
     */
    public static void migrate(Connection connection) {
        try {
            int versionAtStart = readVersion(connection);
            if (versionAtStart > CURRENT_VERSION) {
                throw new SQLException(
                        "Версія схеми SQLite " + versionAtStart
                                + " новіша за підтримувану версію " + CURRENT_VERSION + "."
                );
            }

            createBaseSchema(connection);
            migrateLegacyNeuronLayout(connection);
            ensureNeuronSettingsColumns(connection);
            ensureDirectionColumn(connection);
            ensureVersion(connection, versionAtStart);
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "Не вдалося підготувати схему SQLite.",
                    exception
            );
        }
    }

    /**
     * Створює таблиці базової схеми лише тоді, коли вони ще не існують.
     *
     * @param connection відкрите з'єднання з базою SQLite.
     * @throws SQLException якщо SQLite не може створити таблицю.
     */
    private static void createBaseSchema(Connection connection)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS neurons (
                        id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        activation INTEGER NOT NULL DEFAULT 0,
                        signal_strength INTEGER NOT NULL DEFAULT 1,
                        activation_threshold INTEGER NOT NULL DEFAULT 1
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS neuron_presentations (
                        neuron_id TEXT PRIMARY KEY,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        rotation REAL NOT NULL DEFAULT 0,
                        direction_reversed INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (neuron_id)
                            REFERENCES neurons(id)
                            ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS connections (
                        id TEXT PRIMARY KEY,
                        source_id TEXT NOT NULL,
                        target_id TEXT NOT NULL,
                        FOREIGN KEY (source_id)
                            REFERENCES neurons(id)
                            ON DELETE CASCADE,
                        FOREIGN KEY (target_id)
                            REFERENCES neurons(id)
                            ON DELETE CASCADE,
                        UNIQUE (source_id, target_id),
                        CHECK (source_id <> target_id)
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS groups (
                        id TEXT PRIMARY KEY
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS group_members (
                        group_id TEXT NOT NULL,
                        neuron_id TEXT NOT NULL,
                        PRIMARY KEY (group_id, neuron_id),
                        FOREIGN KEY (group_id)
                            REFERENCES groups(id)
                            ON DELETE CASCADE,
                        FOREIGN KEY (neuron_id)
                            REFERENCES neurons(id)
                            ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS settings (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
        }
    }

    /**
     * Переносить координати нейронів зі старої таблиці до таблиці подань і видаляє застарілі стовпці.
     *
     * @param connection відкрите з'єднання з базою SQLite.
     * @throws SQLException якщо перенесення або зміна таблиці не вдалися.
     */
    private static void migrateLegacyNeuronLayout(Connection connection)
            throws SQLException {
        boolean hasX = columnExists(connection, "neurons", "x");
        boolean hasY = columnExists(connection, "neurons", "y");

        if (!hasX && !hasY) {
            return;
        }

        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            if (hasX && hasY) {
                statement.executeUpdate("""
                        INSERT OR IGNORE INTO neuron_presentations (
                            neuron_id,
                            x,
                            y,
                            rotation,
                            direction_reversed
                        )
                        SELECT id, x, y, 0, 0
                        FROM neurons
                        """);
            }

            if (hasX) {
                statement.executeUpdate("ALTER TABLE neurons DROP COLUMN x");
            }
            if (hasY) {
                statement.executeUpdate("ALTER TABLE neurons DROP COLUMN y");
            }

            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Додає параметри нейронів, яких бракує в старих версіях схеми.
     *
     * @param connection відкрите з'єднання з базою SQLite.
     * @throws SQLException якщо перевірка або зміна таблиці не вдалися.
     */
    private static void ensureNeuronSettingsColumns(Connection connection)
            throws SQLException {
        if (!columnExists(connection, "neurons", "signal_strength")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        ALTER TABLE neurons
                        ADD COLUMN signal_strength
                        INTEGER NOT NULL DEFAULT 1
                        """);
            }
        }

        if (!columnExists(connection, "neurons", "activation_threshold")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        ALTER TABLE neurons
                        ADD COLUMN activation_threshold
                        INTEGER NOT NULL DEFAULT 1
                        """);
            }
        }
    }

    /**
     * Додає стовпець напрямку нейрона, якщо його ще немає в таблиці подань.
     *
     * @param connection відкрите з'єднання з базою SQLite.
     * @throws SQLException якщо перевірка або зміна таблиці не вдалися.
     */
    private static void ensureDirectionColumn(Connection connection)
            throws SQLException {
        if (columnExists(connection, "neuron_presentations", "direction_reversed")) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    ALTER TABLE neuron_presentations
                    ADD COLUMN direction_reversed
                    INTEGER NOT NULL DEFAULT 0
                    """);
        }
    }

    /**
     * Перевіряє наявність стовпця в таблиці без зміни даних.
     *
     * @param connection відкрите з'єднання з базою SQLite.
     * @param table назва таблиці, яку потрібно перевірити.
     * @param column назва стовпця, наявність якого перевіряється.
     * @return {@code true}, якщо стовпець існує; інакше {@code false}.
     * @throws SQLException якщо SQLite не може прочитати опис таблиці.
     */
    private static boolean columnExists(
            Connection connection,
            String table,
            String column
    ) throws SQLException {
        String sql = "PRAGMA table_info(" + table + ")";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Читає записану версію схеми, не змінюючи заголовок бази даних.
     *
     * @param connection відкрите з'єднання з базою SQLite.
     * @return номер версії, записаний у {@code PRAGMA user_version}.
     * @throws SQLException якщо SQLite не повертає значення версії.
     */
    private static int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            if (!resultSet.next()) {
                throw new SQLException("SQLite не повернула значення user_version.");
            }
            return resultSet.getInt(1);
        }
    }

    /**
     * Записує версію схеми лише після першого створення або успішного оновлення старішої схеми.
     *
     * @param connection відкрите з'єднання з базою SQLite.
     * @param versionAtStart версія схеми, прочитана до застосування змін.
     * @throws SQLException якщо SQLite не може записати номер версії.
     */
    private static void ensureVersion(Connection connection, int versionAtStart)
            throws SQLException {
        if (versionAtStart == CURRENT_VERSION) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = " + CURRENT_VERSION);
        }
    }
}
