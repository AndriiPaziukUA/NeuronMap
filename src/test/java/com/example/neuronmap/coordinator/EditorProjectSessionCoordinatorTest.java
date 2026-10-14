package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.MapService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє перехід між станами редакторської сесії та відновлення збережених налаштувань проєкту.
 */
class EditorProjectSessionCoordinatorTest {

    /**
     * Перевіряє порядок очищення редактора перед переходом і скидання вибору та тимчасового режиму.
     */
    @Test
    void prepareForProjectSwitchStopsAndResetsSessionInOrder() {
        NeuronMapModel model = new NeuronMapModel();
        MapService mapService = new MapService(model, new TestMapRepository(CameraState.defaultState(), 30.0));
        EditorState state = new EditorState(1.25, 15.0, -5.0);
        state.selectOnly("selected-neuron");
        state.setSelectedNeuronForMenu("selected-neuron");
        state.enterRotateMode();
        TestSessionActions actions = new TestSessionActions();
        EditorProjectSessionCoordinator coordinator = createCoordinator(
                mapService, state, actions, new AtomicReference<>(), new double[]{30.0}, new AtomicReference<>()
        );

        coordinator.prepareForProjectSwitch();

        assertEquals(List.of("stopSimulation", "clearConnectionHighlights", "cancelInteractions", "hideNeuronMenu",
                "cancelPendingCameraSave", "saveCurrentProject"), actions.events);
        assertTrue(state.selectedNeuronIds().isEmpty());
        assertNull(state.selectedNeuronForMenu());
        assertFalse(state.isSpecialModeActive());
    }

    /** Перевіряє відновлення симуляції та анімацій, які були призупинені лише для показу меню. */
    @Test
    void resumeAfterMenuRestoresPreviouslyRunningSimulation() {
        MapService mapService = new MapService(new NeuronMapModel(),
                new TestMapRepository(CameraState.defaultState(), 30.0));
        TestSessionActions actions = new TestSessionActions();
        EditorProjectSessionCoordinator coordinator = createCoordinator(
                mapService, new EditorState(1.0, 0.0, 0.0), actions,
                new AtomicReference<>(), new double[]{30.0}, new AtomicReference<>()
        );

        coordinator.pauseForMenu();
        coordinator.resumeAfterMenu();

        assertEquals(List.of("pauseSimulationForModal", "pauseConnectionAnimations", "resumeConnectionAnimations",
                "resumeSimulationFromModal"), actions.events);
    }

    /** Перевіряє, що закриття меню після перемикання не відновлює симуляцію попередньої сесії. */
    @Test
    void projectSwitchPreventsResumingPreviousSimulationAfterMenuCloses() {
        MapService mapService = new MapService(new NeuronMapModel(),
                new TestMapRepository(CameraState.defaultState(), 30.0));
        EditorState state = new EditorState(1.0, 0.0, 0.0);
        TestSessionActions actions = new TestSessionActions();
        EditorProjectSessionCoordinator coordinator = createCoordinator(
                mapService, state, actions, new AtomicReference<>(), new double[]{30.0}, new AtomicReference<>()
        );

        coordinator.pauseForMenu();
        coordinator.prepareForProjectSwitch();
        coordinator.resumeAfterMenu();

        assertFalse(actions.events.contains("resumeSimulationFromModal"));
        assertTrue(actions.events.contains("resumeConnectionAnimations"));
    }

    /** Перевіряє, що некоректна збережена швидкість не успадковується від попереднього проєкту. */
    @Test
    void activateProjectFallsBackToConfiguredDefaultWhenPersistedSpeedIsOutOfRange() {
        MapService mapService = new MapService(new NeuronMapModel(),
                new TestMapRepository(CameraState.defaultState(), 35.0));
        TestSessionActions actions = new TestSessionActions();
        double[] speed = {35.0};
        EditorProjectSessionCoordinator coordinator = createCoordinator(
                mapService, new EditorState(1.0, 0.0, 0.0), actions,
                new AtomicReference<>(), speed, new AtomicReference<>()
        );
        TestMapRepository nextRepository = new TestMapRepository(CameraState.defaultState(), 900.0);
        ProjectDescriptor project = new ProjectDescriptor("invalid-speed-project", "Проєкт з некоректною швидкістю",
                Path.of("invalid-speed.db"), Instant.EPOCH);

        coordinator.activateProject(project, nextRepository, true);

        assertEquals(50.0, speed[0], 0.0001);
    }

