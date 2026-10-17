package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.application.InitialProjectInitializer;
import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.controller.CameraController;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.EditorKeyboardController;
import com.example.neuronmap.controller.MainMenuController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.project.ProjectDirectoryWatcher;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.service.ProjectCatalogService;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.MainView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.support.ImmediateTooltipManager;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Узгоджує сценарії редактора, що охоплюють кілька підсистем, і керує життєвим циклом його сцени.
 *
 * <p>Створення циклічно пов'язаного графа контролерів делеговано {@link MapEditorComponentFactory};
 * цей клас зберігає відповідальність за сценарії проєкту, команди редактора, збереження та завершення.</p>
 */
public final class MapEditorCoordinator {

    private final MapService mapService;
    private final NeuronService neuronService;
    private final HistoryService historyService;
    private final EditorSaveCoordinator editorSaveCoordinator;
    private final ProjectCatalogService projectCatalog;
    private final ProjectWorkflowCoordinator projectWorkflowCoordinator;
    private final EditorProjectSessionCoordinator editorProjectSessionCoordinator;
    private final EditorState state;
    private final MainView view;
    private final double minSimulationTickMillis;
    private final double maxSimulationTickMillis;
    private final double defaultSimulationTickMillis;
    private double simulationTickMillis;

    private final Map<String, NeuronView> neuronViews = new HashMap<>();
    private final Map<String, ConnectionView> connectionViews = new HashMap<>();
    private final Map<String, RotationHandleView> rotationHandles = new HashMap<>();

    private final SimulationController simulationController;
    private final SimulationSpeedCoordinator simulationSpeedCoordinator;
    private final ConnectionController connectionController;
    private final NeuronInteractionController neuronController;
    private final CameraController cameraController;
    private final MapPresentationCoordinator presentation;
    private final MapInteractionCoordinator interaction;
    private final EditorKeyboardController keyboardController;
    private final MainMenuController mainMenuController;
    private final ProjectDirectoryWatcher projectDirectoryWatcher;
    private ImmediateTooltipManager tooltipManager;
    private UiProcessCoordinator.Registration tooltipRegistration;
    private ProjectDescriptor currentProject;
    private boolean historyInitialized;
    private Scene scene;

