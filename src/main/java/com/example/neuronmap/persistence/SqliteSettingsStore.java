package com.example.neuronmap.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Зберігає параметри конкретного проєкту в базі даних SQLite.
 */
public final class SqliteSettingsStore {

    public static final String SIMULATION_TICK_MILLIS_KEY =
            "simulation_tick_millis";

    private final java.sql.Connection connection;

    /**
     * Повертає результат операції «SQLite налаштування зберігати».
     *
     * @param connection звʼязок між нейронами.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public SqliteSettingsStore(java.sql.Connection connection) {
        if (connection == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("connection must not be null");
        }
        this.connection = connection;
    }

    /**
     * Повертає або знаходить дані, повʼязані з «потрібні дані».
     *
     * @param key ключ для пошуку або збереження значення.
     *
     * @return текстове значення, сформоване або знайдене методом.
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
     * Зберігає дані, повʼязані з «потрібні дані», у відповідному сховищі.
     *
     * @param key ключ для пошуку або збереження значення.
     *
     * @param value значення, яке потрібно передати або зберегти.
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
     * Повертає або знаходить дані, повʼязані з «такт».
     *
     * @param fallbackMillis значення, що визначає резервний варіант для цієї операції.
     *
     * @return числове значення, визначене методом.
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
            /**
             * Повертає результат операції «виняток».
             *
             * @param exception помилка, яку потрібно обробити.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new PersistenceException(
                    "Не вдалося завантажити налаштування SQLite.",
                    exception
            );
        }
    }

    /**
     * Зберігає дані, повʼязані з «такт», у відповідному сховищі.
     *
     * @param millis значення, що визначає відповідну операцію для цієї операції.
     */
    public void saveSimulationTickMillis(double millis) {
        try {
            write(
                    SIMULATION_TICK_MILLIS_KEY,
                    Double.toString(millis)
            );
        } catch (SQLException exception) {
            /**
             * Повертає результат операції «виняток».
             *
             * @param exception помилка, яку потрібно обробити.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new PersistenceException(
                    "Не вдалося зберегти швидкість такту.",
                    exception
            );
        }
    }
}