    /** Перевіряє, що проєкт без збереженої швидкості отримує типове значення конфігурації. */
    @Test
    void activateProjectWithoutSavedSpeedUsesConfiguredDefault() {
        MapService mapService = new MapService(new NeuronMapModel(),
                new TestMapRepository(CameraState.defaultState(), 35.0));
        TestSessionActions actions = new TestSessionActions();
        double[] speed = {35.0};
        EditorProjectSessionCoordinator coordinator = createCoordinator(
                mapService, new EditorState(1.0, 0.0, 0.0), actions,
                new AtomicReference<>(), speed, new AtomicReference<>()
        );
        TestMapRepository nextRepository = new TestMapRepository(CameraState.defaultState(), Double.NaN);
        ProjectDescriptor project = new ProjectDescriptor("missing-speed-project", "Проєкт без швидкості",
                Path.of("missing-speed.db"), Instant.EPOCH);

        coordinator.activateProject(project, nextRepository, true);

        assertEquals(50.0, speed[0], 0.0001);
    }

    /**
     * Перевіряє відновлення репозиторію, камери та швидкості, а також синхронізацію подань нового проєкту.
     */
    @Test
    void activateProjectRestoresSavedSessionAndUpdatesLastOpenedProject() {
        NeuronMapModel model = new NeuronMapModel();
        TestMapRepository previousRepository = new TestMapRepository(CameraState.defaultState(), 35.0);
        MapService mapService = new MapService(model, previousRepository);
        EditorState state = new EditorState(1.0, 0.0, 0.0);
        state.selectOnly("previous-neuron");
        state.setSelectedNeuronForMenu("previous-neuron");
        state.enterRotateMode();
        TestSessionActions actions = new TestSessionActions();
        AtomicReference<ProjectDescriptor> activeProject = new AtomicReference<>();
        AtomicReference<ProjectDescriptor> lastOpenedProject = new AtomicReference<>();
        double[] speed = {35.0};
        EditorProjectSessionCoordinator coordinator = createCoordinator(
                mapService, state, actions, activeProject, speed, lastOpenedProject
        );
        CameraState savedCamera = new CameraState(1.75, -40.0, 62.0);
        TestMapRepository nextRepository = new TestMapRepository(savedCamera, 90.0);
        ProjectDescriptor project = new ProjectDescriptor("project-2", "Другий проєкт",
                Path.of("project-2.db"), Instant.EPOCH);

        coordinator.activateProject(project, nextRepository, true);

        assertTrue(previousRepository.closed);
        assertTrue(mapService.isActiveRepository(nextRepository));
        assertNotSame(model, nextRepository.loadedModel);
        assertSame(project, activeProject.get());
        assertSame(project, lastOpenedProject.get());
        assertEquals(savedCamera.zoom(), state.zoom(), 0.0001);
        assertEquals(savedCamera.panX(), state.panX(), 0.0001);
        assertEquals(savedCamera.panY(), state.panY(), 0.0001);
        assertTrue(state.selectedNeuronIds().isEmpty());
        assertNull(state.selectedNeuronForMenu());
        assertFalse(state.isSpecialModeActive());
        assertEquals(90.0, speed[0], 0.0001);
        assertEquals(List.of("updateSimulationSpeed", "synchronizeViewsWithModel", "applyCamera", "showMainPage"),
                actions.events);
    }

    /** Перевіряє резервну швидкість для нового тимчасового проєкту та очищення ознаки останнього відкриття. */
    @Test
    void activateTransientProjectUsesDefaultSpeedAndClearsLastOpenedProject() {
        MapService mapService = new MapService(new NeuronMapModel(),
                new TestMapRepository(CameraState.defaultState(), 45.0));
        EditorState state = new EditorState(1.0, 0.0, 0.0);
        TestSessionActions actions = new TestSessionActions();
        AtomicReference<ProjectDescriptor> activeProject = new AtomicReference<>();
        AtomicReference<ProjectDescriptor> lastOpenedProject = new AtomicReference<>(
                new ProjectDescriptor("old", "Старий", Path.of("old.db"), Instant.EPOCH));
        double[] speed = {45.0};
        EditorProjectSessionCoordinator coordinator = createCoordinator(
                mapService, state, actions, activeProject, speed, lastOpenedProject
        );
        TestMapRepository transientRepository = new TestMapRepository(CameraState.defaultState(), 90.0);
        ProjectDescriptor transientProject = new ProjectDescriptor("temporary", "Новий проєкт",
                Path.of("temporary.db"), null);

        coordinator.activateProject(transientProject, transientRepository, false);

        assertSame(transientProject, activeProject.get());
        assertNull(lastOpenedProject.get());
        assertEquals(50.0, speed[0], 0.0001);
        assertEquals(CameraState.defaultState().zoom(), state.zoom(), 0.0001);
    }

