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
import com.example.neuronmap.controller.PulseAnimationController;
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
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.service.ProjectCatalogService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.support.ImmediateTooltipManager;
import com.example.neuronmap.view.editor.MainView;
import com.example.neuronmap.view.editor.NeuronLayerOrderManager;
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Складає компоненти редактора та координує маршрутизацію дій між його підсистемами.
 */
public final class MapEditorCoordinator {

    private final MapService mapService;
    private final NeuronService neuronService;
    private final ConnectionService connectionService;
    private final GroupService groupService;
    private final HistoryService historyService;
    private final EditorSaveCoordinator editorSaveCoordinator;
    private final ProjectCatalogService projectCatalog;
    private final ProjectWorkflowCoordinator projectWorkflowCoordinator;
    private final EditorProjectSessionCoordinator editorProjectSessionCoordinator;
    private final LocalizationService localization;
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
    private final SelectionController selectionController;
    private final NeuronInteractionController neuronController;
    private final CameraController cameraController;
    private final NeuronLayerOrderManager layerOrderController;
    private final NeuronMenuView neuronMenuView;
    private final MapPresentationCoordinator presentation;
    private final MapInteractionCoordinator interaction;
    private final EditorHistoryCoordinator historyCoordinator;
    private final EditorKeyboardController keyboardController;
    private final MainMenuController mainMenuController;
    private final ProjectDirectoryWatcher projectDirectoryWatcher;
    private ImmediateTooltipManager tooltipManager;
    private ProjectDescriptor currentProject;
    private boolean historyInitialized;
    private Scene scene;

    /**
     * Створює координатор редактора з переданими службами та дією завершення роботи.
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

        this.localization = Objects.requireNonNull(localization, "localization");
        this.projectCatalog = Objects.requireNonNull(projectCatalog, "projectCatalog");
        Objects.requireNonNull(mapRepositoryFactory, "mapRepositoryFactory");
        this.mapService = application.map();
        this.neuronService = application.neurons();
        this.connectionService = application.connections();
        this.groupService = application.groups();
        this.currentProject = this.projectCatalog.descriptorForDatabasePath(
                mapService.databasePath()
        );

        this.historyService = new HistoryService(neuronService.model());
        this.editorSaveCoordinator = new EditorSaveCoordinator(
                mapService,
                projectCatalog,
                historyService
        );
        this.minSimulationTickMillis = config.simulation().minTickMillis();
        this.maxSimulationTickMillis = config.simulation().maxTickMillis();
        this.defaultSimulationTickMillis = config.simulation().defaultTickMillis();
        this.simulationTickMillis = EditorProjectSessionCoordinator.resolveInitialSimulationTickMillis(
                mapService.loadSimulationTickMillis(config.simulation().defaultTickMillis()),
                config.simulation().defaultTickMillis(),
                minSimulationTickMillis,
                maxSimulationTickMillis
        );

        CameraState camera = mapService.loadCameraState();
        state = new EditorState(camera.zoom(), camera.panX(), camera.panY());

        view = new MainView(
                this::beginAddNeuronMode,
                this::groupSelection,
                this::ungroupSelection,
                this::exitDeleteConnectionMode,
                this::toggleSimulationPause,
                this::stopSimulationSignals,
                this::changeSimulationSpeed,
                simulationTickMillis,
                localization
        );

        layerOrderController = new NeuronLayerOrderManager(
                neuronViews,
                connectionViews,
                () -> neuronService.model().connections()
        );

        neuronMenuView = new NeuronMenuView(
                view.toolbar().node(),
                state::selectedNeuronForMenu,
                this::toggleNeuronDirection,
                localization
        );
        neuronMenuView.removeDeleteConnectionModeExitButton();

        mapService.load();
        InitialProjectInitializer initialProjectInitializer = new InitialProjectInitializer(application);
        if (initialProjectInitializer.initializeIfRequired(currentProject)) {
            updateStatus(localization.text("status.demo_created"));
            saveNow();
        }
        historyService.initialize();
        historyInitialized = true;

        SimulationStepPresenter stepPresenter = new SimulationStepPresenter(
                view.workspace(),
                neuronViews,
                this::refreshNeuronVisuals,
                new PulseAnimationController(view.workspace())
        );
        simulationController = new SimulationController(
                neuronService,
                new SimulationService(),
                stepPresenter,
                this::saveNow,
                this::updateStatus,
                view.toolbar()::setSimulationPaused,
                view.toolbar()::setSimulationControlsVisible,
                simulationTickMillis,
                config.simulation().minTickMillis(),
                config.simulation().maxTickMillis(),
                localization
        );
        simulationSpeedCoordinator = new SimulationSpeedCoordinator(
                mapService,
                this::applySimulationSpeed,
                this::refreshCurrentProjectAfterSpeedChange,
                this::updateStatus,
                localization,
                minSimulationTickMillis,
                maxSimulationTickMillis
        );

        connectionController = new ConnectionController(
                connectionService,
                state,
                view.workspace(),
                neuronViews,
                this::refreshMapPresentation,
                this::saveNow,
                this::updateStatus,
                localization
        );

        selectionController = new SelectionController(
                groupService,
                state,
                this::refreshNeuronVisuals,
                this::saveNow,
                this::updateStatus,
                localization
        );

        NeuronClipboardService clipboardService = new NeuronClipboardService(
                neuronService,
                groupService
        );
        neuronController = new NeuronInteractionController(
                neuronService,
                groupService,
                selectionController,
                clipboardService,
                state,
                view.workspace(),
                neuronViews,
                rotationHandles,
                connectionController,
                simulationController,
                this::saveNow,
                this::refreshMapPresentation,
                this::updateStatus,
                neuronMenuView,
                localization
        );

        presentation = new MapPresentationCoordinator(
                neuronService,
                view.workspace(),
                neuronViews,
                connectionViews,
                rotationHandles,
                neuronController,
                connectionController,
                layerOrderController
        );

        interaction = new MapInteractionCoordinator(
                neuronService,
                state,
                view.workspace(),
                selectionController,
                connectionController,
                neuronController,
                simulationController,
                presentation,
                this::saveNow,
                this::updateStatus,
                localization
        );
        interaction.configureToolbarButtons(
                view.toolbar().addExcitatoryButton(),
                view.toolbar().addInhibitoryButton()
        );

        historyCoordinator = new EditorHistoryCoordinator(
                historyService,
                simulationController,
                connectionController,
                neuronController,
                state,
                view.workspace(),
                interaction::dispose,
                presentation,
                this::saveNow,
                this::updateStatus,
                localization
        );
        keyboardController = new EditorKeyboardController(
                historyCoordinator::undo,
                historyCoordinator::redo,
                neuronController::deleteSelectedNeurons,
                interaction::cancelInteractions
        );

        cameraController = new CameraController(
                state,
                view.workspace(),
                presentation::refreshScreenSpaceOverlays,
                this::saveNow,
                state::isSpecialModeActive,
                neuronController::isInteractiveTarget,
                this::updateStatus,
                point -> view.toolbar().setCameraCoordinates(point.getX(), point.getY()),
                config.camera().zoomFactor(),
                localization
        );

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
     * Створює набір зовнішніх дій, через які координатор сесії керує контролерами й поданнями редактора.
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

            /**
             * Оновлює швидкість у контролері симуляції та панелі інструментів.
             *
             * @param millis нова тривалість такту симуляції в мілісекундах.
             */
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

