package com.example.neuronmap.persistence;

import com.example.neuronmap.model.NeuronMapModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Зберігає дані тимчасового проєкту до його матеріалізації в каталозі та не створює каталог за відсутності збережених даних.
 */
public final class TransientProjectRepository implements MapRepository {

    private final Path databasePath;
    private MapRepository delegate;
    private CameraState pendingCameraState = CameraState.defaultState();
    private Double pendingSimulationTickMillis;
    private boolean closed;

    /**
     * Створює екземпляр TransientProjectRepository та зберігає передані залежності, потрібні для його роботи.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     */
    public TransientProjectRepository(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
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
     * Перевіряє, чи persistent за поточного стану компонента.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    @Override
    public boolean isPersistent() {
        return delegate != null && delegate.isPersistent();
    }

    /**
     * Завантажує camera state із відповідного джерела даних.
     */
    @Override
    public CameraState loadCameraState() {
        return delegate == null
                ? pendingCameraState
                : delegate.loadCameraState();
    }

    /**
     * Завантажує simulation tick millis із відповідного джерела даних.
     *
     * @param fallbackMillis резервна тривалість такту, якщо збереженого значення немає.
     */
    @Override
    public double loadSimulationTickMillis(double fallbackMillis) {
        if (delegate == null) {
            return pendingSimulationTickMillis == null
                    ? fallbackMillis
                    : pendingSimulationTickMillis;
        }
        return delegate.loadSimulationTickMillis(fallbackMillis);
    }

    /**
     * Завантажує into із відповідного джерела даних.
     *
     * @param model модель карти нейронів.
     */
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

    /**
     * Зберігає simulation tick millis у відповідному сховищі.
     *
     * @param millis тривалість такту в мілісекундах.
     */
    @Override
    public void saveSimulationTickMillis(double millis) {
        ensureOpen();
        if (delegate == null) {
            pendingSimulationTickMillis = millis;
            return;
        }
        delegate.saveSimulationTickMillis(millis);
    }

    /**
     * Зберігає  у відповідному сховищі.
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

    /**
     * Закриває тимчасове сховище та прибирає створені ним тимчасові ресурси.
     */
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

    /**
     * Створює каталог і постійне сховище лише тоді, коли тимчасовий проєкт потрібно реально записати на диск.
     */
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

    /**
     * Готує каталог тимчасового проєкту перед перенесенням у нього даних.
     */
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

    /**
     * Очищає directory від тимчасових або застарілих значень.
     *
     * @param directory каталог, який потрібно обробити.
     */
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

    /**
     * Забезпечує виконання передумови «open» перед продовженням операції.
     */
    private void ensureOpen() {
        if (closed) {

            throw new IllegalStateException("Project repository is closed");
        }
    }
}