    /**
     * Створює координатор зі спільним набором параметрів для тестів.
     *
     * @param mapService служба карти з тестовим репозиторієм.
     * @param state стан редактора.
     * @param actions реєстратор зовнішніх дій.
     * @param activeProject отримувач активного проєкту.
     * @param speed змінне значення для перевірки тривалості такту після активації проєкту.
     * @param lastOpenedProject отримувач останнього відкритого збереженого проєкту.
     * @return готовий координатор редакторської сесії.
     */
    private EditorProjectSessionCoordinator createCoordinator(
            MapService mapService,
            EditorState state,
            TestSessionActions actions,
            AtomicReference<ProjectDescriptor> activeProject,
            double[] speed,
            AtomicReference<ProjectDescriptor> lastOpenedProject
    ) {
        return new EditorProjectSessionCoordinator(
                mapService,
                new HistoryService(mapService.model()),
                state,
                activeProject::set,
                value -> speed[0] = value,
                lastOpenedProject::set,
                5.0,
                500.0,
                50.0,
                actions
        );
    }

    /**
     * Репозиторій для тестів, який повертає наперед задані параметри й запам'ятовує закриття.
     */
    private static final class TestMapRepository implements MapRepository {

        private final CameraState cameraState;
        private final double simulationTickMillis;
        private boolean closed;
        private NeuronMapModel loadedModel;

        /**
         * Створює тестовий репозиторій із фіксованим станом камери та швидкістю.
         *
         * @param cameraState стан камери, який повертає репозиторій.
         * @param simulationTickMillis тривалість такту, яку повертає репозиторій, або {@link Double#NaN}, якщо її немає.
         */
        private TestMapRepository(CameraState cameraState, double simulationTickMillis) {
            this.cameraState = cameraState;
            this.simulationTickMillis = simulationTickMillis;
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

        /** Повертає задану швидкість або резервне значення, якщо швидкість не збережена. */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return Double.isFinite(simulationTickMillis) ? simulationTickMillis : fallbackMillis;
        }

        /** Очищає модель, імітуючи завантаження вмісту проєкту. */
        @Override
        public void loadInto(NeuronMapModel model) {
            loadedModel = model;
            model.clear();
        }

        /** Не виконує запис до диска, оскільки тестовий репозиторій не має сховища. */
        @Override
        public void saveSimulationTickMillis(double millis) {
            // Тест не перевіряє запис налаштувань.
        }

        /** Не виконує запис до диска, оскільки тестовий репозиторій не має сховища. */
        @Override
        public void save(NeuronMapModel model, CameraState cameraState) {
            // Тест не перевіряє збереження карти.
        }

        /** Позначає репозиторій закритим після заміни активного сховища. */
        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * Фіксує викликані дії, щоб перевірити порядок координації без запуску JavaFX-вікна.
     */
    private static final class TestSessionActions implements EditorProjectSessionCoordinator.SessionActions {

        private final List<String> events = new ArrayList<>();

        /** Записує зупинку симуляції. */
        @Override
        public void stopSimulation() {
            events.add("stopSimulation");
        }

        /** Записує очищення підсвічування зв'язків. */
        @Override
        public void clearConnectionHighlights() {
            events.add("clearConnectionHighlights");
        }

        /** Записує скасування поточних взаємодій. */
        @Override
        public void cancelInteractions() {
            events.add("cancelInteractions");
        }

        /** Записує приховування меню нейрона. */
        @Override
        public void hideNeuronMenu() {
            events.add("hideNeuronMenu");
        }

        /** Записує скасування відкладеного збереження камери. */
        @Override
        public void cancelPendingCameraSave() {
            events.add("cancelPendingCameraSave");
        }

        /** Записує збереження поточного проєкту. */
        @Override
        public void saveCurrentProject() {
            events.add("saveCurrentProject");
        }

        /** Повертає значення, що симуляція до відкриття меню була активною. */
        @Override
        public boolean pauseSimulationForModal() {
            events.add("pauseSimulationForModal");
            return true;
        }

        /** Записує відновлення симуляції після меню. */
        @Override
        public void resumeSimulationFromModal() {
            events.add("resumeSimulationFromModal");
        }

        /** Записує призупинення анімацій зв'язків. */
        @Override
        public void pauseConnectionAnimations() {
            events.add("pauseConnectionAnimations");
        }

        /** Записує відновлення анімацій зв'язків. */
        @Override
        public void resumeConnectionAnimations() {
            events.add("resumeConnectionAnimations");
        }

        /** Записує застосування нової швидкості симуляції. */
        @Override
        public void updateSimulationSpeed(double millis) {
            events.add("updateSimulationSpeed");
        }

        /** Записує синхронізацію візуальних подань. */
        @Override
        public void synchronizeViewsWithModel() {
            events.add("synchronizeViewsWithModel");
        }

        /** Записує застосування камери до робочої області. */
        @Override
        public void applyCamera() {
            events.add("applyCamera");
        }

        /** Записує повернення меню на головну сторінку. */
        @Override
        public void showMainPage() {
            events.add("showMainPage");
        }
    }
}