    /**
     * Створює сцену редактора та налаштовує її основні компоненти.
     *
     * @param width ширина видимої області.
     * @param height висота видимої області.
     */
    public Scene createScene(double width, double height) {
        scene = view.createScene(width, height);
        keyboardController.install(scene);

        scene.addEventFilter(
                MouseEvent.MOUSE_PRESSED,
                presentation::bringClickedNeuronToFront
        );
        mainMenuController.install(scene);
        tooltipManager = ImmediateTooltipManager.install(scene);
        return scene;
    }

    /**
     * Розміщує камеру так, щоб початковий вигляд карти був центрований у вікні.
     */
    public void centerInitialView() {
        cameraController.apply();
    }

    /**
     * Зупиняє симуляцію, знімає обробники подій, закриває сховище та звільняє ресурси редактора.
     */
    public void shutdown() {
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

    /**
     * Перемикає симуляцію між виконанням і паузою.
     */
    private void toggleSimulationPause() {
        simulationController.pauseOrResume();
        view.toolbar().setSimulationPaused(simulationController.isPaused());
    }

    /**
     * Зупиняє симуляцію та прибирає незавершені сигнали й анімації.
     */
    private void stopSimulationSignals() {
        simulationController.stopSignals();
        view.toolbar().setSimulationPaused(false);
    }

    /**
     * Передає зміну швидкості координатору, який перевіряє значення та зберігає його в активному проєкті.
     *
     * @param text значення тривалості такту, вибране користувачем.
     */
    private void changeSimulationSpeed(String text) {
        simulationSpeedCoordinator.changeSpeed(text);
    }

    /**
     * Застосовує збережену швидкість до активної симуляції, стану редактора та панелі інструментів.
     *
     * @param millis тривалість такту симуляції в мілісекундах.
     */
    private void applySimulationSpeed(double millis) {
        simulationController.setTickDurationMillis(millis);
        simulationTickMillis = millis;
        view.toolbar().setSimulationSpeedMillis(millis);
    }

    /** Оновлює опис активного проєкту після збереження його параметрів швидкості. */
    private void refreshCurrentProjectAfterSpeedChange() {
        currentProject = projectCatalog.descriptorForDatabasePath(mapService.databasePath());
        projectCatalog.markLastOpened(currentProject);
    }

    /**
     * Починає режим додавання нейрона та готує стан взаємодії.
     *
     * @param type тип нейрона або елемента.
     */
    private void beginAddNeuronMode(NeuronType type) {
        interaction.beginAddNeuronMode(type);
    }

    /**
     * Створює групу з поточно вибраних нейронів.
     */
    private void groupSelection() {
        interaction.groupSelection();
    }

    /**
     * Вилучає поточно вибрані нейрони зі складу їхньої групи.
     */
    private void ungroupSelection() {
        interaction.ungroupSelection();
    }

    /**
     * Завершує режим видалення зв'язків і повертає редактор до звичайної взаємодії.
     */
    private void exitDeleteConnectionMode() {
        interaction.exitDeleteConnectionMode();
    }

    /**
     * Передає зміну напрямку відповідальному контролеру контекстного меню нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    private void toggleNeuronDirection(String neuronId) {
        neuronController.toggleNeuronDirection(neuronId);
    }

    /**
     * Оновлює візуальні подання всіх елементів карти.
     */
    private void refreshMapPresentation() {
        presentation.refreshAll();
    }

    /**
     * Оновлює зовнішній вигляд нейронів за їхнім поточним типом і станом.
     */
    private void refreshNeuronVisuals() {
        presentation.refreshNeurons();
    }

    /**
     * Зберігає карту, стан камери, метадані активного проєкту та історію змін.
     */
    private void saveNow() {
        currentProject = editorSaveCoordinator.save(
                state,
                currentProject,
                historyInitialized
        );
    }

    /**
     * Передає текст повідомлення до рядка стану інтерфейсу.
     *
     * @param text текст повідомлення, яке потрібно показати.
     */
    private void updateStatus(String text) {
        view.setStatus(text);
    }

}
