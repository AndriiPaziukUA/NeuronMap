package com.example.neuronmap.coordinator;

import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.NeuronLayerOrderController;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.RotationHandleView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;

import java.util.Map;

/** Coordinates rebuilding and refreshing the JavaFX representation of the map. */
public final class MapPresentationCoordinator {

    private static final double ROTATION_HANDLE_VIEW_ORDER = -10_000.0;

    private final NeuronService neuronService;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Map<String, ConnectionView> connectionViews;
    private final Map<String, RotationHandleView> rotationHandles;
    private final NeuronInteractionController neuronController;
    private final SelectionController selectionController;
    private final ConnectionController connectionController;
    private final NeuronLayerOrderController layerOrderController;

    public MapPresentationCoordinator(
            NeuronService neuronService,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Map<String, ConnectionView> connectionViews,
            Map<String, RotationHandleView> rotationHandles,
            NeuronInteractionController neuronController,
            SelectionController selectionController,
            ConnectionController connectionController,
            NeuronLayerOrderController layerOrderController
    ) {
        this.neuronService = java.util.Objects.requireNonNull(neuronService, "neuronService");
        this.workspace = java.util.Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = java.util.Objects.requireNonNull(neuronViews, "neuronViews");
        this.connectionViews = java.util.Objects.requireNonNull(connectionViews, "connectionViews");
        this.rotationHandles = java.util.Objects.requireNonNull(rotationHandles, "rotationHandles");
        this.neuronController = java.util.Objects.requireNonNull(neuronController, "neuronController");
        this.selectionController = java.util.Objects.requireNonNull(selectionController, "selectionController");
        this.connectionController = java.util.Objects.requireNonNull(connectionController, "connectionController");
        this.layerOrderController = java.util.Objects.requireNonNull(layerOrderController, "layerOrderController");
    }

    public void refreshAll() {
        refreshNeurons();
        refreshConnections();
        selectionController.refreshGroups();
        connectionController.refreshDeleteHighlights();
        neuronController.refreshOverlayPositions();
        bringRotationHandlesToFront();
    }

    public void refreshScreenSpaceOverlays() {
        neuronController.refreshOverlayPositions();
        bringRotationHandlesToFront();
        connectionController.refreshPreviewAfterCameraChange();
    }

    public void refreshNeurons() {
        neuronController.refreshVisuals();
    }

    public void bringClickedNeuronToFront(MouseEvent event) {
        Node node = event.getTarget() instanceof Node targetNode
                ? targetNode
                : null;

        while (node != null) {
            if (node instanceof NeuronView neuronView) {
                layerOrderController.bringNeuronToFront(neuronView.model().id());
                return;
            }
            node = node.getParent();
        }
    }

    public void bringRotationHandlesToFront() {
        for (RotationHandleView handle : rotationHandles.values()) {
            if (handle != null) {
                handle.setViewOrder(ROTATION_HANDLE_VIEW_ORDER);
            }
        }
    }

    public void refreshConnections() {
        for (Connection connection : neuronService.model().connections()) {
            ConnectionView connectionView = connectionViews.computeIfAbsent(
                    connection.id(),
                    id -> createConnectionView(connection)
            );
            connectionView.updateGeometry();
        }

        connectionViews.entrySet().removeIf(entry -> {
            boolean exists = neuronService.model().connections().stream()
                    .anyMatch(connection -> connection.id().equals(entry.getKey()));

            if (!exists) {
                ConnectionView connectionView = entry.getValue();
                if (connectionView != null) {
                    connectionView.stopDeleteHighlight();
                }
                workspace.edgeLayer().getChildren().remove(connectionView);
            }

            return !exists;
        });
    }

    public ConnectionView createConnectionView(Connection connection) {
        ConnectionView connectionView = new ConnectionView(
                connection,
                neuronViews::get,
                neuronService.model()::neuron
        );
        workspace.edgeLayer().getChildren().add(connectionView);
        return connectionView;
    }

    public void synchronizeViewsWithModel() {
        for (NeuronView neuronView : neuronViews.values()) {
            removeFromParent(neuronView);
        }
        neuronViews.clear();

        for (RotationHandleView handle : rotationHandles.values()) {
            if (handle != null) {
                handle.dispose();
                workspace.overlayLayer().getChildren().remove(handle);
            }
        }
        rotationHandles.clear();

        for (ConnectionView connectionView : connectionViews.values()) {
            if (connectionView != null) {
                connectionView.stopDeleteHighlight();
            }
        }
        connectionViews.clear();
        workspace.edgeLayer().getChildren().clear();

        neuronController.loadViews();
        refreshAll();
    }

    private static void removeFromParent(Node node) {
        Node parent = node.getParent();
        if (parent instanceof javafx.scene.layout.Pane pane) {
            pane.getChildren().remove(node);
        }
    }
}
