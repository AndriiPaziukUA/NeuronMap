package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє, що репозиторій SQLite відрізняє пошкоджені налаштування від помилок сховища та зберігає атомарність запису.
 */
final class SqliteMapRepositoryFailureTest {

    /**
     * Перевіряє повернення типового стану камери, якщо збережені числові значення пошкоджені.
     *
     * @param tempDir тимчасовий каталог для ізольованої бази даних.
     * @throws Exception якщо підготовка тестових даних у SQLite завершується невдало.
     */
    @Test
    void fallsBackToDefaultCameraStateForMalformedValues(@TempDir Path tempDir) throws Exception {
        Path databasePath = tempDir.resolve("malformed-camera.db");

        try (SqliteMapRepository repository = new SqliteMapRepository(databasePath);
             Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)) {
            writeSetting(connection, "zoom", "not-a-number");
            writeSetting(connection, "panX", "15.0");
            writeSetting(connection, "panY", "20.0");

            assertEquals(CameraState.defaultState(), repository.loadCameraState());
        }
    }

    /**
     * Перевіряє, що нескінченний або нечисловий масштаб не потрапляє до стану камери.
     *
     * @param tempDir тимчасовий каталог для ізольованої бази даних.
     * @throws Exception якщо підготовка тестових даних у SQLite завершується невдало.
     */
    @Test
    void fallsBackToDefaultCameraStateForNonFiniteZoom(@TempDir Path tempDir) throws Exception {
        Path databasePath = tempDir.resolve("non-finite-camera.db");

        try (SqliteMapRepository repository = new SqliteMapRepository(databasePath);
             Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)) {
            writeSetting(connection, "zoom", "NaN");
            writeSetting(connection, "panX", "15.0");
            writeSetting(connection, "panY", "20.0");

            assertEquals(CameraState.defaultState(), repository.loadCameraState());
        }
    }

    /**
     * Перевіряє, що помилка читання таблиці налаштувань не маскується типовим станом камери.
     *
     * @param tempDir тимчасовий каталог для ізольованої бази даних.
     * @throws Exception якщо підготовка пошкодженої схеми завершується невдало.
     */
    @Test
    void propagatesSqliteErrorsWhenLoadingCameraState(@TempDir Path tempDir) throws Exception {
        Path databasePath = tempDir.resolve("missing-settings.db");

        try (SqliteMapRepository repository = new SqliteMapRepository(databasePath);
             Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE settings");

            assertThrows(PersistenceException.class, repository::loadCameraState);
        }
    }

    /**
     * Перевіряє, що невдала операція запису відкочується, а репозиторій після помилки може читати попередні дані.
     *
     * @param tempDir тимчасовий каталог для ізольованої бази даних.
     * @throws Exception якщо підготовка тригера або читання результату тесту завершується невдало.
     */
    @Test
    void rollsBackFailedSaveAndRemainsUsable(@TempDir Path tempDir) throws Exception {
        Path databasePath = tempDir.resolve("rollback.db");

        try (SqliteMapRepository repository = new SqliteMapRepository(databasePath)) {
            NeuronMapModel original = new NeuronMapModel();
            var originalNeuron = original.createNeuron(NeuronType.EXCITATORY, 10.0, 20.0);
            repository.save(original, CameraState.defaultState());

            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TRIGGER fail_neuron_insert
                        BEFORE INSERT ON neurons
                        BEGIN
                            SELECT RAISE(ABORT, 'forced failure');
                        END
                        """);
            }

            NeuronMapModel replacement = new NeuronMapModel();
            replacement.createNeuron(NeuronType.INHIBITORY, 30.0, 40.0);
            assertThrows(
                    PersistenceException.class,
                    () -> repository.save(replacement, new CameraState(2.0, 0.0, 0.0))
            );

            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER fail_neuron_insert");
            }

            NeuronMapModel restored = new NeuronMapModel();
            repository.loadInto(restored);
            assertEquals(1, restored.neurons().size());
            assertEquals(originalNeuron.id(), restored.neurons().iterator().next().id());
        }
    }

    /**
     * Записує одне значення налаштування без використання файлового інтерфейсу застосунку.
     *
     * @param connection відкрите з'єднання з тестовою базою даних.
     * @param key ключ налаштування.
     * @param value значення налаштування.
     * @throws Exception якщо SQLite не може записати значення.
     */
    private static void writeSetting(Connection connection, String key, String value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO settings (key, value)
                VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }
}
