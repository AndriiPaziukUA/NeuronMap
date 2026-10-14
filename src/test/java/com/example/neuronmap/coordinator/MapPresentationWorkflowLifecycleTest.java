package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.PulseAnimationController;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronLayerOrderController;
import com.example.neuronmap.view.NeuronMenuView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.RotationHandleView;
import com.example.neuronmap.view.SimulationStepPresenter;
import com.example.neuronmap.view.WorkspaceView;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє наскрізну синхронізацію графічних подань після вставлення, скасування/повторення та перемикання проєкту.
 */
final class MapPresentationWorkflowLifecycleTest {

    /**
     * Запускає JavaFX перед тестами, що перевіряють реальне дерево графічних вузлів.
     *
     * @throws Exception якщо запуск JavaFX не завершився вчасно.
     */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /**
     * Перевіряє, що команда Ctrl+V вставляє копію нейрона та створює для неї подання й ручку у JavaFX-шарах.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void pasteCommandCreatesViewAndRotationHandleInJavaFxTree() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron source = fixture.neuronService.create(
                        NeuronType.EXCITATORY,
                        80.0,
                        60.0
                );
                fixture.presentation.refreshAll();
                fixture.state.selectOnly(source.id());

                Pane root = new Pane(fixture.workspace.node());
                Scene scene = new Scene(root, 800.0, 600.0);
                scene.getRoot().applyCss();
                root.layout();
                Event.fireEvent(fixture.workspace.node(), mouseMovedEvent(260.0, 180.0));
                Event.fireEvent(fixture.workspace.node(), keyPressedEvent(KeyCode.C));

                Event.fireEvent(fixture.workspace.node(), keyPressedEvent(KeyCode.V));

                assertEquals(2, fixture.neuronService.neurons().size());
                assertEquals(2, fixture.neuronViews.size());
                assertEquals(2, fixture.rotationHandles.size());
                assertEquals(2, fixture.workspace.nodeLayer().getChildren().size());
                assertEquals(2, fixture.workspace.overlayLayer().getChildren().size());
                assertTrue(fixture.neuronViews.containsKey(source.id()));

                String pastedId = fixture.state.selectedNeuronIds().stream()
                        .filter(id -> !id.equals(source.id()))
                        .findFirst()
                        .orElseThrow();
                NeuronView pastedView = fixture.neuronViews.get(pastedId);
                RotationHandleView pastedHandle = fixture.rotationHandles.get(pastedId);
                assertNotNull(pastedView);
                assertNotNull(pastedHandle);
                assertTrue(fixture.workspace.nodeLayer().getChildren().contains(pastedView));
                assertTrue(fixture.workspace.overlayLayer().getChildren().contains(pastedHandle));
                assertTrue(fixture.neuronService.neurons().stream()
                        .anyMatch(neuron -> neuron.id().equals(pastedId)));
            } finally {
                fixture.dispose();
            }
        });
    }

    /**
     * Перевіряє, що undo прибирає вставлене подання, а redo створює його знову в актуальному дереві JavaFX.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void undoAndRedoSynchronizeNeuronViewsAndRotationHandles() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            try {
                fixture.neuronService.create(NeuronType.EXCITATORY, 80.0, 60.0);
                fixture.presentation.refreshAll();

                HistoryService history = new HistoryService(fixture.model);
                history.initialize();
                Neuron added = fixture.neuronService.create(
                        NeuronType.INHIBITORY,
                        220.0,
                        140.0
                );
                history.commitSavedState();
                assertTrue(history.canUndo());
                fixture.presentation.refreshAll();

                NeuronView addedView = fixture.neuronViews.get(added.id());
                RotationHandleView addedHandle = fixture.rotationHandles.get(added.id());
                assertNotNull(addedView);
                assertNotNull(addedHandle);
                assertTrue(fixture.workspace.nodeLayer().getChildren().contains(addedView));
                assertTrue(fixture.workspace.overlayLayer().getChildren().contains(addedHandle));

                EditorHistoryCoordinator historyCoordinator = new EditorHistoryCoordinator(
                        history,
                        fixture.simulationController,
                        fixture.connectionController,
                        fixture.neuronController,
                        fixture.state,
                        fixture.workspace,
                        () -> { },
                        fixture.presentation,
                        () -> { },
                        ignored -> { },
                        fixture.localization
                );

                assertTrue(historyCoordinator.undo());
                assertNull(fixture.neuronService.find(added.id()));
                assertFalse(fixture.neuronViews.containsKey(added.id()));
                assertFalse(fixture.rotationHandles.containsKey(added.id()));
                assertFalse(fixture.workspace.nodeLayer().getChildren().contains(addedView));
                assertFalse(fixture.workspace.overlayLayer().getChildren().contains(addedHandle));
                assertEquals(1, fixture.workspace.nodeLayer().getChildren().size());
                assertEquals(1, fixture.workspace.overlayLayer().getChildren().size());

                assertTrue(historyCoordinator.redo());
                NeuronView restoredView = fixture.neuronViews.get(added.id());
                RotationHandleView restoredHandle = fixture.rotationHandles.get(added.id());
                assertNotNull(restoredView);
                assertNotNull(restoredHandle);
                assertTrue(fixture.workspace.nodeLayer().getChildren().contains(restoredView));
                assertTrue(fixture.workspace.overlayLayer().getChildren().contains(restoredHandle));
                assertEquals(2, fixture.workspace.nodeLayer().getChildren().size());
                assertEquals(2, fixture.workspace.overlayLayer().getChildren().size());
            } finally {
                fixture.dispose();
            }
        });
    }

    /**
     * Перевіряє, що активація іншого проєкту від'єднує старі вузли та приєднує подання нової моделі.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void projectSwitchReplacesAttachedViewsWithViewsFromNewModel() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron previousNeuron = fixture.neuronService.create(
                        NeuronType.EXCITATORY,
                        80.0,
                        60.0
                );
                fixture.presentation.refreshAll();
                NeuronView previousView = fixture.neuronViews.get(previousNeuron.id());
                RotationHandleView previousHandle = fixture.rotationHandles.get(previousNeuron.id());
                assertNotNull(previousView);
                assertNotNull(previousHandle);

                MapService mapService = new MapService(
                        fixture.model,
                        new TestMapRepository(CameraState.defaultState(), 650.0)
                );
                HistoryService history = new HistoryService(fixture.model);
                AtomicReference<ProjectDescriptor> activeProject = new AtomicReference<>();
                ProjectDescriptor nextProject = new ProjectDescriptor(
                        "next-project",
                        "Наступний проєкт",
                        Path.of("next-project.db"),
                        Instant.EPOCH
                );
                EditorProjectSessionCoordinator sessionCoordinator =
                        new EditorProjectSessionCoordinator(
                                mapService,
                                history,
                                fixture.state,
                                activeProject::set,
                                ignored -> { },
                                ignored -> { },
                                50.0,
                                5_000.0,
                                650.0,
                                new PresentationSessionActions(fixture.presentation)
                        );
                TestMapRepository nextRepository = new TestMapRepository(
                        CameraState.defaultState(),
                        650.0,
                        fixture.neuronService
                );

                sessionCoordinator.prepareForProjectSwitch();
                sessionCoordinator.activateProject(nextProject, nextRepository, true);

                assertTrue(nextRepository.loadedModel == fixture.model);
                assertTrue(activeProject.get() == nextProject);
                assertNull(fixture.neuronService.find(previousNeuron.id()));
                assertFalse(fixture.neuronViews.containsKey(previousNeuron.id()));
                assertFalse(fixture.rotationHandles.containsKey(previousNeuron.id()));
                assertFalse(fixture.workspace.nodeLayer().getChildren().contains(previousView));
                assertFalse(fixture.workspace.overlayLayer().getChildren().contains(previousHandle));

                assertEquals(1, fixture.neuronService.neurons().size());
                assertEquals(1, fixture.neuronViews.size());
                assertEquals(1, fixture.rotationHandles.size());
                assertEquals(1, fixture.workspace.nodeLayer().getChildren().size());
                assertEquals(1, fixture.workspace.overlayLayer().getChildren().size());
                String nextNeuronId = fixture.neuronService.neurons().iterator().next().id();
                assertNotNull(fixture.neuronViews.get(nextNeuronId));
                assertNotNull(fixture.rotationHandles.get(nextNeuronId));
            } finally {
                fixture.dispose();
            }
        });
    }

    /**
     * Створює подію руху курсора, якою контролер буфера обміну оновлює позицію вставлення.
     *
     * @param x горизонтальна координата курсора в робочій області.
     * @param y вертикальна координата курсора в робочій області.
     * @return подія переміщення миші для JavaFX-диспетчера подій.
     */
    private static MouseEvent mouseMovedEvent(double x, double y) {
        return new MouseEvent(
                MouseEvent.MOUSE_MOVED,
                x,
                y,
                x,
                y,
                MouseButton.NONE,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null
        );
    }

