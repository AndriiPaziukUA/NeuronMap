package com.example.neuronmap.persistence;

import com.example.neuronmap.model.NeuronMapModel;

import java.nio.file.Path;
import java.sql.SQLException;

/** Coordinates SQLite schema, loading, writing and scalar settings. */
public final class SqliteMapRepository implements MapRepository {

    private final Path databasePath;
    private final java.sql.Connection connection;
    private final SqliteSettingsStore settings;
    private final SqliteMapLoader loader;
    private final SqliteMapWriter writer;
    private boolean closed;

    public SqliteMapRepository() {
        this(DatabasePathResolver.resolve());
    }

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

    @Override
    public boolean isPersistent() {
        return !closed;
    }

    @Override
    public Path databasePath() {
        return databasePath;
    }

    @Override
    public double loadSimulationTickMillis(double fallbackMillis) {
        return settings.loadSimulationTickMillis(fallbackMillis);
    }

    @Override
    public synchronized void saveSimulationTickMillis(double millis) {
        ensureOpen();
        settings.saveSimulationTickMillis(millis);
        touchDatabaseFile();
    }

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

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("SQLite repository is closed");
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original persistence error.
        }
    }

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
