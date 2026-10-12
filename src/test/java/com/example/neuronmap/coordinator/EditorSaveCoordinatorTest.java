package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.FileSystemProjectCatalogRepository;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.ProjectCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє збереження камери, оновлення метаданих постійного проєкту та фіксацію історії змін.
 */
final class EditorSaveCoordinatorTest {

    /**
     * Перевіряє збереження камери, оновлення активного проєкту та фіксацію зміни в історії.
     *
     * @param temporaryDirectory тимчасовий каталог тесту.
     * @throws Exception якщо тестова база даних не може бути підготовлена.
     */
    @Test
    void savesCameraProjectMetadataAndHistory(@TempDir Path temporaryDirectory) throws Exception {
        Path storage = temporaryDirectory.resolve("projects");
        Path databasePath = storage.resolve("Sample/project.db");
        Files.createDirectories(databasePath.getParent());
        Files.writeString(databasePath, "database");

        GlobalSettingsStore settings = new GlobalSettingsStore(
                storage.resolve("global-settings.properties")
        );
        ProjectCatalogService projectCatalog = new ProjectCatalogService(
                new FileSystemProjectCatalogRepository(storage, settings)
        );

        NeuronMapModel model = new NeuronMapModel();
        HistoryService historyService = new HistoryService(model);
        historyService.initialize();
        model.createNeuron(NeuronType.EXCITATORY, 15, 25);

        RecordingMapRepository repository = new RecordingMapRepository(databasePath, true);
        MapService mapService = new MapService(model, repository);
        EditorSaveCoordinator coordinator = new EditorSaveCoordinator(
                mapService,
                projectCatalog,
                historyService
        );
        EditorState state = new EditorState(1.75, 123.0, -42.0);
        ProjectDescriptor original = new ProjectDescriptor(
                "sample",
                "Sample",
                databasePath,
                null
        );

        ProjectDescriptor saved = coordinator.save(state, original, true);

        assertEquals(new CameraState(1.75, 123.0, -42.0), repository.savedCameraState());
        assertEquals("Sample", saved.name());
        assertEquals("Sample", settings.load("last_project_name"));
        assertEquals(1, historyService.undoSize());
    }

    /**
     * Перевіряє, що збереження тимчасового проєкту не змінює його опис і не додає крок історії до її ініціалізації.
     *
     * @param temporaryDirectory тимчасовий каталог тесту.
     */
    @Test
    void keepsTransientProjectMetadataAndSkipsUninitializedHistory(@TempDir Path temporaryDirectory) {
        Path storage = temporaryDirectory.resolve("projects");
        GlobalSettingsStore settings = new GlobalSettingsStore(
                storage.resolve("global-settings.properties")
        );
        ProjectCatalogService projectCatalog = new ProjectCatalogService(
                new FileSystemProjectCatalogRepository(storage, settings)
        );

        NeuronMapModel model = new NeuronMapModel();
        HistoryService historyService = new HistoryService(model);
        RecordingMapRepository repository = new RecordingMapRepository(
                storage.resolve("Transient/project.db"),
                false
        );
        MapService mapService = new MapService(model, repository);
        EditorSaveCoordinator coordinator = new EditorSaveCoordinator(
                mapService,
                projectCatalog,
                historyService
        );
        ProjectDescriptor transientProject = new ProjectDescriptor(
                "transient",
                "Transient",
                repository.databasePath(),
                null
        );

        ProjectDescriptor saved = coordinator.save(
                new EditorState(1.0, 4.0, 8.0),
                transientProject,
                false
        );

        assertSame(transientProject, saved);
        assertEquals(new CameraState(1.0, 4.0, 8.0), repository.savedCameraState());
        assertFalse(historyService.canUndo());
        assertNull(settings.load("last_project_name"));
    }

    /**
     * Надає тестову реалізацію репозиторію, яка фіксує збережений стан камери.
     */
    private static final class RecordingMapRepository implements MapRepository {

        private final Path databasePath;
        private final boolean persistent;
        private CameraState savedCameraState;

        /**
         * Створює тестовий репозиторій з указаним шляхом і типом сховища.
         *
         * @param databasePath шлях до файла даних.
         * @param persistent ознака постійного сховища.
         */
        private RecordingMapRepository(Path databasePath, boolean persistent) {
            this.databasePath = databasePath.toAbsolutePath().normalize();
            this.persistent = persistent;
        }

        /**
         * Повертає шлях тестового сховища.
         *
         * @return нормалізований шлях до тестової бази даних.
         */
        @Override
        public Path databasePath() {
            return databasePath;
        }

        /**
         * Повертає ознаку постійності тестового сховища.
         *
         * @return {@code true}, якщо тестове сховище позначене як постійне.
         */
        @Override
        public boolean isPersistent() {
            return persistent;
        }

        /**
         * Повертає стандартний стан камери, оскільки тестовий репозиторій не завантажує дані.
         *
         * @return стандартний стан камери.
         */
        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        /**
         * Повертає резервну швидкість симуляції без читання з диска.
         *
         * @param fallbackMillis резервна тривалість такту.
         * @return передане резервне значення.
         */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return fallbackMillis;
        }

        /**
         * Не змінює модель, оскільки тест не перевіряє завантаження даних.
         *
         * @param model модель, у яку потрібно завантажити дані.
         */
        @Override
        public void loadInto(NeuronMapModel model) {
        }

        /**
         * Не зберігає швидкість симуляції, оскільки тест перевіряє лише камеру.
         *
         * @param millis тривалість такту симуляції.
         */
        @Override
        public void saveSimulationTickMillis(double millis) {
        }

        /**
         * Запам’ятовує стан камери, переданий службою карти.
         *
         * @param model модель карти.
         * @param cameraState стан камери для збереження.
         */
        @Override
        public void save(NeuronMapModel model, CameraState cameraState) {
            savedCameraState = cameraState;
        }

        /**
         * Закриває тестове сховище; додаткових ресурсів воно не використовує.
         */
        @Override
        public void close() {
        }

        /**
         * Повертає останній стан камери, переданий на збереження.
         *
         * @return збережений стан камери або {@code null}, якщо збереження ще не виконувалося.
         */
        private CameraState savedCameraState() {
            return savedCameraState;
        }
    }
}
