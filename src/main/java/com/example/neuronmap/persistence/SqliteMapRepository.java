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

    public SqliteMapRepository() {
        this(DatabasePathResolver.resolve());
    }

    public SqliteMapRepository(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("databasePath must not be null");
        }

        this.databasePath = databasePath.toAbsolutePath().normalize();
        LegacyDatabaseMigrator.migrateIfNeeded(this.databasePath);
        this.connection = SqliteConnectionFactory.open(this.databasePath);
        SqliteSchema.migrate(this.connection);

        this.settings = new SqliteSettingsStore(this.connection);
        this.loader = new SqliteMapLoader(this.connection);
        this.writer = new SqliteMapWriter(this.connection, settings);
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
        settings.saveSimulationTickMillis(millis);
    }

    @Override
    public CameraState loadCameraState() {
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
        try {
            connection.setAutoCommit(false);
            writer.save(model, cameraState);
            connection.commit();
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
    public void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            throw failure("Не вдалося закрити SQLite.", exception);
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
