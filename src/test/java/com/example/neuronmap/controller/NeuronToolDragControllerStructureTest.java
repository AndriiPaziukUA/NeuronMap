package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.coordinator.MapInteractionCoordinator;
import com.example.neuronmap.coordinator.MapPresentationCoordinator;
import com.example.neuronmap.coordinator.NeuronSettingsOverlayCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronStructure;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.NeuronLayerOrderManager;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronDragPreviewView;
import com.example.neuronmap.view.neuron.NeuronStructureDrawerView;
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.event.EventType;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє інструмент структури, її шухлядку та прив'язку дочірніх нейронів до графічних координат. */
final class NeuronToolDragControllerStructureTest {

    /** Перевіряє, що структурний інструмент активується та показує прев'ю під курсором. */
    @Test
    void structureToolActivatesHighlightsAndShowsPreview() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            EditorState state = new EditorState(1.0, 0.0, 0.0);
            WorkspaceView workspace = new WorkspaceView();
            Scene scene = new Scene(workspace.node(), 600.0, 400.0);
            assertSame(scene, workspace.node().getScene(), "Робоча область має бути прикріплена до тестової сцени");
            Button structureButton = new Button("Структура");
            structureButton.getStyleClass().add("tool-button");
            NeuronToolDragController controller = new NeuronToolDragController(
                    state, workspace, (_, _) -> { });

            controller.configureToolbarButtons(Map.of(NeuronType.STRUCTURE, structureButton));
            controller.install();
            controller.setPlacementMode(NeuronType.STRUCTURE);

            assertTrue(structureButton.getStyleClass().contains("tool-button-active"),
                    "Активна кнопка структури має отримувати стандартний клас підсвічування");

            workspace.node().fireEvent(new MouseEvent(
                    MouseEvent.MOUSE_MOVED, 180.0, 140.0, 180.0, 140.0, MouseButton.NONE, 0,
                    false, false, false, false, false, false, false, false, false, false, null));
            assertEquals(1, workspace.overlayLayer().getChildren().size(),
                    "Під час переміщення вказівника має з'являтися один вузол прев'ю");
            assertInstanceOf(NeuronDragPreviewView.class, workspace.overlayLayer().getChildren().getFirst());

