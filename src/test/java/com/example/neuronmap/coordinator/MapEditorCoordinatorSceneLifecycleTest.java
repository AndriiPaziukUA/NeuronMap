package com.example.neuronmap.coordinator;

import com.example.neuronmap.ApplicationMetadata;
import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.project.FileSystemProjectCatalogRepository;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.ProjectCatalogService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Перевіряє зняття фільтра сцени та звільнення ресурсів між повторними сеансами редактора. */
final class MapEditorCoordinatorSceneLifecycleTest {

    /** Перевіряє, що фільтр переднього плану діє до завершення й не реагує після двох циклів відкриття/закриття.
     *
     * @param tempDir тимчасовий каталог, у якому ізольовано сховище кожного циклу.
     * @throws Exception якщо тест не може підготувати файли проєктів або запустити перевірку JavaFX.
     */
    @Test
    void shutdownDetachesSceneFilterAcrossRepeatedEditorLifecycles(@TempDir Path tempDir) throws Exception {
        List<Path> storageRoots = List.of(tempDir.resolve("first-cycle"), tempDir.resolve("second-cycle"));
        for (Path storageRoot : storageRoots) {
            Path projectDirectory = storageRoot.resolve("Project");
            Files.createDirectories(projectDirectory);
            Files.writeString(projectDirectory.resolve("project.db"), "test repository marker");
        }

        JavaFxTestSupport.runAndWait(() -> {
            for (Path storageRoot : storageRoots) {
                Fixture fixture = createFixture(storageRoot);
                MapEditorCoordinator coordinator = fixture.coordinator;
                try {
                    Scene scene = coordinator.createScene(800.0, 600.0);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();

                    NeuronView firstView = findNeuronView(scene, fixture.firstNeuronId);
                    NeuronView secondView = findNeuronView(scene, fixture.secondNeuronId);
                    assertNotNull(firstView);
                    assertNotNull(secondView);
                    Pane nodeLayer = (Pane) firstView.getParent();
                    assertSame(nodeLayer, secondView.getParent());

                    int firstIndex = nodeLayer.getChildren().indexOf(firstView);
                    int secondIndex = nodeLayer.getChildren().indexOf(secondView);
                    assertNotEquals(firstIndex, secondIndex);
                    NeuronView clickedView = firstIndex < secondIndex ? firstView : secondView;
                    NeuronView otherView = clickedView == firstView ? secondView : firstView;
                    int clickedIndexBefore = nodeLayer.getChildren().indexOf(clickedView);

                    Event.fireEvent(clickedView, mousePressedEvent());

                    int clickedIndexAfter = nodeLayer.getChildren().indexOf(clickedView);
                    assertEquals(nodeLayer.getChildren().size() - 1, clickedIndexAfter,
                            "Фільтр сцени має піднімати клацнутий нейрон над іншими");
                    assertNotEquals(clickedIndexBefore, clickedIndexAfter);

                    coordinator.shutdown();
                    int otherIndexAfterShutdown = nodeLayer.getChildren().indexOf(otherView);
                    Event.fireEvent(otherView, mousePressedEvent());
                    assertEquals(otherIndexAfterShutdown, nodeLayer.getChildren().indexOf(otherView),
                            "Після shutdown фільтр сцени не повинен змінювати порядок вузлів");

                    coordinator.shutdown();
                    assertEquals(1, fixture.repository.closeCalls,
                            "Повторний shutdown не повинен повторно закривати репозиторій");
                } finally {
                    coordinator.shutdown();
                }
            }
        });
    }

    /** Складає координатор із тимчасовим сховищем та двома нейронами, не відкриваючи реальну базу SQLite.
     *
     * @param storageRoot корінь ізольованого сховища цього циклу.
     * @return координатор і пов'язані з тестом ідентифікатори та репозиторій.
     */
    private static Fixture createFixture(Path storageRoot) {
        Path databasePath = storageRoot.resolve("Project").resolve("project.db");
        NeuronMapModel storedModel = new NeuronMapModel();
        String firstNeuronId = storedModel.createNeuron(NeuronType.EXCITATORY, 50.0, 60.0).id();
        String secondNeuronId = storedModel.createNeuron(NeuronType.INHIBITORY, 180.0, 120.0).id();
        RecordingMapRepository repository = new RecordingMapRepository(databasePath, storedModel);
        NeuronMapApplicationService application = new NeuronMapApplicationService(
                new NeuronMapModel(), repository);
        AppConfig config = new AppConfig(
                new AppConfig.Window(ApplicationMetadata.NAME, 1280.0, 720.0, "state.properties"),
                new AppConfig.Camera(1.25),
                new AppConfig.Simulation(500.0, 40.0, 3000.0)
        );
        ProjectCatalogService projectCatalog = new ProjectCatalogService(
                new FileSystemProjectCatalogRepository(
                        storageRoot, new GlobalSettingsStore(storageRoot.resolve("global.properties"))));
        MapRepositoryFactory repositoryFactory = new MapRepositoryFactory() {
            /** Створює тестовий репозиторій для нового проєкту в межах тестового сховища. */
            @Override
            public MapRepository createTransientProjectRepository(ProjectDescriptor project) {
                return new RecordingMapRepository(project.databasePath(), storedModel);
            }

            /** Створює тестовий репозиторій для наявного проєкту в межах тестового сховища. */
            @Override
            public MapRepository createSavedProjectRepository(ProjectDescriptor project) {
                return new RecordingMapRepository(project.databasePath(), storedModel);
            }
        };
        MapEditorCoordinator coordinator = new MapEditorCoordinator(
                application,
                config,
                new LocalizationService(Locale.ENGLISH),
                projectCatalog,
                repositoryFactory,
                () -> { }
        );
        return new Fixture(coordinator, repository, firstNeuronId, secondNeuronId);
    }

