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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє відкладену матеріалізацію, передавання стану та життєвий цикл тимчасового репозиторію. */
final class TransientProjectRepositoryTest {

    /** Перевіряє, що порожній тимчасовий проєкт не створює каталог до появи даних. */
    @Test
    void emptyProjectDoesNotCreateFolder(@TempDir Path tempDir) {
        Path path = tempDir.resolve("Project").resolve("project.db");
        try (TransientProjectRepository repository = neverMaterializedRepository(path, tempDir)) {
            repository.save(new NeuronMapModel(), CameraState.defaultState());

            assertFalse(Files.exists(path.getParent()));
            assertFalse(repository.isPersistent());
        }
    }

    /**
     * Перевіряє, що перший запис непорожньої карти очищає вміст лише призначеного каталогу проєкту.
     * Файли й каталоги поруч із ним у корені володіння мають залишитися недоторканими.
     *
     * @param tempDir тимчасовий корінь сховища, ізольований для цього тесту.
     * @throws Exception якщо не вдається підготувати чи прочитати тестові файли.
     */
    @Test
    void firstElementMaterializesProjectAndClearsOnlyOwnedDirectoryContents(@TempDir Path tempDir)
            throws Exception {
        Path directory = tempDir.resolve("Project");
        Path path = directory.resolve("project.db");
        Path siblingFile = tempDir.resolve("keep.txt");
        Files.createDirectories(directory.resolve("old"));
        Files.writeString(directory.resolve("junk.txt"), "junk");
        Files.writeString(directory.resolve("old").resolve("junk.txt"), "nested junk");
        Files.writeString(siblingFile, "keep");

        ProjectDescriptor project = new ProjectDescriptor(
                "project-id",
                "Project",
                path,
                null
        );
        MapRepositoryFactory factory = new DefaultMapRepositoryFactory(tempDir);
        NeuronMapModel model = new NeuronMapModel();
        model.createNeuron(NeuronType.EXCITATORY, 20.0, 30.0);

        try (MapRepository repository = factory.createTransientProjectRepository(project)) {
            repository.save(model, new CameraState(1.5, 20, -10));

            assertTrue(repository.isPersistent());
            assertTrue(Files.isRegularFile(path));
            assertFalse(Files.exists(directory.resolve("junk.txt")));
            assertFalse(Files.exists(directory.resolve("old")));
            assertTrue(Files.isRegularFile(siblingFile));
            assertEquals("keep", Files.readString(siblingFile));
        }
    }

    /** Перевіряє збереження налаштувань панелі й камери до матеріалізації тимчасового проєкту. */
    @Test
    void emptyProjectRetainsPendingToolbarAndCameraSettings(@TempDir Path tempDir) {
        Path path = tempDir.resolve("Project").resolve("project.db");
        try (TransientProjectRepository repository = neverMaterializedRepository(path, tempDir)) {
            CameraState camera = new CameraState(1.5, 20, -10);
            repository.save(new NeuronMapModel(), camera);
            repository.saveSimulationTickMillis(275.0);

            assertEquals(camera, repository.loadCameraState());
            assertEquals(275.0, repository.loadSimulationTickMillis(650.0));
            assertFalse(repository.isPersistent());
        }
    }

    /**
     * Переконується, що постійний делегат створюється ін’єктованим постачальником після підготовки каталогу,
     * а камера й відкладена швидкість передаються йому під час першого збереження.
     *
     * @param tempDir тимчасовий каталог тесту.
     * @throws Exception якщо не вдається підготувати тестові файли.
     */
    @Test
    void usesInjectedDelegateAfterPreparingProjectDirectory(@TempDir Path tempDir) throws Exception {
        Path directory = tempDir.resolve("Project");
        Path path = directory.resolve("project.db");
        Files.createDirectories(directory);
        Path junk = directory.resolve("junk.txt");
        Files.writeString(junk, "junk");

        AtomicInteger factoryCalls = new AtomicInteger();
        RecordingMapRepository delegate = new RecordingMapRepository(path);
        try (TransientProjectRepository repository = new TransientProjectRepository(path, tempDir, () -> {
            factoryCalls.incrementAndGet();
            assertTrue(Files.isDirectory(directory));
            assertFalse(Files.exists(junk));
            return delegate;
        })) {
            assertEquals(0, factoryCalls.get());
            assertFalse(repository.isPersistent());
            repository.saveSimulationTickMillis(275.0);

            NeuronMapModel model = new NeuronMapModel();
            model.createNeuron(NeuronType.EXCITATORY, 10.0, 15.0);
            CameraState camera = new CameraState(1.25, 12.0, -8.0);
            repository.save(model, camera);

            assertEquals(1, factoryCalls.get());
            assertTrue(repository.isPersistent());
            assertSame(model, delegate.savedModel);
            assertEquals(camera, delegate.savedCameraState);
            assertEquals(275.0, delegate.savedSimulationTickMillis, 0.0001);
            assertEquals(0, delegate.closeCalls);
        }

        assertEquals(1, delegate.closeCalls);
    }

