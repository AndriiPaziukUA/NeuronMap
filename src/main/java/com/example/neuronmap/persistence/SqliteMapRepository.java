package com.example.neuronmap.persistence;

import com.example.neuronmap.model.NeuronMapModel;

import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Реалізує постійне сховище карти на SQLite та керує транзакціями, параметрами камери й налаштуваннями симуляції.
 */
public final class SqliteMapRepository implements MapRepository {

    private final Path databasePath;
    private final java.sql.Connection connection;
    private final SqliteSettingsStore settings;
    private final SqliteMapLoader loader;
    private final SqliteMapWriter writer;
    private boolean closed;

    /**
     * Створює екземпляр SqliteMapRepository та зберігає передані залежності, потрібні для його роботи.
     */
    public SqliteMapRepository() {
        this(DatabasePathResolver.resolve());
    }

    /**
     * Створює екземпляр SqliteMapRepository та зберігає передані залежності, потрібні для його роботи.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     */
    public SqliteMapRepository(Path databasePath) {
        if (databasePath == null) {
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
     * Перевіряє, чи persistent за поточного стану компонента.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    @Override
    public boolean isPersistent() {
        return !closed;
    }

    /**
     * Повертає шлях до файлу бази даних.
     *
     * @return шлях до файлу бази даних.
     */
    @Override
    public Path databasePath() {
        return databasePath;
    }

    /**
     * Завантажує simulation tick millis із відповідного джерела даних.
     *
     * @param fallbackMillis резервна тривалість такту, якщо збереженого значення немає.
     */
    @Override
    public double loadSimulationTickMillis(double fallbackMillis) {
        return settings.loadSimulationTickMillis(fallbackMillis);
    }

    /**
     * Зберігає simulation tick millis у відповідному сховищі.
     *
     * @param millis тривалість такту в мілісекундах.
     */
    @Override
    public synchronized void saveSimulationTickMillis(double millis) {
        ensureOpen();
        settings.saveSimulationTickMillis(millis);
        touchDatabaseFile();
    }

    /**
     * Завантажує з бази даних останні збережені параметри камери.
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
     * Завантажує вміст бази даних у передану модель карти.
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
     * Зберігає модель карти та стан камери в транзакції.
     *
     * @param model модель карти нейронів.
     * @param cameraState стан камери, який потрібно зберегти разом із картою.
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
     * Закриває з’єднання SQLite та звільняє ресурси репозиторію.
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
     * Забезпечує наявність файлу бази даних і оновлює його часову мітку за потреби.
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
     * Перевіряє, що репозиторій не закрито перед зверненням до бази даних.
     */
    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("SQLite repository is closed");
        }
    }

    /**
     * Виконує відкат незавершеної транзакції, не маскуючи первинну помилку.
     */
    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {

        }
    }

    /**
     * Відновлює початковий режим автоматичного підтвердження транзакцій.
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
     * Створює PersistenceException із повідомленням і першопричиною помилки SQLite.
     *
     * @param message значення «message», яке використовується в цьому методі.
     * @param cause першопричина помилки, яку потрібно передати разом із повідомленням.
     */
    private PersistenceException failure(
            String message,
            Throwable cause
    ) {
        return new PersistenceException(
                message + " Database: " + databasePath,
                cause
        );
    }
}
