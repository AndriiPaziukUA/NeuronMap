package com.example.neuronmap.coordinator;

import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.NeuronLayerOrderManager;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.group.GroupView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.support.JavaFxNodeLookup;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Єдиний власник синхронізації графічних подань карти з поточним станом моделі. */
public final class MapPresentationCoordinator {

    private static final double ROTATION_HANDLE_VIEW_ORDER = -10_000.0;

    private final NeuronService neuronService;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Map<String, ConnectionView> connectionViews;
    private final Map<String, RotationHandleView> rotationHandles;
    private final NeuronInteractionController neuronController;
    private final ConnectionController connectionController;
    private final NeuronLayerOrderManager layerOrderController;

    /** Створює координатор подань і реєструє його як власника синхронізації нейронних подань. */
    public MapPresentationCoordinator(
            NeuronService neuronService,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Map<String, ConnectionView> connectionViews,
            Map<String, RotationHandleView> rotationHandles,
            NeuronInteractionController neuronController,
            ConnectionController connectionController,
            NeuronLayerOrderManager layerOrderController
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = Objects.requireNonNull(neuronViews, "neuronViews");
        this.connectionViews = Objects.requireNonNull(connectionViews, "connectionViews");
        this.rotationHandles = Objects.requireNonNull(rotationHandles, "rotationHandles");
        this.neuronController = Objects.requireNonNull(neuronController, "neuronController");
        this.connectionController = Objects.requireNonNull(connectionController, "connectionController");
        this.layerOrderController = Objects.requireNonNull(layerOrderController, "layerOrderController");
        this.neuronController.setPresentationActions(
                this::synchronizeNeuronViews,
                this::refreshNeuronVisuals,
                this::refreshOverlayPositions,
                this::refreshRelatedGeometry);
    }

    /** Оновлює всі подання карти та відповідні накладки згідно з поточною моделлю. */
    public void refreshAll() {
        refreshNeurons();
        refreshRelatedGeometry();
        refreshOverlayPositions();
        bringRotationHandlesToFront();
    }

    /** Перераховує накладки, положення яких залежить від екранних координат. */
    public void refreshScreenSpaceOverlays() {
        refreshOverlayPositions();
        bringRotationHandlesToFront();
        connectionController.refreshPreviewAfterCameraChange();
    }

    /** Узгоджує набір подань нейронів із моделлю та оновлює їхню геометрію й видимий стан. */
    public void refreshNeurons() {
        synchronizeNeuronViews();
        refreshNeuronVisuals();
    }

    /** Переміщує клацнутий нейрон вище в порядку шарів, якщо цього вимагає взаємодія. */
    public void bringClickedNeuronToFront(MouseEvent event) {
        NeuronView neuronView = JavaFxNodeLookup.findAncestor(event.getTarget(), NeuronView.class);
        if (neuronView != null) {
            layerOrderController.bringNeuronToFront(neuronView.model().id());
        }
    }

    /** Переміщує ручки обертання на передній план, щоб вони залишалися доступними для миші. */
    public void bringRotationHandlesToFront() {
        for (RotationHandleView handle : rotationHandles.values()) {
            if (handle != null) {
                handle.setViewOrder(ROTATION_HANDLE_VIEW_ORDER);
            }
        }
    }

    /** Координує оновлення позицій контекстного меню та ручок обертання для поточних подань. */
    private void refreshOverlayPositions() {
        for (NeuronView neuronView : neuronViews.values()) {
            neuronController.refreshContextMenuPosition(neuronView);
        }
        neuronController.refreshRotationHandlePosition();
    }

    /** Оновлює вигляд кожного нейрона відповідно до моделі та поточного вибору. */
    private void refreshNeuronVisuals() {
        for (NeuronView neuronView : neuronViews.values()) {
            neuronView.refreshVisuals(neuronController.isNeuronSelected(neuronView.model().id()));
        }
    }

    /** Оновлює залежну геометрію без повторного створення нейронних подань. */
    private void refreshRelatedGeometry() {
        refreshConnections();
        refreshGroups();
        connectionController.refreshDeleteHighlights();
    }

