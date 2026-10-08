package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.JavaFxNodeLookup;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.NeuronToolDragController;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Coordinates canvas-level map interaction and composes the toolbar drag controller. */
public final class MapInteractionCoordinator {

    private final NeuronService neuronService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final SelectionController selectionController;
    private final ConnectionController connectionController;
    private final NeuronInteractionController neuronController;
    private final SimulationController simulationController;
    private final MapPresentationCoordinator presentation;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;
    private final NeuronToolDragController toolDragController;

    public MapInteractionCoordinator(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            SelectionController selectionController,
            ConnectionController connectionController,
            NeuronInteractionController neuronController,
            SimulationController simulationController,
            MapPresentationCoordinator presentation,
            Runnable save,
            Consumer<String> status
    ) {
        this(
                neuronService,
                state,
                workspace,
                selectionController,
                connectionController,
                neuronController,
                simulationController,
                presentation,
                save,
                status,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    public MapInteractionCoordinator(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            SelectionController selectionController,
            ConnectionController connectionController,
            NeuronInteractionController neuronController,
            SimulationController simulationController,
            MapPresentationCoordinator presentation,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.selectionController = Objects.requireNonNull(selectionController, "selectionController");
        this.connectionController = Objects.requireNonNull(connectionController, "connectionController");
        this.neuronController = Objects.requireNonNull(neuronController, "neuronController");
        this.simulationController = Objects.requireNonNull(simulationController, "simulationController");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.toolDragController = new NeuronToolDragController(
                state,
                workspace,
                this::handleToolDrop
        );
    }

    public void install() {
        workspace.node().addEventFilter(MouseEvent.MOUSE_CLICKED, this::handleCanvasClick);
        toolDragController.install();
    }

    public void beginAddNeuronMode(NeuronType type) {
        simulationController.stop();
        connectionController.cancelCreate();
        connectionController.exitDelete();
        neuronController.hideMenu();

        state.enterAddNeuronMode(type);
        workspace.node().setCursor(Cursor.CROSSHAIR);
        status.accept(localization.text("status.add_neuron_hint"));
    }

    public void cancelInteractions() {
        toolDragController.clearPreview();
        connectionController.cancelCreate();
        connectionController.exitDelete();
        neuronController.hideMenu();
        state.resetToIdle();
        workspace.node().setCursor(Cursor.DEFAULT);
        status.accept(localization.text("status.ready"));
    }

    public void addNeuronAt(NeuronType type, double x, double y) {
        if (type == null) {
            return;
        }

        Neuron neuron = neuronService.create(type, x, y);
        neuronController.addViewForNeuron(neuron);
        presentation.refreshAll();
        save.run();
        status.accept(localization.text("status.new_neuron"));
    }

    public void groupSelection() {
        selectionController.groupSelection();
        presentation.refreshAll();
    }

    public void ungroupSelection() {
        selectionController.ungroupSelection();
        presentation.refreshAll();
    }

    public void exitDeleteConnectionMode() {
        connectionController.exitDelete();
        status.accept(localization.text("status.delete_mode_exit"));
    }

    public void dispose() {
        toolDragController.clearPreview();
    }

    private void handleCanvasClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        if (neuronController.isInteractiveTarget(event.getTarget())
                || JavaFxNodeLookup.findAncestor(event.getTarget(), ConnectionView.class) != null) {
            return;
        }

        if (state.mode() == EditorState.Mode.ADD_NEURON) {
            Point2D worldPoint = screenToWorld(event.getX(), event.getY());
            addNeuronAt(
                    state.pendingNeuronType(),
                    worldPoint.getX() - NeuronView.WIDTH / 2.0,
                    worldPoint.getY() - NeuronView.HEIGHT / 2.0
            );
            state.resetToIdle();
            workspace.node().setCursor(Cursor.DEFAULT);
            event.consume();
            return;
        }

        if (state.isIdle()) {
            neuronController.hideMenu();
            selectionController.clear();
            event.consume();
        }
    }

    private void handleToolDrop(NeuronType type, Point2D worldPoint) {
        addNeuronAt(
                type,
                worldPoint.getX() - NeuronView.WIDTH / 2.0,
                worldPoint.getY() - NeuronView.HEIGHT / 2.0
        );
    }

    public void configureToolbarButtons(Button excitatoryButton, Button inhibitoryButton) {
        toolDragController.configureToolbarButtons(excitatoryButton, inhibitoryButton);
    }

    private Point2D screenToWorld(double screenX, double screenY) {
        return new Point2D(
                (screenX - state.panX()) / state.zoom(),
                (screenY - state.panY()) / state.zoom()
        );
    }
}
