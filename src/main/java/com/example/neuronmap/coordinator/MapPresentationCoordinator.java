package com.example.neuronmap.coordinator;

import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.NeuronLayerOrderController;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.GroupView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.RotationHandleView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;

import java.util.Map;

/**
 * Координує оновлення графічного представлення карти після змін даних.
 */
public final class MapPresentationCoordinator {

    private static final double ROTATION_HANDLE_VIEW_ORDER = -10_000.0;

    private final NeuronService neuronService;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Map<String, ConnectionView> connectionViews;
    private final Map<String, RotationHandleView> rotationHandles;
    private final NeuronInteractionController neuronController;
    private final ConnectionController connectionController;
    private final NeuronLayerOrderController layerOrderController;

    /**
     * Повертає результат операції «карта представлення».
     *
     * @param neuronService значення, що визначає нейрон служба для цієї операції.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param connectionViews значення, що визначає звʼязок для цієї операції.
     *
     * @param rotationHandles значення, що визначає обертання обробляє для цієї операції.
     *
     * @param neuronController значення, що визначає нейрон для цієї операції.
     *
     * @param connectionController значення, що визначає звʼязок для цієї операції.
     *
     * @param layerOrderController значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public MapPresentationCoordinator(
            NeuronService neuronService,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Map<String, ConnectionView> connectionViews,
            Map<String, RotationHandleView> rotationHandles,
            NeuronInteractionController neuronController,
            ConnectionController connectionController,
            NeuronLayerOrderController layerOrderController
    ) {
        this.neuronService = java.util.Objects.requireNonNull(neuronService, "neuronService");
        this.workspace = java.util.Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = java.util.Objects.requireNonNull(neuronViews, "neuronViews");
        this.connectionViews = java.util.Objects.requireNonNull(connectionViews, "connectionViews");
        this.rotationHandles = java.util.Objects.requireNonNull(rotationHandles, "rotationHandles");
        this.neuronController = java.util.Objects.requireNonNull(neuronController, "neuronController");
        this.connectionController = java.util.Objects.requireNonNull(connectionController, "connectionController");
        this.layerOrderController = java.util.Objects.requireNonNull(layerOrderController, "layerOrderController");
    }

    /**
     * Обробляє «усі».
     */
    public void refreshAll() {
        refreshNeurons();
        refreshConnections();
        refreshGroups();
        connectionController.refreshDeleteHighlights();
        neuronController.refreshOverlayPositions();
        bringRotationHandlesToFront();
    }

    /**
     * Обробляє «накладки».
     */
    public void refreshScreenSpaceOverlays() {
        neuronController.refreshOverlayPositions();
        bringRotationHandlesToFront();
        connectionController.refreshPreviewAfterCameraChange();
    }

    /**
     * Обробляє «нейрони».
     */
    public void refreshNeurons() {
        neuronController.refreshVisuals();
    }

    /**
     * Виконує операцію «нейрон до».
     *
     * @param event подія інтерфейсу.
     */
    public void bringClickedNeuronToFront(MouseEvent event) {
        Node node = event.getTarget() instanceof Node targetNode ? targetNode : null;
        while (node != null) {
            if (node instanceof NeuronView neuronView) {
                layerOrderController.bringNeuronToFront(neuronView.model().id());
                return;
            }
            node = node.getParent();
        }
    }

    /**
     * Виконує операцію «обертання обробляє до».
     */
    public void bringRotationHandlesToFront() {
        for (RotationHandleView handle : rotationHandles.values()) {
            if (handle != null) {
                handle.setViewOrder(ROTATION_HANDLE_VIEW_ORDER);
            }
        }
    }

    /**
     * Обробляє «звʼязки».
     */
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

    /**
     * Створює обʼєкт із переданих даних «звʼязок відображення».
     *
     * @param connection звʼязок між нейронами.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public ConnectionView createConnectionView(Connection connection) {
        ConnectionView connectionView = new ConnectionView(
                connection,
                neuronViews::get,
                neuronService.model()::neuron
        );
        workspace.edgeLayer().getChildren().add(connectionView);
        return connectionView;
    }

    /**
     * Виконує операцію «із модель».
     */
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

    /**
     * Обробляє «групи».
     */
    private void refreshGroups() {
        workspace.groupLayer().getChildren().clear();
        neuronService.model().groups().forEach(group ->
                workspace.groupLayer().getChildren().add(
                        new GroupView(group, neuronViews)
                )
        );
    }

    /**
     * Видаляє або скидає дані, повʼязані з «із».
     *
     * @param node графічний вузол JavaFX.
     */
    private static void removeFromParent(Node node) {
        Node parent = node.getParent();
        if (parent instanceof javafx.scene.layout.Pane pane) {
            pane.getChildren().remove(node);
        }
    }
}
