package com.example.neuronmap.persistence.map;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.PersistenceException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Зберігає дані тимчасового проєкту до його матеріалізації в каталозі та відкладає створення постійного сховища.
 */
public final class TransientProjectRepository implements MapRepository {

    private final Path databasePath;
    private final Supplier<? extends MapRepository> persistentRepositoryFactory;
    private MapRepository delegate;
    private CameraState pendingCameraState = CameraState.defaultState();
    private Double pendingSimulationTickMillis;
    private boolean closed;

    /**
     * Створює тимчасове сховище, відкладаючи створення постійного репозиторію до першого запису даних.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     * @param persistentRepositoryFactory постачальник постійного репозиторію, який викликається під час матеріалізації.
     */
    public TransientProjectRepository(
            Path databasePath,
            Supplier<? extends MapRepository> persistentRepositoryFactory
    ) {
        this.databasePath = normalizeDatabasePath(databasePath);
        this.persistentRepositoryFactory = Objects.requireNonNull(
                persistentRepositoryFactory,
                "persistentRepositoryFactory"
        );
    }

    /**
     * Нормалізує шлях до бази даних, щоб усі компоненти працювали з одним абсолютним шляхом.
     *
     * @param databasePath початковий шлях до бази даних.
     * @return абсолютний нормалізований шлях.
     */
    private static Path normalizeDatabasePath(Path databasePath) {
        return Objects.requireNonNull(databasePath, "databasePath")
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
     * Повертає ознаку постійного збереження з урахуванням стану делегованого репозиторію.
     *
     * @return {@code true}, якщо постійний репозиторій створено й він підтримує постійне збереження.
     */
    @Override
    public boolean isPersistent() {
        return delegate != null && delegate.isPersistent();
    }

    /**
     * Повертає збережений або відкладений стан камери.
     *
     * @return поточний стан камери.
     */
    @Override
    public CameraState loadCameraState() {
        return delegate == null
                ? pendingCameraState
                : delegate.loadCameraState();
    }

    /**
     * Повертає збережену або відкладену тривалість такту симуляції.
     *
     * @param fallbackMillis резервна тривалість такту, якщо збереженого значення немає.
     * @return збережене значення або передане резервне значення.
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
     * Завантажує дані карти в задану модель або очищає її, якщо проєкт ще не матеріалізовано.
     *
     * @param model модель карти нейронів.
     * @throws IllegalArgumentException якщо модель не задано.
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
     * Зберігає тривалість такту симуляції у відкладеному стані або в постійному сховищі.
     *
     * @param millis тривалість такту в мілісекундах.
     * @throws IllegalStateException якщо репозиторій уже закрито.
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
     * Зберігає модель і стан камери, матеріалізуючи проєкт лише за наявності даних карти.
     *
     * @param model модель карти нейронів.
     * @param cameraState стан камери, який потрібно зберегти разом із картою.
     * @throws IllegalArgumentException якщо модель не задано.
     * @throws NullPointerException якщо стан камери не задано.
     * @throws IllegalStateException якщо репозиторій уже закрито.
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
     * Закриває тимчасове сховище та звільняє ресурси делегованого постійного репозиторію, якщо він існує.
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
     * Готує каталог і створює постійний репозиторій через переданий постачальник.
     *
     * @throws PersistenceException якщо не вдалося підготувати каталог проєкту.
     */
    private void materialize() {
        try {
            prepareProjectDirectory();
            delegate = Objects.requireNonNull(
                    persistentRepositoryFactory.get(),
                    "persistentRepositoryFactory returned null"
            );
        } catch (IOException exception) {
            throw new PersistenceException(
                    "Не вдалося підготувати папку проєкту.",
                    exception
            );
        }
    }

    /**
     * Готує каталог тимчасового проєкту перед перенесенням у нього даних.
     *
     * @throws IOException якщо каталог неможливо створити або підготувати.
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
     * Видаляє вміст каталогу, залишаючи сам каталог для подальшої матеріалізації проєкту.
     *
     * @param directory каталог, який потрібно очистити.
     * @throws IOException якщо один із вкладених шляхів неможливо видалити.
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
     * Перевіряє, що операцію виконують до закриття репозиторію.
     *
     * @throws IllegalStateException якщо репозиторій уже закрито.
     */
    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Project repository is closed");
        }
    }
}