            controller.dispose();
            workspace.dispose();
        });
    }

    /** Перевіряє ширину, порядок, щільне розташування та перемикання шухлядки подвійним кліком API подання. */
    @Test
    void structureDrawerKeepsMembersPinnedInInsertionOrder() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 40.0, 50.0);
            Neuron first = model.createNeuron(NeuronType.EXCITATORY, 10.0, 10.0);
            Neuron second = model.createNeuron(NeuronType.INHIBITORY, 20.0, 20.0);
            assertTrue(model.addStructureMember(structure.id(), first.id()));
            assertTrue(model.addStructureMember(structure.id(), second.id()));
            NeuronView structureView = new NeuronView(model.presentation(structure.id()));
            NeuronView firstView = new NeuronView(model.presentation(first.id()));
            NeuronView secondView = new NeuronView(model.presentation(second.id()));

            structureView.setStructureMembers(List.of(firstView, secondView));
            NeuronStructureDrawerView drawer = structureView.getChildren().stream()
                    .filter(NeuronStructureDrawerView.class::isInstance)
                    .map(NeuronStructureDrawerView.class::cast)
                    .findFirst().orElseThrow();
            assertTrue(structureView.getChildren().contains(drawer));

            assertFalse(drawer.isExpanded());
            assertFalse(drawer.isVisible());
            assertEquals(NeuronView.WIDTH, drawer.getPrefWidth(), 0.001);
            assertEquals(NeuronView.WIDTH, drawer.getWidth(), 0.001);
            assertEquals(NeuronView.HEIGHT * 2.0, drawer.getHeight(), 0.001);
            assertEquals(NeuronView.HEIGHT, drawer.getLayoutY(), 0.001);
            assertNotNull(drawer.getBackground(), "Прямокутна шухлядка має власне видиме тло");
            assertNotNull(drawer.getBorder(), "Шухлядка має окремий контур");
            assertEquals(List.of(firstView, secondView), drawer.memberViews());
            assertSame(drawer, firstView.getParent());
            assertSame(drawer, secondView.getParent());
            assertEquals(0.0, firstView.getLayoutY(), 0.001);
            assertEquals(NeuronView.HEIGHT, secondView.getLayoutY(), 0.001);
            assertTrue(firstView.isStructureMember());
            assertTrue(secondView.isStructureMember());
            assertEquals(28.0, ((Rectangle) firstView.getChildren().getFirst()).getArcHeight(), 0.001,
                    "Внутрішній нейрон має зберігати заокруглені краї");

            firstView.beginDrag(10.0, 10.0, false, false);
            assertFalse(firstView.isDragging(), "Внутрішній нейрон не повинен починати окреме перетягування");
            assertTrue(structureView.toggleStructureDrawer());
            assertTrue(drawer.isVisible());
            assertFalse(structureView.toggleStructureDrawer());
            assertFalse(drawer.isVisible());
            firstView.setStructureMember(false);
            assertEquals(28.0, ((Rectangle) firstView.getChildren().getFirst()).getArcHeight(), 0.001,
                    "Заокруглені краї зберігаються після від'єднання від структури");

            structureView.disposeParameterTooltip();
            firstView.disposeParameterTooltip();
            secondView.disposeParameterTooltip();
        });
    }

    /** Перевіряє, що відкриття вкладеної структури зсуває наступні рядки без їх перекриття. */
    @Test
    void nestedStructureDrawerReflowsFollowingMembers() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron outer = model.createNeuron(NeuronType.STRUCTURE, 10.0, 20.0);
            Neuron nested = model.createNeuron(NeuronType.STRUCTURE, 30.0, 40.0);
            Neuron nestedMember = model.createNeuron(NeuronType.EXCITATORY, 50.0, 60.0);
            Neuron following = model.createNeuron(NeuronType.INHIBITORY, 70.0, 80.0);
            assertTrue(model.addStructureMember(outer.id(), nested.id()));
            assertTrue(model.addStructureMember(outer.id(), following.id()));
            assertTrue(model.addStructureMember(nested.id(), nestedMember.id()));

            NeuronView outerView = new NeuronView(model.presentation(outer.id()));
            NeuronView nestedView = new NeuronView(model.presentation(nested.id()));
            NeuronView nestedMemberView = new NeuronView(model.presentation(nestedMember.id()));
            NeuronView followingView = new NeuronView(model.presentation(following.id()));
            outerView.setStructureMembers(List.of(nestedView, followingView));
            nestedView.setStructureMembers(List.of(nestedMemberView));
            outerView.toggleStructureDrawer();

            assertEquals(NeuronView.HEIGHT, followingView.getLayoutY(), 0.001);
            nestedView.toggleStructureDrawer();
            assertEquals(NeuronView.HEIGHT * 2.0, followingView.getLayoutY(), 0.001);
            assertEquals(NeuronView.HEIGHT * 3.0, outerView.getChildren().stream()
                    .filter(NeuronStructureDrawerView.class::isInstance)
                    .map(NeuronStructureDrawerView.class::cast)
                    .findFirst().orElseThrow().getHeight(), 0.001);
            nestedView.toggleStructureDrawer();
            assertEquals(NeuronView.HEIGHT, followingView.getLayoutY(), 0.001);

            outerView.disposeParameterTooltip();
            nestedView.disposeParameterTooltip();
            nestedMemberView.disposeParameterTooltip();
            followingView.disposeParameterTooltip();
        });
    }

    /** Перевіряє, що повторна синхронізація шухлядок не скидає порядок кореневих нейронних шарів. */
    @Test
    void refreshingStructureDrawersPreservesRootLayerOrder() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron first = fixture.neuronService.create(NeuronType.EXCITATORY, 40.0, 50.0);
                Neuron second = fixture.neuronService.create(NeuronType.INHIBITORY, 80.0, 50.0);
                fixture.presentation.refreshAll();
                NeuronView firstView = fixture.neuronViews.get(first.id());
                NeuronView secondView = fixture.neuronViews.get(second.id());
                firstView.toFront();

                fixture.presentation.refreshAll();

                assertTrue(fixture.workspace.nodeLayer().getChildren().indexOf(firstView)
                        > fixture.workspace.nodeLayer().getChildren().indexOf(secondView));
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що зв'язок використовує фактичні координати нейрона, вкладеного в шухлядку структури. */
    @Test
    void connectionGeometryFollowsNeuronInsideStructureDrawer() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            new Scene(workspace.node(), 640.0, 480.0);
            NeuronMapModel model = new NeuronMapModel();
            Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 40.0, 50.0);
            Neuron member = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
            Neuron target = model.createNeuron(NeuronType.INHIBITORY, 350.0, 50.0);
            assertTrue(model.addStructureMember(structure.id(), member.id()));
            NeuronView structureView = new NeuronView(model.presentation(structure.id()));
            NeuronView memberView = new NeuronView(model.presentation(member.id()));
            NeuronView targetView = new NeuronView(model.presentation(target.id()));
            structureView.setStructureMembers(List.of(memberView));
            targetView.refreshVisuals(false);
            workspace.nodeLayer().getChildren().addAll(structureView, targetView);

            Connection connection = new Connection("drawer-connection", member.id(), target.id());
            ConnectionView connectionView = new ConnectionView(connection,
                    id -> Map.of(member.id(), memberView, target.id(), targetView).get(id),
                    id -> Map.of(member.id(), member, target.id(), target).get(id));
            workspace.edgeLayer().getChildren().add(connectionView);
            connectionView.updateGeometry();
            assertFalse(connectionView.isVisible(),
                    "Зв'язок до внутрішнього нейрона ховається, коли шухлядка закрита");
            assertNull(connectionView.capturePulseSnapshot(),
                    "Прихований зв'язок не повинен породжувати імпульс за застарілими координатами");
            structureView.toggleStructureDrawer();
            connectionView.updateGeometry();
            assertTrue(connectionView.isVisible());
            assertNotNull(connectionView.capturePulseSnapshot());
            Line line = (Line) connectionView.getChildren().getFirst();
            Point2D expectedStart = connectionView.sceneToLocal(memberView.outputPointInScene());

            assertEquals(expectedStart.getX(), line.getStartX(), 0.001);
            assertEquals(expectedStart.getY(), line.getStartY(), 0.001);
            assertEquals(50.0 + NeuronView.HEIGHT + NeuronView.HEIGHT / 2.0,
                    line.getStartY(), 0.001);

            connectionView.stopDeleteHighlight();
            memberView.disposeParameterTooltip();
            targetView.disposeParameterTooltip();
            structureView.disposeParameterTooltip();
            workspace.dispose();
        });
    }
    /** Перевіряє, що розміщення структури на полотні додає до її списку одного збуджувального нейрона. */
    @Test
    void placingStructureCreatesOneExcitatoryMember() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            MapInteractionCoordinator interactions = new MapInteractionCoordinator(
                    fixture.neuronService, fixture.state, fixture.workspace, fixture.selectionController,
                    fixture.connectionController, fixture.neuronController, fixture.presentation,
                    () -> { }, ignored -> { }, fixture.localization);
            try {
                interactions.addNeuronAt(NeuronType.STRUCTURE, 80.0, 60.0);
                Neuron structureNeuron = fixture.model.neurons().stream()
                        .filter(neuron -> neuron.type() == NeuronType.STRUCTURE)
                        .findFirst().orElseThrow();
                NeuronStructure structure = fixture.model.structure(structureNeuron.id());

                assertEquals(1, structure.memberIds().size());
                assertEquals(2, fixture.model.neurons().size());
                assertEquals(NeuronType.EXCITATORY,
                        fixture.model.neuron(structure.memberIds().iterator().next()).type());
            } finally {
                interactions.dispose();
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що подвійний лівий клік перемикає шухлядку, а не запускає звичайний імпульс. */
    @Test
    void doubleClickOnStructureTogglesDrawer() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron structure = fixture.neuronService.create(NeuronType.STRUCTURE, 80.0, 60.0);
                fixture.presentation.refreshAll();
                NeuronView structureView = fixture.neuronViews.get(structure.id());

                structureView.fireEvent(mouseEvent(MouseEvent.MOUSE_CLICKED, 2, 80.0, 60.0));
                assertTrue(structureView.isStructureDrawerExpanded());
                structureView.fireEvent(mouseEvent(MouseEvent.MOUSE_CLICKED, 2, 80.0, 60.0));
                assertFalse(structureView.isStructureDrawerExpanded());
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що перетягування елемента шухлядки не починає рух контейнера структури. */
    @Test
    void draggingStructureMemberDoesNotMoveStructureContainer() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron structure = fixture.neuronService.create(NeuronType.STRUCTURE, 80.0, 60.0);
                fixture.presentation.refreshAll();
                NeuronView structureView = fixture.neuronViews.get(structure.id());
                NeuronView memberView = fixture.neuronViews.values().stream()
                        .filter(NeuronView::isStructureMember)
                        .findFirst().orElseThrow();
                structureView.toggleStructureDrawer();
                double initialX = fixture.neuronService.presentation(structure.id()).orElseThrow().x();
                double initialY = fixture.neuronService.presentation(structure.id()).orElseThrow().y();

                memberView.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 1, 10.0, 10.0));
                memberView.fireEvent(mouseEvent(MouseEvent.MOUSE_DRAGGED, 1, 90.0, 80.0));
                memberView.fireEvent(mouseEvent(MouseEvent.MOUSE_RELEASED, 1, 90.0, 80.0));

                assertFalse(memberView.isDragging());
                assertFalse(structureView.isDragging());
                assertEquals(initialX, fixture.neuronService.presentation(structure.id()).orElseThrow().x(), 0.001);
                assertEquals(initialY, fixture.neuronService.presentation(structure.id()).orElseThrow().y(), 0.001);
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Створює синтетичну мишачу подію з указаними координатами та модифікаторами без системної миші. */
    private static MouseEvent mouseEvent(EventType<? extends MouseEvent> eventType, int clickCount,
                                         double x, double y) {
        boolean primaryButtonDown = eventType == MouseEvent.MOUSE_PRESSED
                || eventType == MouseEvent.MOUSE_DRAGGED;
        return new MouseEvent(eventType, x, y, x, y, MouseButton.PRIMARY, clickCount,
                false, false, false, false,
                primaryButtonDown, false, false,
                false, false, true, null);
    }

    /** Збирає справжній граф взаємодії редактора для перевірки поведінки подій JavaFX. */
    private static final class Fixture {
        private final NeuronMapModel model = new NeuronMapModel();
        private final NeuronService neuronService = new NeuronService(model);
        private final GroupService groupService = new GroupService(model);
        private final ConnectionService connectionService = new ConnectionService(model);
        private final EditorState state = new EditorState(1.0, 0.0, 0.0);
        private final WorkspaceView workspace = new WorkspaceView();
        private final Map<String, NeuronView> neuronViews = new HashMap<>();
        private final Map<String, ConnectionView> connectionViews = new HashMap<>();
        private final Map<String, RotationHandleView> rotationHandles = new HashMap<>();
        private final LocalizationService localization = new LocalizationService(Locale.ENGLISH);
        private final StackPane settingsOverlayHost = new StackPane();
        private final MainMenuView mainMenu = new MainMenuView(localization, ignored -> { });
        private final NeuronSettingsOverlayCoordinator settingsOverlayCoordinator =
                new NeuronSettingsOverlayCoordinator(settingsOverlayHost, mainMenu, localization);
        private final SelectionController selectionController = new SelectionController(
                groupService, state, () -> { }, () -> { }, ignored -> { }, localization);
        private final ConnectionController connectionController = ConnectionControllerFactory.create(
                new ConnectionControllerFactory.Dependencies(
                        connectionService, state, workspace, neuronViews, this::refreshPresentation,
                        () -> { }, ignored -> { }, localization));
        private final SimulationController simulationController = new SimulationController(
                neuronService, new SimulationService(),
                new SimulationStepPresenter(workspace, neuronViews, () -> { },
                        new PulseAnimationController(workspace)),
                () -> { }, ignored -> { }, ignored -> { }, ignored -> { },
                100.0, 10.0, 1_000.0, localization);
        private final NeuronInteractionController neuronController = NeuronInteractionControllerFactory.create(
                new NeuronInteractionControllerFactory.Dependencies(
                        neuronService, groupService, selectionController,
                        new NeuronClipboardService(neuronService, groupService), state, workspace,
                        neuronViews, rotationHandles, connectionController, simulationController,
                        () -> { }, this::refreshPresentation, ignored -> { },
                        new NeuronMenuView(new Pane(), state::selectedNeuronForMenu, ignored -> { }, localization),
                        localization, settingsOverlayCoordinator));
        private final AtomicReference<MapPresentationCoordinator> presentationReference = new AtomicReference<>();
        private final MapPresentationCoordinator presentation = initializePresentation();

        /** Створює координатор і публікує його до callback-ів синхронізації подань. */
        private MapPresentationCoordinator initializePresentation() {
            MapPresentationCoordinator created = new MapPresentationCoordinator(
                    neuronService, workspace, neuronViews, connectionViews, rotationHandles,
                    neuronController, connectionController,
                    new NeuronLayerOrderManager(neuronViews, connectionViews, model::connections));
            presentationReference.set(created);
            return created;
        }

        /** Оновлює презентацію через координатор, використаний фабриками контролерів. */
        private void refreshPresentation() {
            MapPresentationCoordinator coordinator = presentationReference.get();
            if (coordinator != null) {
                coordinator.refreshAll();
            }
        }

        /** Звільняє контролери, UI-підписки та ресурси робочої області після кожної перевірки. */
        private void dispose() {
            neuronController.dispose();
            simulationController.shutdown();
            connectionController.dispose();
            mainMenu.dispose();
            workspace.dispose();
        }
    }

}