    /** Перевіряє, що подвійне закриття звільняє делегат один раз і подальші записи відхиляються. */
    @Test
    void closeIsIdempotentAndRejectsSubsequentWrites(@TempDir Path tempDir) {
        Path path = tempDir.resolve("ClosedProject").resolve("project.db");
        RecordingMapRepository delegate = new RecordingMapRepository(path);
        AtomicInteger factoryCalls = new AtomicInteger();

        try (TransientProjectRepository repository = new TransientProjectRepository(path, tempDir, () -> {
            factoryCalls.incrementAndGet();
            return delegate;
        })) {
            NeuronMapModel model = new NeuronMapModel();
            model.createNeuron(NeuronType.INHIBITORY, 5.0, 8.0);
            repository.save(model, CameraState.defaultState());

            repository.close();
            repository.close();

            assertEquals(1, factoryCalls.get());
            assertEquals(1, delegate.closeCalls);
            assertThrows(IllegalStateException.class, () -> repository.saveSimulationTickMillis(225.0));
            assertThrows(
                    IllegalStateException.class,
                    () -> repository.save(new NeuronMapModel(), CameraState.defaultState())
            );
        }

        assertEquals(1, delegate.closeCalls, "Повторний close не повинен повторно звільняти делегат");
    }

    /** Перевіряє, що файл бази даних, який існував до матеріалізації, не перезаписується. */
    @Test
    void existingProjectDatabaseIsNeverOverwritten(@TempDir Path tempDir) throws Exception {
        Path directory = tempDir.resolve("Project");
        Path path = directory.resolve("project.db");
        Files.createDirectories(directory);
        Files.writeString(path, "existing");

        try (TransientProjectRepository repository = neverMaterializedRepository(path, tempDir)) {
            NeuronMapModel model = new NeuronMapModel();
            model.createNeuron(NeuronType.EXCITATORY, 1, 2);

            assertThrows(
                    PersistenceException.class,
                    () -> repository.save(model, CameraState.defaultState())
            );
            assertEquals("existing", Files.readString(path));
        }
    }

    /** Перевіряє, що репозиторій не приймає шлях до каталогу, який не належить переданому кореню сховища. */
    @Test
    void refusesProjectOutsideExplicitOwnershipRoot(@TempDir Path tempDir) {
        Path databasePath = tempDir.resolve("outside/Project/project.db");

        assertThrows(
                IllegalArgumentException.class,
                () -> new TransientProjectRepository(databasePath, tempDir, () -> {
                    throw new AssertionError("The repository must reject the path before materialization");
                })
        );
    }

    /** Створює репозиторій, постачальник якого виявляє неочікувану матеріалізацію в тесті. */
    private static TransientProjectRepository neverMaterializedRepository(Path path, Path ownershipRoot) {
        return new TransientProjectRepository(path, ownershipRoot, () -> {
            throw new AssertionError("The repository should not be materialized in this test");
        });
    }

    /** Імітує постійне сховище, зберігаючи передані дані та рахуючи закриття без доступу до SQLite. */
    private static final class RecordingMapRepository implements MapRepository {

        private final Path databasePath;
        private NeuronMapModel savedModel;
        private CameraState savedCameraState;
        private double savedSimulationTickMillis = Double.NaN;
        private int closeCalls;

        /** Створює тестовий делегат для вказаного шляху. */
        private RecordingMapRepository(Path databasePath) {
            this.databasePath = databasePath.toAbsolutePath().normalize();
        }

        /** Повертає шлях, пов’язаний із тестовим делегатом. */
        @Override
        public Path databasePath() {
            return databasePath;
        }

        /** Повертає типовий стан камери, оскільки тест не перевіряє читання. */
        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        /** Повертає записану тривалість такту або резервне значення до її першого збереження. */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return Double.isNaN(savedSimulationTickMillis) ? fallbackMillis : savedSimulationTickMillis;
        }

        /** Залишає модель без змін, оскільки тест перевіряє тільки передавання операцій запису. */
        @Override
        public void loadInto(NeuronMapModel model) {
            // Читання не потрібне для цього тестового делегата.
        }

        /** Запам’ятовує тривалість такту, передану тимчасовим репозиторієм після матеріалізації. */
        @Override
        public void saveSimulationTickMillis(double millis) {
            savedSimulationTickMillis = millis;
        }

        /** Запам’ятовує модель і стан камери, передані тимчасовим репозиторієм. */
        @Override
        public void save(NeuronMapModel model, CameraState cameraState) {
            savedModel = model;
            savedCameraState = cameraState;
        }

        /** Рахує закриття тестового делегата. */
        @Override
        public void close() {
            closeCalls++;
        }
    }
}
