package com.example.neuronmap.persistence;

import com.example.neuronmap.model.NeuronMapModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Keeps a new project in memory until its first real board element is persisted. */
public final class TransientProjectRepository implements MapRepository {

    private final Path databasePath;
    private MapRepository delegate;
    private CameraState pendingCameraState = CameraState.defaultState();
    private Double pendingSimulationTickMillis;
    private boolean closed;

    public TransientProjectRepository(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public Path databasePath() {
        return databasePath;
    }

    @Override
    public boolean isPersistent() {
        return delegate != null && delegate.isPersistent();
    }

    @Override
    public CameraState loadCameraState() {
        return delegate == null
                ? pendingCameraState
                : delegate.loadCameraState();
    }

    @Override
    public double loadSimulationTickMillis(double fallbackMillis) {
        if (delegate == null) {
            return pendingSimulationTickMillis == null
                    ? fallbackMillis
                    : pendingSimulationTickMillis;
        }
        return delegate.loadSimulationTickMillis(fallbackMillis);
    }

    @Override
    public void loadInto(NeuronMapModel model) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        if (delegate == null) {
            model.clear();
            return;
        }
        delegate.loadInto(model);
    }

    @Override
    public void saveSimulationTickMillis(double millis) {
        ensureOpen();
        if (delegate == null) {
            pendingSimulationTickMillis = millis;
            return;
        }
        delegate.saveSimulationTickMillis(millis);
    }

    @Override
    public synchronized void save(
            NeuronMapModel model,
            CameraState cameraState
    ) {
        ensureOpen();
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        pendingCameraState = Objects.requireNonNull(
                cameraState,
                "cameraState"
        );

        if (delegate == null && model.isEmpty()) {
            return;
        }

        if (delegate == null) {
            materialize();
        }

        delegate.save(model, pendingCameraState);
        if (pendingSimulationTickMillis != null) {
            delegate.saveSimulationTickMillis(pendingSimulationTickMillis);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;

        if (delegate != null) {
            delegate.close();
            delegate = null;
            pendingSimulationTickMillis = null;
            return;
        }

        pendingCameraState = CameraState.defaultState();
        pendingSimulationTickMillis = null;
    }

    private void materialize() {
        try {
            prepareProjectDirectory();
            delegate = new SqliteMapRepository(databasePath);
        } catch (IOException exception) {
            throw new PersistenceException(
                    "Не вдалося підготувати папку проєкту.",
                    exception
            );
        }
    }

    private void prepareProjectDirectory() throws IOException {
        Path directory = databasePath.getParent();
        if (directory == null) {
            throw new IOException("Project database has no parent directory");
        }

        if (Files.isRegularFile(databasePath)) {
            throw new IOException(
                    "Project database already exists: " + databasePath
            );
        }

        if (Files.notExists(directory)) {
            Files.createDirectories(directory);
            return;
        }

        clearDirectory(directory);
    }

    private static void clearDirectory(Path directory) throws IOException {
        List<Path> paths;
        try (var stream = Files.walk(directory)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }

        for (Path path : paths) {
            if (!path.equals(directory)) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Project repository is closed");
        }
    }
}
