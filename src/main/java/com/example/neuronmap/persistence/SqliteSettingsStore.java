package com.example.neuronmap.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Читає й записує налаштування проєкту в таблиці SQLite, включно зі швидкістю тактів симуляції.
 */
public final class SqliteSettingsStore {

    public static final String SIMULATION_TICK_MILLIS_KEY =
            "simulation_tick_millis";

    private final java.sql.Connection connection;

    /**
     * Створює екземпляр SqliteSettingsStore та зберігає передані залежності, потрібні для його роботи.
     *
     * @param connection напрямлений зв’язок між нейронами.
     */
    public SqliteSettingsStore(java.sql.Connection connection) {
        if (connection == null) {

            throw new IllegalArgumentException("connection must not be null");
        }
        this.connection = connection;
    }

    /**
     * Зчитує значення налаштування за ключем із таблиці налаштувань.
     *
     * @param key ключ налаштування або перекладу.
     */
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

    /**
     * Записує значення налаштування за ключем у таблицю налаштувань.
     *
     * @param key ключ налаштування або перекладу.
     * @param value значення, яке потрібно зберегти або перевірити.
     */
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

    /**
     * Завантажує simulation tick millis із відповідного джерела даних.
     *
     * @param fallbackMillis резервна тривалість такту, якщо збереженого значення немає.
     */
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

    /**
     * Зберігає simulation tick millis у відповідному сховищі.
     *
     * @param millis тривалість такту в мілісекундах.
     */
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
