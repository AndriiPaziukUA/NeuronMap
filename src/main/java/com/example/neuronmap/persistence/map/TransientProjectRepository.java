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

/**
 * Зберігає налаштування ще не матеріалізованого проєкту в пам’яті та створює постійне сховище лише за потреби.
 * Під час першого збереження непорожньої карти може очистити вміст призначеного каталогу проєкту, але не виходить
 * за межі безпосереднього дочірнього каталогу переданого кореня володіння.
 */
public final class TransientProjectRepository implements MapRepository {

    private final Path databasePath;
    private final Path ownershipRoot;
    private final Supplier<? extends MapRepository> persistentRepositoryFactory;
    private MapRepository delegate;
    private CameraState pendingCameraState = CameraState.defaultState();
    private Double pendingSimulationTickMillis;
    private boolean closed;

    /**
     * Створює тимчасове сховище, відкладаючи створення постійного репозиторію до першого запису непорожньої карти.
     * Якщо каталог проєкту вже існує, під час матеріалізації його вміст буде очищено за умови, що файл бази відсутній.
     * Каталог проєкту має бути безпосереднім дочірнім каталогом явно переданого кореня володіння.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     * @param ownershipRoot довірений корінь каталогу проєктів; сам корінь не очищується й не видаляється.
     * @param persistentRepositoryFactory постачальник постійного репозиторію; якщо його створення завершується помилкою, наступний запис може повторити спробу.
     * @throws IllegalArgumentException якщо каталог проєкту не є безпосереднім дочірнім каталогом кореня володіння.
     * @throws NullPointerException якщо будь-який обов’язковий аргумент не задано.
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

    /** Повертає нормалізований шлях до файлу бази даних, призначеного цьому репозиторію. */
    @Override
    public Path databasePath() {
        return databasePath;
    }

    /** Повертає {@code true}, лише якщо створений постійний делегат також повідомляє про постійне збереження. */
    @Override
    public boolean isPersistent() {
        return delegate != null && delegate.isPersistent();
    }

    /**
     * Повертає стан камери з постійного сховища або відкладений стан, якщо проєкт ще не матеріалізовано.
     * До першого збереження повертається типовий стан камери.
     *
     * @return поточний стан камери проєкту.
     */
    @Override
    public CameraState loadCameraState() {
        return delegate == null ? pendingCameraState : delegate.loadCameraState();
    }

    /**
     * Повертає відкладену або збережену тривалість такту симуляції.
     * До матеріалізації, якщо значення ще не задане, повертає переданий резервний параметр без його зміни.
     * Після матеріалізації запит делегується постійному репозиторію.
     *
     * @param fallbackMillis резервна тривалість такту, яку повертають за відсутності відкладеного значення.
     * @return відкладене значення, результат постійного сховища або резервне значення.
     */
    @Override
    public double loadSimulationTickMillis(double fallbackMillis) {
        if (delegate == null) {
            return pendingSimulationTickMillis == null ? fallbackMillis : pendingSimulationTickMillis;
        }
        return delegate.loadSimulationTickMillis(fallbackMillis);
    }

    /**
     * Завантажує карту з постійного сховища або очищає передану модель, якщо проєкт ще не матеріалізовано.
     * Цей виклик сам по собі не створює каталогу чи файлу бази даних.
     *
     * @param model модель карти, до якої потрібно завантажити дані.
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
     * Зберігає тривалість такту в пам’яті до матеріалізації або передає запис постійному сховищу.
     * Відкладене значення буде перенесено до SQLite після першого збереження непорожньої карти.
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
     * Запам’ятовує стан камери та зберігає карту; порожню модель залишає тимчасовою, не створюючи базу даних.
     * Під час першого збереження непорожньої моделі готує каталог, створює постійний делегат і передає йому карту,
     * актуальний стан камери та відкладену тривалість такту симуляції.
     *
     * @param model модель карти нейронів.
     * @param cameraState стан камери, який потрібно зберегти зараз і використати під час матеріалізації.
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

    /**
     * Закриває репозиторій не більше одного разу та звільняє ресурси постійного делегата, якщо його створено.
     * Не видаляє каталог або файл проєкту; очищає відкладену тривалість такту, а до матеріалізації скидає й камеру.
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
     * Підготовлює каталог проєкту й створює постійний репозиторій через ін’єктовану фабрику.
     * Метод викликається за відсутності делегата; після успішного створення наступні записи використовують його.
     * Фабрика не повинна створювати базу, якщо вона вже існує.
     *
     * @throws PersistenceException якщо каталог неможливо підготувати.
     * @throws NullPointerException якщо постачальник повертає {@code null} замість репозиторію.
     */
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
     * Перевіряє відсутність наявної бази даних і готує лише каталог проєкту, який належить переданому кореню.
     * Якщо каталог проєкту існує, його вміст очищається; якщо його немає, створюється новий каталог.
     * Символічні посилання в ролі кореня чи каталогу проєкту не використовуються для виходу за межі володіння.
     *
     * @throws IOException якщо база вже існує, каталог небезпечний або файлову операцію не вдалося виконати.
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

    /** Відхиляє операції запису після закриття репозиторію. */
    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Project repository is closed");
        }
    }
}
