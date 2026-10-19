package com.example.neuronmap.persistence.map;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.PersistenceException;
import com.example.neuronmap.persistence.sqlite.DefaultMapRepositoryFactory;
import com.example.neuronmap.project.ProjectDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє відкладену матеріалізацію тимчасового проєкту та передачу роботи постійному репозиторію.
 */
final class TransientProjectRepositoryTest {

    /**
     * Перевіряє, що порожній тимчасовий проєкт не створює каталог до появи даних.
     *
     * @param tempDir тимчасовий каталог тесту.
     */
    @Test
    void emptyProjectDoesNotCreateFolder(@TempDir Path tempDir) {
        Path path = tempDir.resolve("Project").resolve("project.db");
        TransientProjectRepository repository = neverMaterializedRepository(path);

        repository.save(new NeuronMapModel(), CameraState.defaultState());

        assertFalse(Files.exists(path.getParent()));
        assertFalse(repository.isPersistent());
        repository.close();
    }

    /**
     * Перевіряє, що перший нейрон матеріалізує проєкт і очищає попередній вміст каталогу.
     *
     * @param tempDir тимчасовий каталог тесту.
     * @throws Exception якщо не вдається підготувати тестові файли.
     */
    @Test
    void firstElementMaterializesProjectAndCleansJunk(@TempDir Path tempDir)
            throws Exception {
        Path directory = tempDir.resolve("Project");
        Path path = directory.resolve("project.db");
        Files.createDirectories(directory.resolve("old"));
        Files.writeString(directory.resolve("junk.txt"), "junk");
        Files.writeString(directory.resolve("old").resolve("junk.txt"), "junk");

        ProjectDescriptor project = new ProjectDescriptor(
                "project-id",
                "Project",
                path,
                null
        );
        MapRepositoryFactory factory = new DefaultMapRepositoryFactory();
        NeuronMapModel model = new NeuronMapModel();
        model.createNeuron(NeuronType.EXCITATORY, 20.0, 30.0);

        try (MapRepository repository = factory.createTransientProjectRepository(project)) {
            repository.save(model, new CameraState(1.5, 20, -10));

            assertTrue(repository.isPersistent());
            assertTrue(Files.isRegularFile(path));
            assertFalse(Files.exists(directory.resolve("junk.txt")));
            assertFalse(Files.exists(directory.resolve("old")));
        }
    }

    /**
     * Перевіряє збереження налаштувань панелі й камери до матеріалізації проєкту.
     *
     * @param tempDir тимчасовий каталог тесту.
     */
    @Test
    void emptyProjectRetainsPendingToolbarAndCameraSettings(@TempDir Path tempDir) {
        Path path = tempDir.resolve("Project").resolve("project.db");
        TransientProjectRepository repository = neverMaterializedRepository(path);

        CameraState camera = new CameraState(1.5, 20, -10);
        repository.save(new NeuronMapModel(), camera);
        repository.saveSimulationTickMillis(275.0);

        assertEquals(camera, repository.loadCameraState());
        assertEquals(275.0, repository.loadSimulationTickMillis(650.0));
        assertFalse(repository.isPersistent());
        repository.close();
    }

    /**
     * Переконується, що постійний делегат створюється ін'єктованим постачальником після підготовки каталогу.
     * Тест використовує тестовий делегат і не відкриває SQLite.
     *
     * @param tempDir тимчасовий каталог тесту.
     * @throws Exception якщо не вдається підготувати тестові файли.
     */
    @Test
    void usesInjectedDelegateAfterPreparingProjectDirectory(@TempDir Path tempDir)
            throws Exception {
        Path directory = tempDir.resolve("Project");
        Path path = directory.resolve("project.db");
        Files.createDirectories(directory);
        Path junk = directory.resolve("junk.txt");
        Files.writeString(junk, "junk");

        AtomicInteger factoryCalls = new AtomicInteger();
        RecordingMapRepository delegate = new RecordingMapRepository(path);
        TransientProjectRepository repository = new TransientProjectRepository(path, () -> {
            factoryCalls.incrementAndGet();
            assertTrue(Files.isDirectory(directory));
            assertFalse(Files.exists(junk));
            return delegate;
        });

        assertEquals(0, factoryCalls.get());
        assertFalse(repository.isPersistent());

        NeuronMapModel model = new NeuronMapModel();
        model.createNeuron(NeuronType.EXCITATORY, 10.0, 15.0);
        CameraState camera = new CameraState(1.25, 12.0, -8.0);
        repository.save(model, camera);

        assertEquals(1, factoryCalls.get());
        assertTrue(repository.isPersistent());
        assertSame(model, delegate.savedModel);
        assertEquals(camera, delegate.savedCameraState);
        assertEquals(0, delegate.closeCalls);

        repository.close();

        assertEquals(1, delegate.closeCalls);
    }

