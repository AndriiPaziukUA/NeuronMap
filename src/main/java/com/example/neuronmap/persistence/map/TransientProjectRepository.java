package com.example.neuronmap.persistence.map;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.FileTreeOperations;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.PersistenceException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/** Зберігає дані тимчасового проєкту до його матеріалізації в каталозі та відкладає створення постійного сховища. */
public final class TransientProjectRepository implements MapRepository {

    private final Path databasePath;
    private final Path ownershipRoot;
    private final Supplier<? extends MapRepository> persistentRepositoryFactory;
    private MapRepository delegate;
    private CameraState pendingCameraState = CameraState.defaultState();
    private Double pendingSimulationTickMillis;
    private boolean closed;

    /**
     * Створює тимчасове сховище, відкладаючи створення постійного репозиторію до першого запису даних.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     * @param ownershipRoot довірений корінь каталогу проєктів, у межах якого дозволено очищення.
     * @param persistentRepositoryFactory постачальник постійного репозиторію, який викликається під час матеріалізації.
     * @throws IllegalArgumentException якщо каталог проєкту не є безпосереднім дочірнім каталогом кореня володіння.
     */
    public TransientProjectRepository(
            Path databasePath,
            Path ownershipRoot,
            Supplier<? extends MapRepository> persistentRepositoryFactory
    ) {
        this.databasePath = normalizeDatabasePath(databasePath);
        this.ownershipRoot = Objects.requireNonNull(ownershipRoot, "ownershipRoot")
                .toAbsolutePath()
                .normalize();
        Path projectDirectory = this.databasePath.getParent();
        if (projectDirectory == null || !this.ownershipRoot.equals(projectDirectory.getParent())) {
            throw new IllegalArgumentException("Project directory is outside the ownership root");
        }
        this.persistentRepositoryFactory = Objects.requireNonNull(
                persistentRepositoryFactory,
                "persistentRepositoryFactory"
        );
    }

    /** Нормалізує шлях до бази даних, щоб усі компоненти працювали з одним абсолютним шляхом. */
    private static Path normalizeDatabasePath(Path databasePath) {
        return Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    /** Повертає шлях до файлу бази даних. */
    @Override
    public Path databasePath() {
        return databasePath;
    }

    /** Повертає ознаку постійного збереження з урахуванням стану делегованого репозиторію. */
    @Override
    public boolean isPersistent() {
        return delegate != null && delegate.isPersistent();
    }

    /** Повертає збережений або відкладений стан камери. */
    @Override
    public CameraState loadCameraState() {
        return delegate == null ? pendingCameraState : delegate.loadCameraState();
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
            return pendingSimulationTickMillis == null ? fallbackMillis : pendingSimulationTickMillis;
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
    public synchronized void save(NeuronMapModel model, CameraState cameraState) {
        ensureOpen();
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        pendingCameraState = Objects.requireNonNull(cameraState, "cameraState");

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

    /** Закриває тимчасове сховище та звільняє ресурси делегованого репозиторію, якщо він існує. */
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

    /** Готує каталог і створює постійний репозиторій через переданий постачальник. */
    private void materialize() {
        try {
            prepareProjectDirectory();
            delegate = Objects.requireNonNull(
                    persistentRepositoryFactory.get(),
                    "persistentRepositoryFactory returned null"
            );
        } catch (IOException exception) {
            throw new PersistenceException("Не вдалося підготувати папку проєкту.", exception);
        }
    }

    /**
     * Перевіряє базу та готує каталог лише всередині явно переданого кореня володіння.
     *
     * @throws IOException якщо каталог сховища небезпечний або файлову операцію не вдалося виконати.
     */
    private void prepareProjectDirectory() throws IOException {
        Path directory = databasePath.getParent();
        if (directory == null) {
            throw new IOException("Project database has no parent directory");
        }

        if (Files.exists(databasePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Project database already exists: " + databasePath);
        }

        if (Files.notExists(ownershipRoot, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(ownershipRoot);
        }
        if (Files.isSymbolicLink(ownershipRoot)
                || !Files.isDirectory(ownershipRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Project ownership root is not a real directory: " + ownershipRoot);
        }
        if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(directory);
            return;
        }

        FileTreeOperations.clearOwnedDirectoryContents(ownershipRoot, directory);
    }

    /** Перевіряє, що операцію виконують до закриття репозиторію. */
    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Project repository is closed");
        }
    }
}
