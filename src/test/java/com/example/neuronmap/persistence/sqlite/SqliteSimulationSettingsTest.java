package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.persistence.MapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє збереження та повторне читання тривалості такту симуляції з таблиці налаштувань. */
class SqliteSimulationSettingsTest {

    /** Перевіряє, що збережена тривалість такту замінює резервне значення після повторного відкриття репозиторію. */
    @Test
    void simulationTickMillisRoundTripsThroughSettingsTable(@TempDir Path tempDir) {
        Path db = tempDir.resolve("simulation-settings.db");

        try (MapRepository repository = new SqliteMapRepository(db)) {
            assertEquals(650.0, repository.loadSimulationTickMillis(650.0));
            repository.saveSimulationTickMillis(275.0);
        }

        try (MapRepository repository = new SqliteMapRepository(db)) {
            assertEquals(275.0, repository.loadSimulationTickMillis(650.0));
        }
    }

    /** Перевіряє повернення резервної тривалості, якщо в SQLite збережено значення, яке не можна розібрати як число. */
    @Test
    void invalidPersistedSimulationTickMillisFallsBackToDefault(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("invalid-simulation-settings.db");

        try (MapRepository repository = new SqliteMapRepository(db)) {
            // Створює таблицю налаштувань перед внесенням навмисно некоректного значення.
        }

        try (java.sql.Connection connection = SqliteConnectionFactory.open(db);
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO settings (key, value) VALUES (?, ?) " +
                             "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            statement.setString(1, "simulation_tick_millis");
            statement.setString(2, "not-a-number");
            statement.executeUpdate();
        }

        try (MapRepository repository = new SqliteMapRepository(db)) {
            assertEquals(650.0, repository.loadSimulationTickMillis(650.0));
        }
    }
}