    /**
     * Оновлює геометрію зв'язків, створює відсутні подання та вилучає неактуальні.
     * Наявне подання з тим самим ідентифікатором і кінцями зберігається, щоб його активні анімації
     * продовжували роботу без перезапуску після відновлення історії.
     */
    public void refreshConnections() {
        for (Connection connection : neuronService.model().connections()) {
            ConnectionView existingView = connectionViews.get(connection.id());
            if (existingView != null && !hasSameEndpoints(existingView.model(), connection)) {
                existingView.stopDeleteHighlight();
                workspace.edgeLayer().getChildren().remove(existingView);
                connectionViews.remove(connection.id());
            }

            ConnectionView connectionView = connectionViews.computeIfAbsent(
                    connection.id(), id -> createConnectionView(connection));
            connectionView.updateModel(connection);
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

    /** Створює видиме подання для напрямленого зв'язку та додає його до відповідного шару. */
    public ConnectionView createConnectionView(Connection connection) {
        ConnectionView connectionView = new ConnectionView(
                connection, neuronViews::get, neuronService.model()::neuron);
        workspace.edgeLayer().getChildren().add(connectionView);
        return connectionView;
    }

    /**
     * Узгоджує подання після undo/redo, зберігаючи анімації зв'язків, які залишаються в поточній карті.
     * Зв'язки, видалені відновленим станом, буде вилучено звичайним механізмом синхронізації.
     */
    public void synchronizeViewsAfterHistoryChange() {
        neuronController.hideMenu();
        synchronizeNeuronViews();
        refreshNeuronVisuals();
        refreshRelatedGeometry();
        refreshOverlayPositions();
        bringRotationHandlesToFront();
    }

    /** Повністю перебудовує подання після заміни активного проєкту або іншої повної заміни моделі. */
    public void synchronizeViewsWithModel() {
        neuronController.hideMenu();
        for (String neuronId : List.copyOf(neuronViews.keySet())) {
            removeNeuronView(neuronId);
        }
        for (ConnectionView connectionView : connectionViews.values()) {
            if (connectionView != null) {
                connectionView.stopDeleteHighlight();
            }
        }
        connectionViews.clear();
        workspace.edgeLayer().getChildren().clear();
        workspace.groupLayer().getChildren().clear();
        refreshAll();
    }

    /** Узгоджує набір графічних вузлів із моделлю, видаляючи застарілі й створюючи відсутні подання. */
    private void synchronizeNeuronViews() {
        for (String neuronId : List.copyOf(neuronViews.keySet())) {
            var presentation = neuronService.presentation(neuronId);
            NeuronView view = neuronViews.get(neuronId);
            if (presentation.isEmpty() || view == null || view.presentation() != presentation.orElseThrow()) {
                removeNeuronView(neuronId);
            }
        }
        for (Neuron neuron : neuronService.neurons()) {
            if (!neuronViews.containsKey(neuron.id())) {
                createNeuronView(neuron);
            }
        }
    }

    /** Створює нейронне подання, додає його до графічних шарів і реєструє обробники взаємодії. */
    private void createNeuronView(Neuron neuron) {
        NeuronPresentation presentation = neuronService.presentation(neuron.id())
                .orElseThrow(() -> new IllegalStateException("Missing presentation for neuron " + neuron.id()));
        NeuronView view = new NeuronView(presentation);
        neuronViews.put(neuron.id(), view);
        workspace.nodeLayer().getChildren().add(view);
        RotationHandleView handle = new RotationHandleView();
        handle.setVisible(false);
        rotationHandles.put(neuron.id(), handle);
        workspace.overlayLayer().getChildren().add(handle);
        try {
            neuronController.installInteractionHandlers(view, handle);
        } catch (RuntimeException | Error failure) {
            removeNeuronView(neuron.id());
            throw failure;
        }
    }

    /** Від'єднує обробники перед вилученням подання та ручки з мап і графічних шарів. */
    private void removeNeuronView(String neuronId) {
        NeuronView view = neuronViews.get(neuronId);
        RotationHandleView handle = rotationHandles.get(neuronId);
        neuronController.uninstallInteractionHandlers(view, handle);
        neuronViews.remove(neuronId);
        rotationHandles.remove(neuronId);
        if (view != null) {
            removeFromParent(view);
        }
        if (handle != null) {
            handle.dispose();
            removeFromParent(handle);
        }
    }

    /** Оновлює візуальні області груп за положеннями нейронів-учасників. */
    private void refreshGroups() {
        workspace.groupLayer().getChildren().clear();
        neuronService.model().groups().forEach(group ->
                workspace.groupLayer().getChildren().add(new GroupView(group, neuronViews)));
    }

    /**
     * Перевіряє ідентичність кінців зв'язку для безпечного повторного використання його подання.
     *
     * @param current зв'язок, на який наразі посилається графічне подання.
     * @param restored зв'язок із відновленої моделі.
     * @return {@code true}, якщо ідентифікатор джерела та цілі збігаються.
     */
    private static boolean hasSameEndpoints(Connection current, Connection restored) {
        return current.sourceId().equals(restored.sourceId())
                && current.targetId().equals(restored.targetId());
    }

    /** Вилучає графічний вузол із батьківського контейнера, якщо вузол до нього приєднаний. */
    private static void removeFromParent(Node node) {
        Node parent = node.getParent();
        if (parent instanceof Pane pane) {
            pane.getChildren().remove(node);
        }
    }
}