    /**
     * Створює натискання клавіші з Ctrl для запуску команди копіювання або вставлення редактора.
     *
     * @param keyCode клавіша, що запускає команду буфера обміну.
     * @return подія клавіатури, яку можна передати JavaFX-диспетчеру.
     */
    private static KeyEvent keyPressedEvent(KeyCode keyCode) {
        return new KeyEvent(
                KeyEvent.KEY_PRESSED,
                "",
                "",
                keyCode,
                false,
                true,
                false,
                false
        );
    }

    /**
     * Виконує перевірку в JavaFX Application Thread і передає її виняток потоку тестів.
     *
     * @param action перевірка, яку потрібно виконати в JavaFX-потоці.
     * @throws Exception якщо потік JavaFX не відповів вчасно або перевірка завершилася помилкою.
     */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }

    /**
     * Збирає реальні контролери та подання для перевірки взаємодій без створення головного вікна застосунку.
     */
    private static final class Fixture {

        private final NeuronMapModel model = new NeuronMapModel();
        private final NeuronService neuronService = new NeuronService(model);
        private final GroupService groupService = new GroupService(model);
        private final ConnectionService connectionService = new ConnectionService(model);
        private final EditorState state = new EditorState(1.0, 0.0, 0.0);
        private final WorkspaceView workspace = new WorkspaceView();
        private final Map<String, NeuronView> neuronViews = new HashMap<>();
        private final Map<String, ConnectionView> connectionViews = new HashMap<>();
        private final Map<String, RotationHandleView> rotationHandles = new HashMap<>();
        private final LocalizationService localization = new LocalizationService(Locale.ENGLISH);
        private final AtomicReference<MapPresentationCoordinator> presentationReference =
                new AtomicReference<>();
        private final SelectionController selectionController = new SelectionController(
                groupService,
                state,
                () -> { },
                () -> { },
                ignored -> { },
                localization
        );
        private final ConnectionController connectionController = new ConnectionController(
                connectionService,
                state,
                workspace,
                neuronViews,
                this::refreshPresentation,
                () -> { },
                ignored -> { },
                localization
        );
        private final SimulationController simulationController = new SimulationController(
                neuronService,
                new SimulationService(),
                new SimulationStepPresenter(
                        workspace,
                        neuronViews,
                        () -> { },
                        new PulseAnimationController(workspace)
                ),
                () -> { },
                ignored -> { },
                ignored -> { },
                ignored -> { },
                650.0,
                50.0,
                5_000.0,
                localization
        );
        private final NeuronInteractionController neuronController = new NeuronInteractionController(
                neuronService,
                groupService,
                selectionController,
                new NeuronClipboardService(neuronService, groupService),
                state,
                workspace,
                neuronViews,
                rotationHandles,
                connectionController,
                simulationController,
                () -> { },
                this::refreshPresentation,
                ignored -> { },
                new NeuronMenuView(
                        new Pane(),
                        state::selectedNeuronForMenu,
                        ignored -> { },
                        localization
                ),
                localization
        );
        private final MapPresentationCoordinator presentationCoordinator = new MapPresentationCoordinator(
                neuronService,
                workspace,
                neuronViews,
                connectionViews,
                rotationHandles,
                neuronController,
                connectionController,
                new NeuronLayerOrderController(
                        neuronViews,
                        connectionViews,
                        model::connections
                )
        );
        private final MapPresentationCoordinator presentation = initializePresentationReference();

        /**
         * Публікує створений координатор, щоб callback-и контролерів використовували реальну синхронізацію.
         *
         * @return створений координатор презентації.
         */
        private MapPresentationCoordinator initializePresentationReference() {
            presentationReference.set(presentationCoordinator);
            return presentationCoordinator;
        }

        /**
         * Синхронізує подання з моделлю й використовується як callback зміни складу карти.
         */
        private void refreshPresentation() {
            MapPresentationCoordinator coordinator = presentationReference.get();
            if (coordinator != null) {
                coordinator.refreshAll();
            }
        }

        /**
         * Зупиняє контролери й прибирає підсвічування, щоб звільнити ресурси JavaFX після тесту.
         */
        private void dispose() {
            neuronController.dispose();
            simulationController.shutdown();
            connectionController.clearDeleteHighlights();
        }
    }

    /**
     * Репозиторій, який імітує завантаження наступної карти в ту саму модель редактора.
     */
    private static final class TestMapRepository implements MapRepository {

        private final CameraState cameraState;
        private final double tickMillis;
        private final NeuronService neuronService;
        private NeuronMapModel loadedModel;

        /**
         * Створює репозиторій із параметрами камери та симуляції і, за потреби, новою картою.
         *
         * @param cameraState стан камери, який потрібно завантажити.
         * @param tickMillis тривалість такту симуляції.
         */
        private TestMapRepository(CameraState cameraState, double tickMillis) {
            this(cameraState, tickMillis, null);
        }

        /**
         * Створює репозиторій, який може наповнити модель новим нейроном під час завантаження.
         *
         * @param cameraState стан камери, який потрібно завантажити.
         * @param tickMillis тривалість такту симуляції.
         * @param neuronService служба для створення вмісту наступної карти; може бути {@code null}.
         */
        private TestMapRepository(
                CameraState cameraState,
                double tickMillis,
                NeuronService neuronService
        ) {
            this.cameraState = cameraState;
            this.tickMillis = tickMillis;
            this.neuronService = neuronService;
        }

        /** Повертає стабільний тестовий шлях до бази даних. */
        @Override
        public Path databasePath() {
            return Path.of("test-project.db");
        }

        /** Повертає заданий у тесті стан камери. */
        @Override
        public CameraState loadCameraState() {
            return cameraState;
        }

        /** Повертає задану в тесті тривалість такту симуляції. */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return tickMillis;
        }

        /** Очищає попередню модель і, якщо задано службу, додає нейрон наступного проєкту. */
        @Override
        public void loadInto(NeuronMapModel model) {
            loadedModel = model;
            if (neuronService == null) {
                return;
            }
            model.clear();
            neuronService.create(NeuronType.INHIBITORY, 420.0, 260.0);
        }

        /** Не виконує запис швидкості, оскільки тестовий репозиторій не зберігає файлів. */
        @Override
        public void saveSimulationTickMillis(double millis) {
            // Тест не перевіряє запис налаштувань проєкту.
        }

        /** Не виконує запис карти, оскільки тестовий репозиторій не зберігає файлів. */
        @Override
        public void save(NeuronMapModel model, CameraState cameraState) {
            // Тест не перевіряє постійне збереження карти.
        }

        /** Закриває тестовий репозиторій без зовнішніх ресурсів. */
        @Override
        public void close() {
            // Тестовий репозиторій не відкриває файлових дескрипторів.
        }
    }

    /**
     * Підставляє реальне оновлення дерева подань у сценарій активації проєкту, залишаючи інші дії порожніми.
     */
    private static final class PresentationSessionActions implements EditorProjectSessionCoordinator.SessionActions {

        private final MapPresentationCoordinator presentation;

        /**
         * Створює набір тестових дій, що делегує синхронізацію координатору презентації.
         *
         * @param presentation координатор фактичних JavaFX-подань.
         */
        private PresentationSessionActions(MapPresentationCoordinator presentation) {
            this.presentation = presentation;
        }

        /** Не запускає симуляцію в ізольованому тесті життєвого циклу. */
        @Override
        public void stopSimulation() {
        }

        /** Не змінює підсвічування зв'язків у цьому сценарії. */
        @Override
        public void clearConnectionHighlights() {
        }

        /** Не має незавершених взаємодій, які потрібно скасовувати. */
        @Override
        public void cancelInteractions() {
        }

        /** Не показує меню нейрона в тестовому сценарії. */
        @Override
        public void hideNeuronMenu() {
        }

        /** Не планує відкладеного збереження камери. */
        @Override
        public void cancelPendingCameraSave() {
        }

        /** Не зберігає модель, оскільки перевіряється лише перемикання подань. */
        @Override
        public void saveCurrentProject() {
        }

        /** Повертає {@code false}, бо симуляція перед відкриттям меню не працює. */
        @Override
        public boolean pauseSimulationForModal() {
            return false;
        }

        /** Не відновлює симуляцію в цьому тесті. */
        @Override
        public void resumeSimulationFromModal() {
        }

        /** Не запускає анімації зв'язків у тестовому сценарії. */
        @Override
        public void pauseConnectionAnimations() {
        }

        /** Не відновлює анімації зв'язків у тестовому сценарії. */
        @Override
        public void resumeConnectionAnimations() {
        }

        /** Не змінює контролер симуляції в тесті перемикання проєкту. */
        @Override
        public void updateSimulationSpeed(double millis) {
        }

        /** Виконує справжню синхронізацію JavaFX-подань із завантаженою моделлю. */
        @Override
        public void synchronizeViewsWithModel() {
            presentation.synchronizeViewsWithModel();
        }

        /** Не змінює камеру на графічному полотні в межах цього тесту. */
        @Override
        public void applyCamera() {
        }

        /** Не змінює сторінку меню в тестовому сценарії. */
        @Override
        public void showMainPage() {
        }
    }
}