    /**
     * Перевіряє, що наявний файл бази даних не перезаписується під час спроби матеріалізації.
     *
     * @param tempDir тимчасовий каталог тесту.
     * @throws Exception якщо не вдається створити тестовий файл.
     */
    @Test
    void existingProjectDatabaseIsNeverOverwritten(@TempDir Path tempDir)
            throws Exception {
        Path directory = tempDir.resolve("Project");
        Path path = directory.resolve("project.db");
        Files.createDirectories(directory);
        Files.writeString(path, "existing");

        TransientProjectRepository repository = neverMaterializedRepository(path);
        NeuronMapModel model = new NeuronMapModel();
        model.createNeuron(NeuronType.EXCITATORY, 1, 2);

        org.junit.jupiter.api.Assertions.assertThrows(
                PersistenceException.class,
                () -> repository.save(model, CameraState.defaultState())
        );
        assertEquals("existing", Files.readString(path));
        repository.close();
    }

    /**
     * Створює репозиторій, постачальник якого виявляє неочікувану матеріалізацію в тесті.
     *
     * @param path шлях до бази даних тимчасового проєкту.
     * @return тимчасовий репозиторій без реального постійного делегата.
     */
    private static TransientProjectRepository neverMaterializedRepository(Path path) {
        return new TransientProjectRepository(path, () -> {
            throw new AssertionError("The repository should not be materialized in this test");
        });
    }

    /**
     * Імітує постійне сховище, зберігаючи передані дані та рахуючи закриття без доступу до SQLite.
     */
    private static final class RecordingMapRepository implements MapRepository {

        private final Path databasePath;
        private NeuronMapModel savedModel;
        private CameraState savedCameraState;
        private int closeCalls;

        /**
         * Створює тестовий делегат для вказаного шляху.
         *
         * @param databasePath шлях до бази даних, який представляє делегат.
         */
        private RecordingMapRepository(Path databasePath) {
            this.databasePath = databasePath.toAbsolutePath().normalize();
        }

        /**
         * Повертає шлях, пов'язаний із тестовим делегатом.
         *
         * @return абсолютний нормалізований шлях до бази даних.
         */
        @Override
        public Path databasePath() {
            return databasePath;
        }

        /**
         * Повертає типовий стан камери, оскільки тест не перевіряє читання.
         *
         * @return типовий стан камери.
         */
        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        /**
         * Повертає резервну тривалість такту, оскільки тест не зберігає це налаштування.
         *
         * @param fallbackMillis резервна тривалість такту.
         * @return передане резервне значення.
         */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return fallbackMillis;
        }

        /**
         * Залишає модель без змін, оскільки тест перевіряє тільки передавання операцій запису.
         *
         * @param model модель карти, у яку потрібно було б завантажити дані.
         */
        @Override
        public void loadInto(NeuronMapModel model) {
            // Читання не потрібне для цього тестового делегата.
        }

        /**
         * Не зберігає налаштування симуляції, оскільки цей сценарій перевіряє лише запис карти.
         *
         * @param millis тривалість такту симуляції.
         */
        @Override
        public void saveSimulationTickMillis(double millis) {
            // Налаштування симуляції не потрібне для цього тесту.
        }

        /**
         * Запам'ятовує модель і стан камери, передані тимчасовим репозиторієм.
         *
         * @param model модель карти.
         * @param cameraState стан камери.
         */
        @Override
        public void save(NeuronMapModel model, CameraState cameraState) {
            savedModel = model;
            savedCameraState = cameraState;
        }

        /**
         * Рахує закриття тестового делегата.
         */
        @Override
        public void close() {
            closeCalls++;
        }
    }
}
