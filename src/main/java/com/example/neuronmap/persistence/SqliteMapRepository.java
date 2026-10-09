package com.example.neuronmap.persistence;

import com.example.neuronmap.model.NeuronMapModel;

import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Реалізує контракт сховища карти за допомогою бази даних SQLite.
 */
public final class SqliteMapRepository implements MapRepository {

    private final Path databasePath;
    private final java.sql.Connection connection;
    private final SqliteSettingsStore settings;
    private final SqliteMapLoader loader;
    private final SqliteMapWriter writer;
    private boolean closed;

    /**
     * Повертає результат операції «SQLite карта».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public SqliteMapRepository() {
        this(DatabasePathResolver.resolve());
    }

    /**
     * Повертає результат операції «SQLite карта».
     *
     * @param databasePath значення, що визначає база даних шлях для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public SqliteMapRepository(Path databasePath) {
        if (databasePath == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("databasePath must not be null");
        }

        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.connection = SqliteConnectionFactory.open(this.databasePath);
        SqliteSchema.migrate(this.connection);

        this.settings = new SqliteSettingsStore(this.connection);
        this.loader = new SqliteMapLoader(this.connection);
        this.writer = new SqliteMapWriter(this.connection, settings);
    }

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    @Override
    public boolean isPersistent() {
        return !closed;
    }

    /**
     * Повертає результат операції «база даних шлях».
     *
     * @return шлях до відповідного файлу або каталогу.
     */
    @Override
    public Path databasePath() {
        return databasePath;
    }

    /**
     * Повертає або знаходить дані, повʼязані з «такт».
     *
     * @param fallbackMillis значення, що визначає резервний варіант для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    @Override
    public double loadSimulationTickMillis(double fallbackMillis) {
        return settings.loadSimulationTickMillis(fallbackMillis);
    }

    /**
     * Зберігає дані, повʼязані з «такт», у відповідному сховищі.
     *
     * @param millis значення, що визначає відповідну операцію для цієї операції.
     */
    @Override
    public synchronized void saveSimulationTickMillis(double millis) {
        ensureOpen();
        settings.saveSimulationTickMillis(millis);
        touchDatabaseFile();
    }

    /**
     * Повертає або знаходить дані, повʼязані з «камера стан».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    @Override
    public CameraState loadCameraState() {
        ensureOpen();
        try {
            String zoom = settings.load("zoom");
            String panX = settings.load("panX");
            String panY = settings.load("panY");

            return new CameraState(
                    Double.parseDouble(zoom),
                    Double.parseDouble(panX),
                    Double.parseDouble(panY)
            );
        } catch (Exception exception) {
            return CameraState.defaultState();
        }
    }

    /**
     * Повертає або знаходить дані, повʼязані з «відповідну операцію».
     *
     * @param model модель карти нейронів.
     */
    @Override
    public void loadInto(NeuronMapModel model) {
        ensureOpen();
        try {
            loader.loadInto(model);
        } catch (SQLException exception) {
            throw failure(
                    "Не вдалося завантажити карту з SQLite.",
                    exception
            );
        }
    }

    /**
     * Зберігає дані, повʼязані з «потрібні дані», у відповідному сховищі.
     *
     * @param model модель карти нейронів.
     *
     * @param cameraState значення, що визначає камера стан для цієї операції.
     */
    @Override
    public synchronized void save(
            NeuronMapModel model,
            CameraState cameraState
    ) {
        ensureOpen();
        try {
            connection.setAutoCommit(false);
            writer.save(model, cameraState);
            connection.commit();
            touchDatabaseFile();
        } catch (SQLException exception) {
            rollbackQuietly();
            throw failure(
                    "Не вдалося зберегти карту в SQLite.",
                    exception
            );
        } finally {
            restoreAutoCommit();
        }
    }

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            connection.close();
        } catch (SQLException exception) {
            throw failure("Не вдалося закрити SQLite.", exception);
        }
    }

    /**
     * Виконує операцію «база даних файл».
     */
    private void touchDatabaseFile() {
        try {
            java.nio.file.Files.setLastModifiedTime(
                    databasePath,
                    java.nio.file.attribute.FileTime.from(java.time.Instant.now())
            );
        } catch (java.io.IOException exception) {
            throw failure(
                    "Не вдалося оновити час зміни SQLite.",
                    exception
            );
        }
    }

    /**
     * Виконує операцію «відкрити».
     */
    private void ensureOpen() {
        if (closed) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException("SQLite repository is closed");
        }
    }

    /**
     * Виконує операцію «відповідну операцію».
     */
    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original persistence error.
        }
    }

    /**
     * Задає або оновлює значення, повʼязані з «відповідну операцію».
     */
    private void restoreAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException exception) {
            throw failure(
                    "Не вдалося відновити режим транзакції SQLite.",
                    exception
            );
        }
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param message повідомлення для показу чи журналювання.
     *
     * @param cause значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private PersistenceException failure(
            String message,
            Throwable cause
    ) {
        /**
         * Повертає результат операції «виняток».
         *
         * @param databasePath значення, що визначає база даних шлях для цієї операції.
         *
         * @param cause значення, що визначає відповідну операцію для цієї операції.
         *
         * @return значення або обʼєкт, визначений описаною операцією.
         */
        return new PersistenceException(
                message + " Database: " + databasePath,
                cause
        );
    }
}