    /**
     * Створює координатор редактора, ініціалізує стан карти й передає складання графа компонентів фабриці.
     *
     * @param application прикладна служба, яка надає доступ до служб карти й збереження.
     * @param config завантажена конфігурація застосунку.
     * @param localization служба локалізації інтерфейсу.
     * @param projectCatalog готова служба каталогу проєктів.
     * @param mapRepositoryFactory фабрика репозиторіїв проєктів.
     * @param exitApplication дія завершення роботи застосунку.
     */
    public MapEditorCoordinator(
            NeuronMapApplicationService application,
            AppConfig config,
            LocalizationService localization,
            ProjectCatalogService projectCatalog,
            MapRepositoryFactory mapRepositoryFactory,
            Runnable exitApplication
    ) {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(config, "config");
        this.projectCatalog = Objects.requireNonNull(projectCatalog, "projectCatalog");
        Objects.requireNonNull(mapRepositoryFactory, "mapRepositoryFactory");
        mapService = application.map();
        neuronService = application.neurons();
        ConnectionService connectionService = application.connections();
        GroupService groupService = application.groups();
        currentProject = this.projectCatalog.descriptorForDatabasePath(mapService.databasePath());

        historyService = new HistoryService(neuronService.model());
        editorSaveCoordinator = new EditorSaveCoordinator(mapService, projectCatalog, historyService);
        minSimulationTickMillis = config.simulation().minTickMillis();
        maxSimulationTickMillis = config.simulation().maxTickMillis();
        defaultSimulationTickMillis = config.simulation().defaultTickMillis();
        simulationTickMillis = EditorProjectSessionCoordinator.resolveInitialSimulationTickMillis(
                mapService.loadSimulationTickMillis(config.simulation().defaultTickMillis()),
                config.simulation().defaultTickMillis(), minSimulationTickMillis, maxSimulationTickMillis);

        CameraState camera = mapService.loadCameraState();
        state = new EditorState(camera.zoom(), camera.panX(), camera.panY());
        view = new MainView(
                this::beginAddNeuronMode,
                this::groupSelection,
                this::ungroupSelection,
                this::toggleSimulationPause,
                this::stopSimulationSignals,
                this::changeSimulationSpeed,
                simulationTickMillis,
                localization
        );

        mapService.load();
        InitialProjectInitializer initialProjectInitializer = new InitialProjectInitializer(application);
        if (initialProjectInitializer.initializeIfRequired(currentProject)) {
            updateStatus(localization.text("status.demo_created"));
            saveNow();
        }
        historyService.initialize();
        historyInitialized = true;

        MapEditorComponentFactory.Components components = MapEditorComponentFactory.create(
                new MapEditorComponentFactory.Inputs(
                        config,
                        localization,
                        mapService,
                        neuronService,
                        connectionService,
                        groupService,
                        historyService,
                        state,
                        view,
                        neuronViews,
                        connectionViews,
                        rotationHandles,
                        simulationTickMillis,
                        new MapEditorComponentFactory.Actions(
                                this::saveNow,
                                this::updateStatus,
                                this::refreshMapPresentation,
                                this::refreshNeuronVisuals,
                                this::applySimulationSpeed,
                                this::refreshCurrentProjectAfterSpeedChange,
                                this::toggleNeuronDirection
                        )
                )
        );
        simulationController = components.simulationController();
        simulationSpeedCoordinator = components.simulationSpeedCoordinator();
        connectionController = components.connectionController();
        neuronController = components.neuronController();
        cameraController = components.cameraController();
        presentation = components.presentation();
        interaction = components.interaction();
        keyboardController = components.keyboardController();

        editorProjectSessionCoordinator = new EditorProjectSessionCoordinator(
                mapService,
                historyService,
                state,
                project -> currentProject = project,
                speed -> simulationTickMillis = speed,
                projectCatalog::markLastOpened,
                minSimulationTickMillis,
                maxSimulationTickMillis,
                defaultSimulationTickMillis,
                createEditorProjectSessionActions()
        );
        editorProjectSessionCoordinator.registerUiProcess(
                "status-message",
                view::pauseStatusMessageLifecycle,
                view::resumeStatusMessageLifecycle
        );
        editorProjectSessionCoordinator.registerUiProcess(
                "camera-save-debounce",
                cameraController::pausePendingSave,
                cameraController::resumePendingSave
        );

        projectWorkflowCoordinator = new ProjectWorkflowCoordinator(
                projectCatalog,
                localization,
                mapService,
                mapRepositoryFactory,
                () -> currentProject,
                project -> currentProject = project,
                editorProjectSessionCoordinator::prepareForProjectSwitch,
                this::saveNow,
                editorProjectSessionCoordinator::activateProject
        );

        mainMenuController = new MainMenuController(
                view.mainMenu(),
                localization,
                projectCatalog::listProjects,
                projectWorkflowCoordinator::createNewProject,
                projectWorkflowCoordinator::openProject,
                projectWorkflowCoordinator::renameProject,
                projectWorkflowCoordinator::deleteProject,
                exitApplication,
                editorProjectSessionCoordinator::pauseForMenu,
                editorProjectSessionCoordinator::resumeAfterMenu
        );

        projectDirectoryWatcher = new ProjectDirectoryWatcher(
                projectCatalog.storageDirectory(),
                mainMenuController::refreshProjects
        );
        projectDirectoryWatcher.start();

        neuronController.loadViews();
        presentation.refreshAll();
        interaction.install();
        cameraController.install();
        connectionController.install();
    }

    /**
     * Створює дії, через які координатор сесії керує контролерами й поданнями редактора.
     *
     * @return адаптер дій поточної редакторської сесії.
     */
    private EditorProjectSessionCoordinator.SessionActions createEditorProjectSessionActions() {
        return new EditorProjectSessionCoordinator.SessionActions() {
            /** Зупиняє активний сеанс симуляції. */
            @Override
            public void stopSimulation() {
                simulationController.stop();
            }

            /** Прибирає підсвічування зв'язків у режимі видалення. */
            @Override
            public void clearConnectionHighlights() {
                connectionController.clearDeleteHighlights();
            }

            /** Скасовує активні взаємодії з картою. */
            @Override
            public void cancelInteractions() {
                interaction.cancelInteractions();
            }

            /** Приховує контекстне меню нейрона. */
            @Override
            public void hideNeuronMenu() {
                neuronController.hideMenu();
            }

            /** Скасовує таймер відкладеного збереження камери. */
            @Override
            public void cancelPendingCameraSave() {
                cameraController.cancelPendingSave();
            }

            /** Зберігає поточний проєкт перед зміною сховища. */
            @Override
            public void saveCurrentProject() {
                saveNow();
            }

            /** Призупиняє симуляцію для модального меню. */
            @Override
            public boolean pauseSimulationForModal() {
                return simulationController.pauseForModal();
            }

            /** Відновлює симуляцію після закриття модального меню. */
            @Override
            public void resumeSimulationFromModal() {
                simulationController.resumeFromModal();
            }

            /** Призупиняє анімації зв'язків. */
            @Override
            public void pauseConnectionAnimations() {
                connectionController.pauseAnimations();
            }

            /** Відновлює анімації зв'язків. */
            @Override
            public void resumeConnectionAnimations() {
                connectionController.resumeAnimations();
            }

            /** Оновлює швидкість у контролері симуляції та панелі інструментів. */
            @Override
            public void updateSimulationSpeed(double millis) {
                simulationController.setTickDurationMillis(millis);
                view.toolbar().setSimulationSpeedMillis(millis);
            }

            /** Узгоджує подання карти з новим вмістом моделі. */
            @Override
            public void synchronizeViewsWithModel() {
                presentation.synchronizeViewsWithModel();
            }

            /** Застосовує відновлену камеру до робочої області. */
            @Override
            public void applyCamera() {
                cameraController.apply();
            }

            /** Повертає меню на головну сторінку після перемикання проєкту. */
            @Override
            public void showMainPage() {
                view.mainMenu().showMainPage();
            }
        };
    }

