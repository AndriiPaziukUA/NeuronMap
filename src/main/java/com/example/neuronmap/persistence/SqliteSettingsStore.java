package com.example.neuronmap.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Owns scalar application settings stored in the SQLite settings table. */
public final class SqliteSettingsStore {

    public static final String SIMULATION_TICK_MILLIS_KEY =
            "simulation_tick_millis";

    private final java.sql.Connection connection;

    public SqliteSettingsStore(java.sql.Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        this.connection = connection;
    }

    public String load(String key) throws SQLException {
        String sql = """
                SELECT value
                FROM settings
                WHERE key = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? resultSet.getString("value")
                        : null;
            }
        }
    }

    public void write(String key, String value) throws SQLException {
        String sql = """
                INSERT INTO settings (key, value)
                VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE
                SET value = excluded.value
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    public double loadSimulationTickMillis(double fallbackMillis) {
        try {
            String value = load(SIMULATION_TICK_MILLIS_KEY);
            if (value == null) {
                return fallbackMillis;
            }

            double millis = Double.parseDouble(value);
            return Double.isFinite(millis) && millis > 0.0
                    ? millis
                    : fallbackMillis;
        } catch (NumberFormatException exception) {
            return fallbackMillis;
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "Не вдалося завантажити налаштування SQLite.",
                    exception
            );
        }
    }

    public void saveSimulationTickMillis(double millis) {
        try {
            write(
                    SIMULATION_TICK_MILLIS_KEY,
                    Double.toString(millis)
            );
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "Не вдалося зберегти швидкість такту.",
                    exception
            );
        }
    }
}
