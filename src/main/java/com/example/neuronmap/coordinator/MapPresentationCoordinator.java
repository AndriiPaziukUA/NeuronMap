package com.example.neuronmap.coordinator;

import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronStructure;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.NeuronLayerOrderManager;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.group.GroupView;
import com.example.neuronmap.view.neuron.NeuronStructureDrawerSurfaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.support.JavaFxNodeLookup;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Єдиний власник синхронізації графічних подань карти з поточним станом моделі. */
public final class MapPresentationCoordinator {

    private static final double ROTATION_HANDLE_VIEW_ORDER = -10_000.0;

    private final NeuronService neuronService;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Map<String, ConnectionView> connectionViews;
    private final Map<String, RotationHandleView> rotationHandles;
    private final Map<String, NeuronStructureDrawerSurfaceView> structureDrawerSurfaces = new HashMap<>();
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

    /**
     * Повертає ідентифікатори нейронів, для яких зараз існують графічні подання.
     *
     * @return незмінна множина ідентифікаторів зареєстрованих подань.
     */
    public Set<String> neuronIds() {
        return Set.copyOf(neuronViews.keySet());
    }

    /** Переміщує клацнутий нейрон вище в порядку шарів, якщо цього вимагає взаємодія. */
    public void bringClickedNeuronToFront(MouseEvent event) {
        NeuronView neuronView = JavaFxNodeLookup.findAncestor(event.getTarget(), NeuronView.class);
        if (neuronView != null && !neuronView.isStructureMember()) {
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

    /**
     * Координує оновлення геометрії назв, позицій контекстного меню та ручок обертання.
     * Назви оновлюються в тому самому проході, який камера викликає після zoom і pan.
     */
    private void refreshOverlayPositions() {
        for (NeuronView neuronView : neuronViews.values()) {
            neuronView.refreshHoverNameGeometry(workspace.nameLayer());
            neuronController.refreshContextMenuPosition(neuronView);
        }
        neuronController.refreshRotationHandlePosition();
    }

    /** Оновлює вигляд нейронів і синхронізує геометрію винесених поверхонь шухлядок. */
    private void refreshNeuronVisuals() {
        for (NeuronView neuronView : neuronViews.values()) {
            neuronView.refreshVisuals(neuronController.isNeuronSelected(neuronView.model().id()));
        }
        refreshStructureDrawerSurfaces();
    }

    /** Оновлює геометрію поверхонь шухлядок перед зв'язками та іншими залежними графічними елементами. */
    private void refreshRelatedGeometry() {
        refreshStructureDrawerSurfaces();
        refreshConnections();
        refreshGroups();
        connectionController.refreshDeleteHighlights();
    }

    /**
     * Оновлює або створює нижні поверхні шухлядок і вилучає поверхні структур, яких більше немає в моделі.
     * Поверхні розташовані у шарі під зв'язками; вкладені нейрони залишаються всередині відповідних подань
     * у верхньому шарі вузлів.
     */
    private void refreshStructureDrawerSurfaces() {
        List<NeuronStructure> structures = List.copyOf(neuronService.model().structures());
        Set<String> currentStructureIds = new HashSet<>();
        for (NeuronStructure structure : structures) {
            currentStructureIds.add(structure.id());
        }

        for (String structureId : List.copyOf(structureDrawerSurfaces.keySet())) {
            if (!currentStructureIds.contains(structureId)) {
                NeuronStructureDrawerSurfaceView staleSurface = structureDrawerSurfaces.remove(structureId);
                workspace.structureDrawerLayer().getChildren().remove(staleSurface);
            }
        }

        for (NeuronStructure structure : structures) {
            NeuronView structureView = neuronViews.get(structure.id());
            if (structureView == null) {
                continue;
            }
            NeuronStructureDrawerSurfaceView surface = structureDrawerSurfaces.computeIfAbsent(
                    structure.id(), _ -> {
                        NeuronStructureDrawerSurfaceView newSurface = new NeuronStructureDrawerSurfaceView();
                        workspace.structureDrawerLayer().getChildren().add(newSurface);
                        return newSurface;
                    });
            surface.refreshGeometry(structureView, workspace.structureDrawerLayer());
        }
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
                    connection.id(), _ -> createConnectionView(connection));
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

    /**
     * Синхронізує подання після зміни історії та визначає останній нейрон, що з'явився в карті.
     * Порядок перевірки відповідає порядку нейронів у моделі.
     *
     * @param previousNeuronIds ідентифікатори подань до виконання undo або redo.
     * @return ідентифікатор останнього відновленого нейрона або {@code null}, якщо нейронів не додалося.
     */
    public String synchronizeViewsAfterHistoryChange(Set<String> previousNeuronIds) {
        Objects.requireNonNull(previousNeuronIds, "previousNeuronIds");
        synchronizeViewsAfterHistoryChange();
        return neuronService.neurons().stream()
                .map(Neuron::id)
                .filter(neuronId -> !previousNeuronIds.contains(neuronId))
                .reduce((_, last) -> last)
                .orElse(null);
    }

    /**
     * Передає фокус клавіатури відновленому нейрону, якщо його подання існує.
     * Відсутній нейрон ігнорується, бо інші зміни історії можуть не додавати нейронів до карти.
     *
     * @param neuronId ідентифікатор нейрона, який потрібно сфокусувати; {@code null} ігнорується.
     */
    public void focusRestoredNeuron(String neuronId) {
        if (neuronId == null) {
            return;
        }
        NeuronView neuronView = neuronViews.get(neuronId);
        if (neuronView != null) {
            workspace.focusNode(neuronView);
        }
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

    /** Узгоджує набір подань, а потім вкладає внутрішні нейрони у шухлядки їхніх структур. */
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
        synchronizeStructureDrawers();
    }

    /**
     * Синхронізує кореневі подання та вміст шухлядок, включно зі збереженим станом відкритості.
     * Уже розміщені кореневі подання не переставляються, щоб звичайне оновлення не скидало порядок шарів.
     */
    private void synchronizeStructureDrawers() {
        boolean hasNewStructureMembers = false;
        for (NeuronView view : neuronViews.values()) {
            boolean structureMember = neuronService.model().structureContaining(view.model().id()) != null;
            hasNewStructureMembers |= structureMember && !view.isStructureMember();
            view.setStructureMember(structureMember);
            if (structureMember && view.getParent() == workspace.nodeLayer()) {
                workspace.nodeLayer().getChildren().remove(view);
            } else if (!structureMember && view.getParent() != workspace.nodeLayer()) {
                removeFromParent(view);
                workspace.nodeLayer().getChildren().add(view);
            }
        }
        if (hasNewStructureMembers) {
            neuronController.hideMenu();
        }

        for (NeuronStructure structure : neuronService.model().structures()) {
            NeuronView structureView = neuronViews.get(structure.id());
            if (structureView == null) {
                continue;
            }
            List<NeuronView> memberViews = structure.memberIds().stream()
                    .map(neuronViews::get)
                    .filter(Objects::nonNull)
                    .toList();
            structureView.setStructureMembers(memberViews);
            if (structureView.isStructureDrawerExpanded() != structure.isExpanded()) {
                structureView.toggleStructureDrawer();
            }
        }
        refreshStructureDrawerSurfaces();
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
        workspace.nameLayer().getChildren().add(view.nameOverlayNode());
        try {
            handle.addEventHandler(MouseEvent.MOUSE_ENTERED, _ -> view.setNameHoverActive(true));
            handle.addEventHandler(MouseEvent.MOUSE_EXITED, _ -> view.setNameHoverActive(false));
            handle.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    view.setNameHiddenWhileRotating(true);
                }
            });
            handle.addEventHandler(MouseEvent.MOUSE_RELEASED, _ -> view.setNameHiddenWhileRotating(false));
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
            removeFromParent(view.nameOverlayNode());
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
     * @return {@code true}, якщо ідентифікатори джерела та цілі збігаються.
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