    /** Створює сцену редактора, встановлює обробники клавіш, меню та централізоване керування підказками. */
    public Scene createScene(double width, double height) {
        scene = view.createScene(width, height);
        keyboardController.install(scene);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, presentation::bringClickedNeuronToFront);
        mainMenuController.install(scene);
        tooltipManager = ImmediateTooltipManager.install(scene);
        tooltipRegistration = editorProjectSessionCoordinator.registerUiProcess(
                "immediate-tooltips", tooltipManager::pause, tooltipManager::resume);
        return scene;
    }

    /** Розміщує камеру так, щоб початковий вигляд карти був центрований у вікні. */
    public void centerInitialView() {
        cameraController.apply();
    }

    /** Зупиняє процеси редактора, знімає обробники подій і звільняє ресурси. */
    public void shutdown() {
        editorProjectSessionCoordinator.disposeUiProcesses();
        if (tooltipRegistration != null) {
            tooltipRegistration.close();
            tooltipRegistration = null;
        }
        keyboardController.dispose();
        projectDirectoryWatcher.close();
        mainMenuController.dispose(scene);
        scene = null;
        simulationController.shutdown();
        connectionController.clearDeleteHighlights();
        interaction.dispose();
        neuronController.dispose();
        cameraController.cancelPendingSave();
        view.dispose();
        if (tooltipManager != null) {
            tooltipManager.dispose();
            tooltipManager = null;
        }
        saveNow();
        mapService.close();
    }

    /** Перемикає симуляцію між виконанням і ручною паузою. */
    private void toggleSimulationPause() {
        simulationController.pauseOrResume();
        view.toolbar().setSimulationPaused(simulationController.isPaused());
    }

    /** Зупиняє симуляцію та прибирає незавершені сигнали й анімації. */
    private void stopSimulationSignals() {
        simulationController.stopSignals();
        view.toolbar().setSimulationPaused(false);
    }

    /** Передає швидкість координатору, який перевіряє значення та зберігає його в активному проєкті. */
    private void changeSimulationSpeed(String text) {
        simulationSpeedCoordinator.changeSpeed(text);
    }

    /** Застосовує швидкість до симуляції, стану редактора та панелі інструментів. */
    private void applySimulationSpeed(double millis) {
        simulationController.setTickDurationMillis(millis);
        simulationTickMillis = millis;
        view.toolbar().setSimulationSpeedMillis(millis);
    }

    /** Оновлює опис активного проєкту після збереження його параметрів швидкості. */
    private void refreshCurrentProjectAfterSpeedChange() {
        currentProject = projectCatalog.descriptorForDatabasePath(mapService.databasePath());
        projectCatalog.markLastOpened(Optional.ofNullable(currentProject));
    }

    /** Починає режим додавання нейрона та готує стан взаємодії. */
    private void beginAddNeuronMode(NeuronType type) {
        interaction.beginAddNeuronMode(type);
    }

    /** Створює групу з поточно вибраних нейронів. */
    private void groupSelection() {
        interaction.groupSelection();
    }

    /** Вилучає поточно вибрані нейрони зі складу їхньої групи. */
    private void ungroupSelection() {
        interaction.ungroupSelection();
    }

    /** Передає зміну напрямку відповідальному контролеру контекстного меню нейрона. */
    private void toggleNeuronDirection(String neuronId) {
        neuronController.toggleNeuronDirection(neuronId);
    }

    /** Оновлює візуальні подання всіх елементів карти. */
    private void refreshMapPresentation() {
        presentation.refreshAll();
    }

    /** Оновлює зовнішній вигляд нейронів за їхнім поточним типом і станом. */
    private void refreshNeuronVisuals() {
        presentation.refreshNeurons();
    }

    /** Зберігає карту, камеру, метадані активного проєкту та історію змін. */
    private void saveNow() {
        currentProject = editorSaveCoordinator.save(state, currentProject, historyInitialized);
    }

    /** Передає текст повідомлення до рядка стану інтерфейсу. */
    private void updateStatus(String text) {
        view.setStatus(text);
    }
}
