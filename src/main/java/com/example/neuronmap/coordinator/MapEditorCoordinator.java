package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.controller.CameraController;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.NeuronLayerOrderController;
import com.example.neuronmap.controller.NeuronMenuCustomizer;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.controller.StatusMessagePresenter;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.CameraState;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationSpeed;
import com.example.neuronmap.view.ImmediateTooltipManager;
import com.example.neuronmap.view.MainView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.RotationHandleView;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Composition coordinator for the map editor; feature work lives in services/controllers. */
public final class MapEditorCoordinator {

    private final MapService mapService;
    private final NeuronService neuronService;
    private final ConnectionService connectionService;
    private final GroupService groupService;
    private final HistoryService historyService;
    private final EditorState state;
    private final MainView view;
    private final StatusMessagePresenter statusMessagePresenter;
    private final double minSimulationTickMillis;
    private final double maxSimulationTickMillis;
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
    private ImmediateTooltipManager tooltipManager;
    private boolean historyInitialized;

    public MapEditorCoordinator(
            NeuronMapApplicationService application,
            AppConfig config
    ) {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(config, "config");

        this.mapService = application.map();
        this.neuronService = application.neurons();
        this.connectionService = application.connections();
        this.groupService = application.groups();
        this.historyService = new HistoryService(neuronService.model());
        this.minSimulationTickMillis = config.simulation().minTickMillis();
        this.maxSimulationTickMillis = config.simulation().maxTickMillis();
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
                simulationTickMillis
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
                this::toggleNeuronDirection
        );
        menuCustomizer.removeDeleteConnectionModeExitButton();

        loadMap();
        historyService.initialize();
        historyInitialized = true;

        simulationController = new SimulationController(
                neuronService,
                new com.example.neuronmap.simulation.SimulationService(),
                view.workspace(),
                neuronViews,
                this::refreshNeuronVisuals,
                this::saveNow,
                this::updateStatus,
                view.toolbar()::setSimulationPaused,
                view.toolbar()::setSimulationControlsVisible,
                simulationTickMillis,
                config.simulation().minTickMillis(),
                config.simulation().maxTickMillis()
        );

        connectionController = new ConnectionController(
                connectionService,
                state,
                view.workspace(),
                neuronViews,
                this::refreshMapPresentation,
                this::saveNow,
                this::updateStatus
        );

        selectionController = new SelectionController(
                application,
                state,
                view.workspace(),
                neuronViews,
                this::refreshNeuronVisuals,
                this::saveNow,
                this::updateStatus
        );

        neuronController = new NeuronInteractionController(
                application,
                state,
                groupService,
                view.workspace(),
                neuronViews,
                rotationHandles,
                connectionController,
                simulationController,
                this::saveNow,
                this::refreshMapPresentation,
                this::updateStatus,
                menuCustomizer
        );

        presentation = new MapPresentationCoordinator(
                neuronService,
                view.workspace(),
                neuronViews,
                connectionViews,
                rotationHandles,
                neuronController,
                selectionController,
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
                neuronViews,
                this::saveNow,
                this::updateStatus
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
                this::updateStatus
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
                config.camera().zoomFactor()
        );

        neuronController.loadViews();
        presentation.refreshAll();
        interaction.install();
        cameraController.install();
        connectionController.install();
    }

    public Scene createScene(double width, double height) {
        Scene scene = view.createScene(width, height);

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

        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, presentation::bringClickedNeuronToFront);
        statusMessagePresenter.attach(scene);
        tooltipManager = ImmediateTooltipManager.install(scene);
        return scene;
    }

    public void centerInitialView() {
        cameraController.apply();
    }

    public void shutdown() {
        simulationController.shutdown();
        interaction.dispose();
        neuronController.dispose();
        statusMessagePresenter.dispose();
        if (tooltipManager != null) {
            tooltipManager.dispose();
            tooltipManager = null;
        }
        saveNow();
        mapService.saveSimulationTickMillis(simulationTickMillis);
        mapService.close();
    }

    private void toggleSimulationPause() {
        simulationController.pauseOrResume();
        view.toolbar().setSimulationPaused(simulationController.isPaused());
    }

    private void stopSimulationSignals() {
        simulationController.stopSignals();
        view.toolbar().setSimulationPaused(false);
    }

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
            view.toolbar().setSimulationSpeedMillis(millis);
        } catch (RuntimeException exception) {
            updateStatus("Некоректна швидкість. Введи додатне число мілісекунд.");
        }
    }

    private void loadMap() {
        mapService.load();
        if (neuronService.model().isEmpty()) {
            createDemoMap();
            saveNow();
        }
    }

    private void createDemoMap() {
        Neuron first = neuronService.create(NeuronType.EXCITATORY, 250, 220);
        Neuron second = neuronService.create(NeuronType.INHIBITORY, 520, 340);
        Neuron third = neuronService.create(NeuronType.EXCITATORY, 790, 210);
        connectionService.create(first.id(), second.id());
        connectionService.create(second.id(), third.id());
        updateStatus("Створено початкову демо-схему.");
    }

    private void beginAddNeuronMode(NeuronType type) {
        interaction.beginAddNeuronMode(type);
    }

    private void groupSelection() {
        interaction.groupSelection();
    }

    private void ungroupSelection() {
        interaction.ungroupSelection();
    }

    private void exitDeleteConnectionMode() {
        interaction.exitDeleteConnectionMode();
    }

    private void toggleNeuronDirection(String neuronId) {
        neuronService.toggleDirection(neuronId);
        refreshMapPresentation();
        saveNow();
        updateStatus("Напрямок нейрона змінено.");
    }

    private void refreshMapPresentation() {
        presentation.refreshAll();
    }

    private void refreshNeuronVisuals() {
        presentation.refreshNeurons();
    }

    private boolean hasHistoryState() {
        return historyInitialized;
    }

    private void saveNow() {
        mapService.save(state);
        if (hasHistoryState()) {
            historyService.commitSavedState();
        }
    }

    private void updateStatus(String text) {
        statusMessagePresenter.show(text);
    }

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
