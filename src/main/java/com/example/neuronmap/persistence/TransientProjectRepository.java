package com.example.neuronmap.persistence;

import com.example.neuronmap.model.NeuronMapModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Тимчасово зберігає стан нового проєкту в памʼяті та створює постійний файл лише за потреби.
 */
public final class TransientProjectRepository implements MapRepository {

    private final Path databasePath;
    private MapRepository delegate;
    private CameraState pendingCameraState = CameraState.defaultState();
    private Double pendingSimulationTickMillis;
    private boolean closed;

    /**
     * Повертає результат операції «тимчасовий проєкт».
     *
     * @param databasePath значення, що визначає база даних шлях для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public TransientProjectRepository(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
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
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    @Override
    public boolean isPersistent() {
        return delegate != null && delegate.isPersistent();
    }

    /**
     * Повертає або знаходить дані, повʼязані з «камера стан».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    @Override
    public CameraState loadCameraState() {
        return delegate == null
                ? pendingCameraState
                : delegate.loadCameraState();
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
        if (delegate == null) {
            return pendingSimulationTickMillis == null
                    ? fallbackMillis
                    : pendingSimulationTickMillis;
        }
        return delegate.loadSimulationTickMillis(fallbackMillis);
    }

    /**
     * Повертає або знаходить дані, повʼязані з «відповідну операцію».
     *
     * @param model модель карти нейронів.
     */
    @Override
    public void loadInto(NeuronMapModel model) {
        if (model == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("model must not be null");
        }
        if (delegate == null) {
            model.clear();
            return;
        }
        delegate.loadInto(model);
    }

    /**
     * Зберігає дані, повʼязані з «такт», у відповідному сховищі.
     *
     * @param millis значення, що визначає відповідну операцію для цієї операції.
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
        if (model == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
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
     * Виконує операцію «відповідну операцію».
     */
    private void materialize() {
        try {
            prepareProjectDirectory();
            delegate = new SqliteMapRepository(databasePath);
        } catch (IOException exception) {
            /**
             * Повертає результат операції «виняток».
             *
             * @param exception помилка, яку потрібно обробити.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new PersistenceException(
                    "Не вдалося підготувати папку проєкту.",
                    exception
            );
        }
    }

    /**
     * Виконує операцію «проєкт каталог».
     */
    private void prepareProjectDirectory() throws IOException {
        Path directory = databasePath.getParent();
        if (directory == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IOException("Project database has no parent directory");
        }

        if (Files.isRegularFile(databasePath)) {
            /**
             * Повертає результат операції «виняток».
             *
             * @param databasePath значення, що визначає база даних шлях для цієї операції.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
     * Видаляє або скидає дані, повʼязані з «каталог».
     *
     * @param directory каталог для пошуку чи збереження.
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
     * Виконує операцію «відкрити».
     */
    private void ensureOpen() {
        if (closed) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException("Project repository is closed");
        }
    }
}