    /** Знаходить кореневе подання нейрона за моделлю, обходячи вкладені графічні вузли його картки.
     *
     * @param scene сцена, у якій потрібно знайти нейрон.
     * @param neuronId ідентифікатор нейрона в моделі.
     * @return подання нейрона або {@code null}, якщо його немає в дереві сцени.
     */
    private static NeuronView findNeuronView(Scene scene, String neuronId) {
        for (Node node : scene.getRoot().lookupAll(".neuron-card")) {
            Node current = node;
            while (current != null && !(current instanceof NeuronView)) {
                current = current.getParent();
            }
            if (current instanceof NeuronView neuronView && neuronView.model().id().equals(neuronId)) {
                return neuronView;
            }
        }
        return null;
    }

    /** Створює подію натискання первинної кнопки миші для перевірки фільтра сцени.
     *
     * @return подія натискання, яка спрямовується до графічного подання нейрона.
     */
    private static MouseEvent mousePressedEvent() {
        return new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                10.0, 10.0, 10.0, 10.0,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false, false, false, true, null
        );
    }

    /** Утримує координатор і тестовий репозиторій разом з ідентифікаторами створених нейронів. */
    private static final class Fixture {
        private final MapEditorCoordinator coordinator;
        private final RecordingMapRepository repository;
        private final String firstNeuronId;
        private final String secondNeuronId;

        /** Створює набір даних, потрібний для перевірки порядку нейронів і закриття репозиторію.
         *
         * @param coordinator координатор тестового редактора.
         * @param repository репозиторій, у якому рахується кількість закриттів.
         * @param firstNeuronId ідентифікатор першого підготовленого нейрона.
         * @param secondNeuronId ідентифікатор другого підготовленого нейрона.
         */
        private Fixture(
                MapEditorCoordinator coordinator,
                RecordingMapRepository repository,
                String firstNeuronId,
                String secondNeuronId
        ) {
            this.coordinator = coordinator;
            this.repository = repository;
            this.firstNeuronId = firstNeuronId;
            this.secondNeuronId = secondNeuronId;
        }
    }

    /** Імітує репозиторій із готовою моделлю та фіксує закриття без зовнішньої бази даних. */
    private static final class RecordingMapRepository implements MapRepository {
        private final Path databasePath;
        private final NeuronMapModel storedModel;
        private int closeCalls;

        /** Створює тестове сховище для переданого файла та моделі карти.
         *
         * @param databasePath шлях до файла тестового проєкту.
         * @param storedModel модель, яку репозиторій повертає під час завантаження.
         */
        private RecordingMapRepository(Path databasePath, NeuronMapModel storedModel) {
            this.databasePath = databasePath.toAbsolutePath().normalize();
            this.storedModel = storedModel;
        }

        /** Повертає файл, закріплений за тестовим проєктом. */
        @Override
        public Path databasePath() {
            return databasePath;
        }

        /** Повертає типовий стан камери, який не є предметом цієї перевірки. */
        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        /** Повертає резервну швидкість симуляції, оскільки тест не зберігає окреме значення. */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return fallbackMillis;
        }

        /** Копіює підготовлену карту в модель, яку завантажує координатор. */
        @Override
        public void loadInto(NeuronMapModel model) {
            model.replaceContentsFrom(storedModel);
        }

        /** Не зберігає швидкість, бо перевірка стосується лише життєвого циклу сцени. */
        @Override
        public void saveSimulationTickMillis(double millis) {
            // У цьому сценарії швидкість не впливає на перевірювану поведінку.
        }

        /** Приймає збереження без доступу до диска, залишаючи дані тестової моделі незмінними. */
        @Override
        public void save(NeuronMapModel model, CameraState cameraState) {
            // Файлова персистентність не є предметом цього сценарію.
        }

        /** Фіксує виклики закриття для перевірки ідемпотентності завершення редактора. */
        @Override
        public void close() {
            closeCalls++;
        }
    }
}
