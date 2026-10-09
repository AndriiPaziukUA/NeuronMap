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
 * Синхронізує візуальні подання нейронів, зв’язків, груп і накладок із поточним станом моделі карти.
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
     * Створює екземпляр MapPresentationCoordinator та зберігає передані залежності, потрібні для його роботи.
     *
     * @param neuronService служба операцій над нейронами.
     * @param workspace полотно редактора.
     * @param neuronViews мапа візуальних подань нейронів за їхніми ідентифікаторами.
     * @param connectionViews мапа візуальних подань зв’язків за ідентифікаторами.
     * @param rotationHandles мапа ручок обертання нейронів за ідентифікаторами.
     * @param neuronController значення «neuron controller», яке використовується в цьому методі.
     * @param connectionController значення «connection controller», яке використовується в цьому методі.
     * @param layerOrderController контролер, який керує порядком шарів елементів полотна.
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
     * Оновлює всі візуальні подання карти та розташування накладок.
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
     * Перераховує накладки, положення яких залежить від екранних координат.
     */
    public void refreshScreenSpaceOverlays() {
        neuronController.refreshOverlayPositions();
        bringRotationHandlesToFront();
        connectionController.refreshPreviewAfterCameraChange();
    }

    /**
     * Оновлює всі подання нейронів за поточними даними моделі.
     */
    public void refreshNeurons() {
        neuronController.refreshVisuals();
    }

    /**
     * Переміщує клацнутий нейрон вище в порядку шарів, якщо цього вимагає взаємодія.
     *
     * @param event подія інтерфейсу, яку потрібно обробити.
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
     * Переміщує ручки обертання на передній план, щоб вони залишалися доступними для миші.
     */
    public void bringRotationHandlesToFront() {
        for (RotationHandleView handle : rotationHandles.values()) {
            if (handle != null) {
                handle.setViewOrder(ROTATION_HANDLE_VIEW_ORDER);
            }
        }
    }

    /**
     * Оновлює геометрію та відображення всіх зв’язків.
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
     * Створює видиме подання для напрямленого зв’язку.
     *
     * @param connection напрямлений зв’язок між нейронами.
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
     * Додає відсутні й прибирає застарілі подання так, щоб інтерфейс відповідав моделі.
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
     * Оновлює візуальні області груп за положеннями нейронів-учасників.
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
     * Видаляє from parent з поточної моделі або подання.
     *
     * @param node вузол JavaFX, який потрібно перевірити або змінити.
     */
    private static void removeFromParent(Node node) {
        Node parent = node.getParent();
        if (parent instanceof javafx.scene.layout.Pane pane) {
            pane.getChildren().remove(node);
        }
    }
}
