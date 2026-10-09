package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.controller.CameraController;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.MainMenuController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.NeuronLayerOrderController;
import com.example.neuronmap.controller.NeuronMenuCustomizer;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.controller.SimulationStepPresenter;
import com.example.neuronmap.controller.StatusMessagePresenter;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.CameraState;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import com.example.neuronmap.persistence.ProjectDirectoryWatcher;
import com.example.neuronmap.persistence.ProjectStorageDirectoryResolver;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import com.example.neuronmap.persistence.ProjectDirectoryWatcher;
import com.example.neuronmap.persistence.ProjectStorageDirectoryResolver;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.SqliteMapRepository;
import com.example.neuronmap.persistence.TransientProjectRepository;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.service.ProjectCatalogService;
import com.example.neuronmap.simulation.SimulationSpeed;
import com.example.neuronmap.view.ImmediateTooltipManager;
import com.example.neuronmap.view.MainView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.RotationHandleView;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;

import java.util.HashMap;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Координує основні сценарії редактора: керування проєктами, взаємодію з картою, симуляцію, збереження та завершення роботи.
 */
public final class MapEditorCoordinator {

    private final MapService mapService;
    private final NeuronService neuronService;
    private final ConnectionService connectionService;
    private final GroupService groupService;
    private final HistoryService historyService;
    private final ProjectCatalogService projectCatalog;
    private final LocalizationService localization;
    private final EditorState state;
    private final MainView view;
    private final StatusMessagePresenter statusMessagePresenter;
    private final double minSimulationTickMillis;
    private final double maxSimulationTickMillis;
    private final double defaultSimulationTickMillis;
    private final Runnable exitApplication;
    private double simulationTickMillis;

    private final Map<String, NeuronView> neuronViews = new HashMap<>();
    private final Map<String, com.example.neuronmap.view.ConnectionView> connectionViews = new HashMap<>();
    private final Map<String, RotationHandleView> rotationHandles = new HashMap<>();

    private final SimulationController simulationController;
    private final ConnectionController connectionController;
    private final SelectionController selectionController;
    private final NeuronInteractionController neuronController;
    private final CameraController cameraController;
    private final NeuronLayerOrderController layerOrderController;
    private final NeuronMenuCustomizer menuCustomizer;
    private final MapPresentationCoordinator presentation;
    private final MapInteractionCoordinator interaction;
    private final EditorHistoryCoordinator historyCoordinator;
    private final MainMenuController mainMenuController;
    private final ProjectDirectoryWatcher projectDirectoryWatcher;
    private ImmediateTooltipManager tooltipManager;
    private ProjectDescriptor currentProject;
    private boolean historyInitialized;
    private boolean modalSimulationWasRunning;
    private Scene scene;

    /**
     * Створює екземпляр MapEditorCoordinator та зберігає передані залежності, потрібні для його роботи.
     *
     * @param application прикладна служба, яка надає доступ до служб карти й збереження.
     * @param config завантажена конфігурація застосунку.
     */
    public MapEditorCoordinator(
            NeuronMapApplicationService application,
            AppConfig config
    ) {
        this(
                application,
                config,
                new LocalizationService(java.util.Locale.forLanguageTag("uk")),
                () -> { }
        );
    }

