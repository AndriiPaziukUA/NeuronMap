package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronDragPreviewFactory;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.transform.Scale;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Owns canvas input, toolbar neuron drag/drop and transient map interaction state. */
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
    private final Map<String, NeuronView> neuronViews;

    private Node dragPreview;
    private NeuronType dragPreviewType;
    private Scale dragPreviewScale;

    public MapInteractionCoordinator(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            SelectionController selectionController,
            ConnectionController connectionController,
            NeuronInteractionController neuronController,
            SimulationController simulationController,
            MapPresentationCoordinator presentation,
            Map<String, NeuronView> neuronViews,
            Runnable save,
            Consumer<String> status
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.selectionController = Objects.requireNonNull(selectionController, "selectionController");
        this.connectionController = Objects.requireNonNull(connectionController, "connectionController");
        this.neuronController = Objects.requireNonNull(neuronController, "neuronController");
        this.simulationController = Objects.requireNonNull(simulationController, "simulationController");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.neuronViews = Objects.requireNonNull(neuronViews, "neuronViews");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
    }

    public void install() {
        workspace.node().addEventFilter(MouseEvent.MOUSE_CLICKED, this::handleCanvasClick);
        installDropHandlers();
        configureNeuronToolDragSources();
    }

    public void beginAddNeuronMode(NeuronType type) {
        simulationController.stop();
        connectionController.cancelCreate();
        connectionController.exitDelete();
        neuronController.hideMenu();

        state.enterAddNeuronMode(type);
        workspace.node().setCursor(Cursor.CROSSHAIR);
        status.accept("Клацни на вільному місці дошки, щоб створити нейрон.");
    }

    public void cancelInteractions() {
        removeDragPreview();
        connectionController.cancelCreate();
        connectionController.exitDelete();
        neuronController.hideMenu();
        state.resetToIdle();
        workspace.node().setCursor(Cursor.DEFAULT);
        status.accept("Готово.");
    }

    public void addNeuronAt(NeuronType type, double x, double y) {
        if (type == null) {
            return;
        }

        Neuron neuron = neuronService.create(type, x, y);
        neuronController.addViewForNeuron(neuron);
        presentation.refreshAll();
        save.run();
        status.accept("Додано нейрон.");
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
        status.accept("Режим видалення зв'язків вимкнено.");
    }

    public void dispose() {
        removeDragPreview();
    }

    private void handleCanvasClick(MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY) {
            if (state.mode() == EditorState.Mode.DELETE_CONNECTION) {
                connectionController.exitDelete();
                status.accept("Режим видалення зв'язків вимкнено.");
                event.consume();
            }
            return;
        }

        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        if (neuronController.isInteractiveTarget(event.getTarget())) {
            return;
        }

        if (findConnectionView(event.getTarget()) != null) {
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

    private void configureNeuronToolDragSources() {
        // Toolbar buttons are wired explicitly through configureToolbarButtons().
    }

    private void configureDragSource(Button button, NeuronType type) {
        if (button == null || type == null) {
            return;
        }

        button.setOnDragDetected(event -> {
            Dragboard dragboard = button.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString(type.name());
            dragboard.setContent(content);

            dragPreviewType = type;
            removeDragPreview();
            button.setCursor(Cursor.CLOSED_HAND);
            event.consume();
        });

        button.setOnMouseReleased(event -> button.setCursor(Cursor.HAND));
        button.addEventHandler(DragEvent.DRAG_DONE, event -> {
            removeDragPreview();
            dragPreviewType = null;
            button.setCursor(Cursor.HAND);
            event.consume();
        });
    }

    private void installDropHandlers() {
        workspace.node().addEventHandler(DragEvent.DRAG_OVER, event -> {
            NeuronType type = dragNeuronType(event.getDragboard());
            if (type == null) {
                return;
            }
            event.acceptTransferModes(TransferMode.COPY);
            updateDragPreview(type, event.getSceneX(), event.getSceneY());
            event.consume();
        });

        workspace.node().addEventHandler(DragEvent.DRAG_EXITED, event -> removeDragPreview());

        workspace.node().addEventHandler(DragEvent.DRAG_DROPPED, event -> {
            boolean success = false;
            try {
                NeuronType type = dragNeuronType(event.getDragboard());
                if (type != null) {
                    Point2D viewportPoint = workspace.node().sceneToLocal(
                            event.getSceneX(),
                            event.getSceneY()
                    );
                    Point2D worldPoint = screenToWorld(
                            viewportPoint.getX(),
                            viewportPoint.getY()
                    );
                    addNeuronAt(
                            type,
                            worldPoint.getX() - NeuronView.WIDTH / 2.0,
                            worldPoint.getY() - NeuronView.HEIGHT / 2.0
                    );
                    success = true;
                }
                event.setDropCompleted(success);
            } finally {
                removeDragPreview();
                dragPreviewType = null;
            }
            event.consume();
        });
    }

    public void configureToolbarButtons(
            Button excitatoryButton,
            Button inhibitoryButton
    ) {
        configureDragSource(excitatoryButton, NeuronType.EXCITATORY);
        configureDragSource(inhibitoryButton, NeuronType.INHIBITORY);
    }

    private void updateDragPreview(NeuronType type, double sceneX, double sceneY) {
        if (dragPreview == null || dragPreviewType != type) {
            removeDragPreview();
            dragPreview = NeuronDragPreviewFactory.create(type);
            dragPreviewType = type;
            dragPreviewScale = new Scale(
                    state.zoom(),
                    state.zoom(),
                    NeuronView.WIDTH / 2.0,
                    NeuronView.HEIGHT / 2.0
            );
            dragPreview.getTransforms().add(dragPreviewScale);
            workspace.overlayLayer().getChildren().add(dragPreview);
        }

        Point2D cursor = workspace.overlayLayer().sceneToLocal(sceneX, sceneY);
        dragPreviewScale.setX(state.zoom());
        dragPreviewScale.setY(state.zoom());
        dragPreview.relocate(
                cursor.getX() - NeuronView.WIDTH / 2.0,
                cursor.getY() - NeuronView.HEIGHT / 2.0
        );
        dragPreview.toFront();
    }

    private NeuronType dragNeuronType(Dragboard dragboard) {
        if (dragboard == null || !dragboard.hasString()) {
            return null;
        }
        try {
            return NeuronType.valueOf(dragboard.getString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void removeDragPreview() {
        if (dragPreview != null) {
            workspace.overlayLayer().getChildren().remove(dragPreview);
        }
        dragPreview = null;
        dragPreviewType = null;
        dragPreviewScale = null;
    }

    private Point2D screenToWorld(double screenX, double screenY) {
        return new Point2D(
                (screenX - state.panX()) / state.zoom(),
                (screenY - state.panY()) / state.zoom()
        );
    }

    private ConnectionView findConnectionView(Object target) {
        Node node = target instanceof Node targetNode ? targetNode : null;
        while (node != null) {
            if (node instanceof ConnectionView connectionView) {
                return connectionView;
            }
            node = node.getParent();
        }
        return null;
    }
}