    /**
     * Створює екземпляр MapEditorCoordinator та зберігає передані залежності, потрібні для його роботи.
     *
     * @param application прикладна служба, яка надає доступ до служб карти й збереження.
     * @param config завантажена конфігурація застосунку.
     * @param localization служба локалізації інтерфейсу.
     * @param exitApplication callback завершення роботи застосунку.
     */
    public MapEditorCoordinator(
            NeuronMapApplicationService application,
            AppConfig config,
            LocalizationService localization,
            Runnable exitApplication
    ) {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(config, "config");

        this.localization = Objects.requireNonNull(localization, "localization");
        this.exitApplication = Objects.requireNonNull(exitApplication, "exitApplication");
        this.mapService = application.map();
        this.neuronService = application.neurons();
        this.connectionService = application.connections();
        this.groupService = application.groups();
        Path storageDirectory = ProjectStorageDirectoryResolver.resolve();
        GlobalSettingsStore globalSettings = new GlobalSettingsStore(
                storageDirectory.resolve("neuronmap-global.properties")
        );
        this.projectCatalog = new ProjectCatalogService(
                storageDirectory,
                globalSettings
        );
        this.currentProject = projectCatalog.descriptorForDatabasePath(
                mapService.databasePath()
        );

        this.historyService = new HistoryService(neuronService.model());
        this.minSimulationTickMillis = config.simulation().minTickMillis();
        this.maxSimulationTickMillis = config.simulation().maxTickMillis();
        this.defaultSimulationTickMillis = config.simulation().defaultTickMillis();
        this.simulationTickMillis = resolveInitialSimulationTickMillis(
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

        statusMessagePresenter = new StatusMessagePresenter(view::setStatus);

        layerOrderController = new NeuronLayerOrderController(
                view.workspace(),
                neuronViews,
                connectionViews,
                () -> neuronService.model().connections()
        );

        menuCustomizer = new NeuronMenuCustomizer(
                view.toolbar().node(),
                state::selectedNeuronForMenu,
                this::toggleNeuronDirection,
                localization
        );
        menuCustomizer.removeDeleteConnectionModeExitButton();

        mapService.load();
        if (neuronService.model().isEmpty()
                && !currentProject.isPersisted()) {
            createDemoMap();
            saveNow();
        }
        historyService.initialize();
        historyInitialized = true;

        SimulationStepPresenter stepPresenter = new SimulationStepPresenter(
                neuronService,
                view.workspace(),
                neuronViews,
                this::refreshNeuronVisuals
        );
        simulationController = new SimulationController(
                neuronService,
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
                menuCustomizer,
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

        mainMenuController = new MainMenuController(
                view.mainMenu(),
                localization,
                projectCatalog::listProjects,
                this::createNewProject,
                this::openProject,
                this::renameProject,
                this::deleteProject,
                exitApplication,
                this::pauseForMenu,
                this::resumeAfterMenu
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
     * Створює сцену редактора та налаштовує її основні компоненти.
     *
     * @param width ширина видимої області.
     * @param height висота видимої області.
     */
    public Scene createScene(double width, double height) {
        scene = view.createScene(width, height);

        scene.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.Z) {
                if (historyCoordinator.undo()) {
                    event.consume();
                }
                return;
            }
            if (event.isControlDown() && event.getCode() == KeyCode.Y) {
                if (historyCoordinator.redo()) {
                    event.consume();
                }
                return;
            }
            if (event.getCode() == KeyCode.DELETE) {
                if (neuronController.deleteSelectedNeurons()) {
                    event.consume();
                }
                return;
            }
            if (event.getCode() == KeyCode.ESCAPE) {
                interaction.cancelInteractions();
                event.consume();
            }
        });

        scene.addEventFilter(
                MouseEvent.MOUSE_PRESSED,
                presentation::bringClickedNeuronToFront
        );
        mainMenuController.install(scene);
        statusMessagePresenter.attach(scene);
        tooltipManager = ImmediateTooltipManager.install(scene);
        return scene;
    }

    /**
     * Обчислює початкове зміщення камери, щоб початкова область карти була видимою в центрі полотна.
     */
    public void centerInitialView() {
        cameraController.apply();
    }

    /**
     * Зупиняє симуляцію, закриває сховище та звільняє ресурси редактора.
     */
    public void shutdown() {
        projectDirectoryWatcher.close();
        mainMenuController.dispose(scene);
        simulationController.shutdown();
        connectionController.clearDeleteHighlights();
        interaction.dispose();
        neuronController.dispose();
        cameraController.cancelPendingSave();
        statusMessagePresenter.dispose();
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
     * Розбирає значення зі списку швидкостей і передає його контролеру симуляції.
     *
     * @param text текст, який потрібно показати або розібрати.
     */
    private void changeSimulationSpeed(String text) {
        try {
            double millis = SimulationSpeed.parseMillis(
                    text,
                    minSimulationTickMillis,
                    maxSimulationTickMillis
            );
            simulationController.setTickDurationMillis(millis);
            simulationTickMillis = millis;
            mapService.saveSimulationTickMillis(millis);
            if (mapService.isPersistent()) {
                currentProject = projectCatalog.descriptorForDatabasePath(
                        mapService.databasePath()
                );
                projectCatalog.markLastOpened(currentProject);
            }
            view.toolbar().setSimulationSpeedMillis(millis);
        } catch (RuntimeException exception) {
            updateStatus(localization.text("status.speed_invalid"));
        }
    }

    /**
     * Призупиняє симуляцію на час відкритого меню.
     */
    private void pauseForMenu() {
        modalSimulationWasRunning = simulationController.pauseForModal();
        connectionController.pauseAnimations();
    }

    /**
     * Відновлює симуляцію після закриття меню, якщо вона була активною раніше.
     */
    private void resumeAfterMenu() {
        connectionController.resumeAnimations();
        if (modalSimulationWasRunning) {
            modalSimulationWasRunning = false;
            simulationController.resumeFromModal();
        }
    }

    /**
     * Створює новий тимчасовий проєкт і активує його в редакторі.
     */
    private void createNewProject() {
        prepareForProjectSwitch();
        ProjectDescriptor project = projectCatalog.createTransientProject(
                localization.text("project.default_name")
        );
        TransientProjectRepository repository = new TransientProjectRepository(
                project.databasePath()
        );
        activateProject(project, repository, false);
    }

    /**
     * Відкриває вказаний проєкт після підготовки поточного проєкту до перемикання.
     *
     * @param project опис проєкту, над яким виконується дія.
     */
    private void openProject(ProjectDescriptor project) {
        if (project == null) {
            return;
        }
        if (currentProject != null
                && currentProject.id().equals(project.id())) {
            return;
        }

        prepareForProjectSwitch();
        SqliteMapRepository repository = new SqliteMapRepository(
                project.databasePath()
        );
        try {
            activateProject(project, repository, true);
        } catch (RuntimeException exception) {
            repository.close();
            throw exception;
        }
    }

    /**
     * Перейменовує проєкт через каталог і повертає його оновлений опис.
     *
     * @param project опис проєкту, над яким виконується дія.
     * @param name назва, яку потрібно перевірити або зберегти.
     */
    private ProjectDescriptor renameProject(
            ProjectDescriptor project,
            String name
    ) {
        if (project == null || name == null || name.isBlank()) {
            return project;
        }

        boolean active = currentProject != null
                && currentProject.databasePath().equals(project.databasePath());
        if (!active) {
            return projectCatalog.rename(project, name);
        }

        saveNow();
        mapService.close();

        try {
            ProjectDescriptor renamed = projectCatalog.rename(project, name);
            mapService.switchRepository(
                    new SqliteMapRepository(renamed.databasePath())
            );
            currentProject = renamed;
            projectCatalog.markLastOpened(renamed);
            return renamed;
        } catch (RuntimeException exception) {
            try {
                mapService.switchRepository(
                        new SqliteMapRepository(project.databasePath())
                );
                currentProject = projectCatalog.descriptorForDatabasePath(
                        project.databasePath()
                );
            } catch (RuntimeException ignored) {

            }
            throw exception;
        }
    }

    /**
     * Видаляє проєкт і оновлює активний стан редактора, якщо це потрібно.
     *
     * @param project опис проєкту, над яким виконується дія.
     */
    private void deleteProject(ProjectDescriptor project) {
        if (project == null) {
            return;
        }

        boolean active = currentProject != null
                && currentProject.id().equals(project.id());

        if (!active) {
            projectCatalog.delete(project);
            return;
        }

        prepareForProjectSwitch();
        ProjectDescriptor replacement = projectCatalog.createTransientProject(
                localization.text("project.default_name")
        );
        TransientProjectRepository repository = new TransientProjectRepository(
                replacement.databasePath()
        );
        activateProject(replacement, repository, false);
        projectCatalog.delete(project);

        List<ProjectDescriptor> remaining = projectCatalog.listProjects();
        if (!remaining.isEmpty()) {
            openProject(remaining.get(0));
        }
    }

    /**
     * Зупиняє тимчасові взаємодії й готує поточний проєкт до закриття або заміни.
     */
    private void prepareForProjectSwitch() {
        simulationController.stop();
        connectionController.clearDeleteHighlights();
        interaction.cancelInteractions();
        neuronController.hideMenu();
        cameraController.cancelPendingSave();
        saveNow();

        state.clearSelection();
        state.resetToIdle();
        modalSimulationWasRunning = false;
    }

    /**
     * Підключає репозиторій вибраного проєкту та за потреби завантажує його дані.
     *
     * @param project опис проєкту, над яким виконується дія.
     * @param repository сховище, через яке читають і зберігають карту.
     * @param loadExisting ознака, чи потрібно завантажити наявний вміст проєкту.
     */
    private void activateProject(
            ProjectDescriptor project,
            MapRepository repository,
            boolean loadExisting
    ) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(repository, "repository");

        repository.loadInto(neuronService.model());

        CameraState camera = loadExisting
                ? repository.loadCameraState()
                : CameraState.defaultState();
        double fallbackSpeed = loadExisting
                ? simulationTickMillis
                : defaultSimulationTickMillis;
        double speed = resolveInitialSimulationTickMillis(
                loadExisting
                        ? repository.loadSimulationTickMillis(fallbackSpeed)
                        : fallbackSpeed,
                fallbackSpeed,
                minSimulationTickMillis,
                maxSimulationTickMillis
        );

        mapService.switchRepository(repository);
        currentProject = project;
        simulationTickMillis = speed;
        simulationController.setTickDurationMillis(speed);
        view.toolbar().setSimulationSpeedMillis(speed);

        state.setZoom(camera.zoom());
        state.setPanX(camera.panX());
        state.setPanY(camera.panY());
        state.clearSelection();
        state.resetToIdle();

        historyService.clear();
        historyService.initialize();
        historyInitialized = true;

        presentation.synchronizeViewsWithModel();
        cameraController.apply();
        projectCatalog.markLastOpened(project.isPersisted() ? project : null);
        modalSimulationWasRunning = false;
        view.mainMenu().showMainPage();
    }

/**
 * Створює демонстраційну карту з прикладом нейронів і зв’язків.
 */
private void createDemoMap() {
        Neuron first = neuronService.create(NeuronType.EXCITATORY, 250, 220);
        Neuron second = neuronService.create(NeuronType.INHIBITORY, 520, 340);
        Neuron third = neuronService.create(NeuronType.EXCITATORY, 790, 210);
        connectionService.create(first.id(), second.id());
        connectionService.create(second.id(), third.id());
        updateStatus(localization.text("status.demo_created"));
    }

    /**
     * Починає операцію add neuron mode та готує стан взаємодії.
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
     * Завершує режим видалення зв’язків і повертає редактор до звичайної взаємодії.
     */
    private void exitDeleteConnectionMode() {
        interaction.exitDeleteConnectionMode();
    }

    /**
     * Перемикає напрямок указаного нейрона й оновлює його подання.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    private void toggleNeuronDirection(String neuronId) {
        neuronService.toggleDirection(neuronId);
        refreshMapPresentation();
        saveNow();
        updateStatus(localization.text("status.direction_changed"));
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
     * Негайно зберігає поточну карту й пов’язані налаштування.
     */
    private void saveNow() {
        mapService.save(state);

        if (mapService.isPersistent()) {
            currentProject = projectCatalog.descriptorForDatabasePath(
                    mapService.databasePath()
            );
            projectCatalog.markLastOpened(currentProject);
        }

        if (historyInitialized) {
            historyService.commitSavedState();
        }
    }

    /**
     * Передає текст повідомлення до рядка стану інтерфейсу.
     *
     * @param text текст, який потрібно показати або розібрати.
     */
    private void updateStatus(String text) {
        statusMessagePresenter.show(text);
    }

    /**
     * Вибирає початкову тривалість такту: збережене значення, якщо воно коректне, або резервне значення з конфігурації.
     *
     * @param persistedMillis збережена тривалість такту симуляції, якщо її знайдено.
     * @param fallbackMillis резервна тривалість такту, якщо збереженого значення немає.
     * @param minMillis мінімальна допустима тривалість такту в мілісекундах.
     * @param maxMillis максимальна допустима тривалість такту в мілісекундах.
     */
    private static double resolveInitialSimulationTickMillis(
            double persistedMillis,
            double fallbackMillis,
            double minMillis,
            double maxMillis
    ) {
        try {
            return SimulationSpeed.requireMillis(persistedMillis, minMillis, maxMillis);
        } catch (RuntimeException exception) {
            return fallbackMillis;
        }
    }
}
