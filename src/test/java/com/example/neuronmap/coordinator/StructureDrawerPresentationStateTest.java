package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.ConnectionControllerFactory;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.NeuronInteractionControllerFactory;
import com.example.neuronmap.controller.PulseAnimationController;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.service.history.FieldStateSnapshot;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.NeuronLayerOrderManager;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronStructureDrawerSurfaceView;
import com.example.neuronmap.view.neuron.NeuronStructureDrawerView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.MoveTo;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє стан шухлядки, збереження відкритості та порядок її графічних шарів. */
class StructureDrawerPresentationStateTest {

    /** Перевіряє, що оновлення подань відновлює відкриту й закриту шухлядку за станом моделі. */
    @Test
    void refreshAllSynchronizesDrawerVisibilityFromModel() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron structure = fixture.model.createNeuron(NeuronType.STRUCTURE, 80.0, 60.0);
                Neuron member = fixture.model.createNeuron(NeuronType.EXCITATORY, 120.0, 110.0);
                assertTrue(fixture.model.addStructureMember(structure.id(), member.id()));
                fixture.model.structure(structure.id()).setExpanded(true);
                FieldStateSnapshot expandedState = FieldStateSnapshot.capture(fixture.model);

                fixture.presentation.refreshAll();
                NeuronView structureView = fixture.neuronViews.get(structure.id());
                assertNotNull(structureView);
                assertTrue(structureView.isStructureDrawerExpanded());

                assertNotNull(fixture.model.removeNeuron(structure.id()));
                fixture.presentation.refreshAll();
                assertFalse(fixture.neuronViews.containsKey(structure.id()));

                expandedState.restoreInto(fixture.model);
                fixture.presentation.refreshAll();
                NeuronView restoredStructureView = fixture.neuronViews.get(structure.id());
                assertNotNull(restoredStructureView);
                assertTrue(restoredStructureView.isStructureDrawerExpanded());

                fixture.model.structure(structure.id()).setExpanded(false);
                fixture.presentation.refreshAll();
                assertFalse(restoredStructureView.isStructureDrawerExpanded());
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що поверхня шухлядки знаходиться під зв'язками, а учасники й сяйво — над ними. */
    @Test
    void expandedDrawerSurfaceIsBelowConnectionsAndMemberViewsRemainAboveThem() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron structure = fixture.model.createNeuron(NeuronType.STRUCTURE, 80.0, 60.0);
                Neuron member = fixture.model.createNeuron(NeuronType.EXCITATORY, 120.0, 110.0);
                Neuron outside = fixture.model.createNeuron(NeuronType.INHIBITORY, 500.0, 120.0);
                assertTrue(fixture.model.addStructureMember(structure.id(), member.id()));
                assertTrue(fixture.model.createConnection(member.id(), outside.id()));
                fixture.model.structure(structure.id()).setExpanded(true);

                fixture.presentation.refreshAll();

                NeuronStructureDrawerSurfaceView surface = fixture.workspace.structureDrawerLayer()
                        .getChildren().stream()
                        .filter(NeuronStructureDrawerSurfaceView.class::isInstance)
                        .map(NeuronStructureDrawerSurfaceView.class::cast)
                        .findFirst().orElse(null);
                assertNotNull(surface, "Координатор має створити окрему поверхню шухлядки");
                assertTrue(surface.isVisible(), "Відкрита шухлядка має показувати суцільний фон і контур");
                assertSame(fixture.workspace.structureDrawerLayer(), surface.getParent());
                assertTrue(fixture.workspace.edgeLayer().getChildren().stream().anyMatch(ConnectionView.class::isInstance));

                Pane world = (Pane) fixture.workspace.nodeLayer().getParent();
                List<Node> layers = List.copyOf(world.getChildren());
                assertTrue(layers.indexOf(fixture.workspace.structureDrawerLayer())
                                < layers.indexOf(fixture.workspace.edgeLayer()),
                        "Фон і контур шухлядки мають малюватися перед зв'язками");
                assertTrue(layers.indexOf(fixture.workspace.edgeLayer())
                                < layers.indexOf(fixture.workspace.nodeLayer()),
                        "Нейрони на шухлядці мають малюватися після зв'язків");

                MoveTo surfaceOrigin = (MoveTo) surface.getElements().getFirst();
                assertEquals(80.0, surfaceOrigin.getX(), 0.001);
                assertEquals(60.0 + NeuronView.HEIGHT, surfaceOrigin.getY(), 0.001);
                NeuronView memberView = fixture.neuronViews.get(member.id());
                NeuronStructureDrawerView drawer = (NeuronStructureDrawerView) memberView.getParent();
                assertSame(fixture.neuronViews.get(structure.id()).getChildren().stream()
                        .filter(NeuronStructureDrawerView.class::isInstance).findFirst().orElseThrow(), drawer);
                Node structureBody = fixture.neuronViews.get(structure.id()).getChildren().getFirst();
                assertTrue(drawer.getViewOrder() > structureBody.getViewOrder(),
                        "Вміст шухлядки має малюватися раніше за тіло структури, щоб сяйво було поверх учасників");

                fixture.model.structure(structure.id()).setExpanded(false);
                fixture.presentation.refreshAll();
                assertFalse(surface.isVisible(), "Поверхня закритої шухлядки має ховатися");
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що подвійне клацання зберігає відкритість у моделі та запускає механізм збереження. */
    @Test
    void doubleClickPersistsStructureDrawerState() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron structure = fixture.model.createNeuron(NeuronType.STRUCTURE, 80.0, 60.0);
                Neuron member = fixture.model.createNeuron(NeuronType.EXCITATORY, 120.0, 110.0);
                assertTrue(fixture.model.addStructureMember(structure.id(), member.id()));
                fixture.presentation.refreshAll();

                NeuronView structureView = fixture.neuronViews.get(structure.id());
                assertNotNull(structureView);
                fireDoubleClick(structureView);
                assertTrue(fixture.model.structure(structure.id()).isExpanded());
                assertTrue(structureView.isStructureDrawerExpanded());
                assertEquals(1, fixture.saveCount.get());

                fireDoubleClick(structureView);
                assertFalse(fixture.model.structure(structure.id()).isExpanded());
                assertFalse(structureView.isStructureDrawerExpanded());
                assertEquals(2, fixture.saveCount.get());
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Надсилає подію подвійного клацання, яку обробляє контролер взаємодії з нейронами. */
    private static void fireDoubleClick(NeuronView neuronView) {
        MouseEvent event = new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                10.0, 10.0, 10.0, 10.0,
                MouseButton.PRIMARY, 2,
                false, false, false, false,
                true, false, false, false, false, false, null
        );
        javafx.event.Event.fireEvent(neuronView, event);
    }

    /** Збирає сумісний набір компонентів, щоб перевіряти реальний цикл синхронізації подань. */
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
        private final MainMenuView mainMenu = new MainMenuView(localization, ignored -> { });
        private final StackPane settingsOverlayHost = new StackPane(workspace.node(), mainMenu);
        private final HBox toolbar = new HBox();
        private final NeuronSettingsOverlayCoordinator settingsOverlayCoordinator =
                new NeuronSettingsOverlayCoordinator(settingsOverlayHost, mainMenu, localization);
        private final AtomicInteger saveCount = new AtomicInteger();
        private final SelectionController selectionController = new SelectionController(
                groupService, state, () -> { }, () -> { }, ignored -> { }, localization);
        private final ConnectionController connectionController = ConnectionControllerFactory.create(
                new ConnectionControllerFactory.Dependencies(
                        connectionService, state, workspace, neuronViews, this::refreshPresentation,
                        () -> { }, ignored -> { }, localization));
        private final SimulationController simulationController = new SimulationController(
                neuronService,
                new SimulationService(),
                new SimulationStepPresenter(
                        workspace, neuronViews, () -> { }, new PulseAnimationController(workspace)),
                () -> { }, ignored -> { }, ignored -> { }, ignored -> { },
                100.0, 10.0, 1_000.0, localization);
        private final NeuronInteractionController neuronController = NeuronInteractionControllerFactory.create(
                new NeuronInteractionControllerFactory.Dependencies(
                        neuronService, groupService, selectionController,
                        new NeuronClipboardService(neuronService, groupService), state, workspace,
                        neuronViews, rotationHandles, connectionController, simulationController,
                        saveCount::incrementAndGet, this::refreshPresentation, ignored -> { },
                        new NeuronMenuView(toolbar, state::selectedNeuronForMenu, ignored -> { }, localization),
                        localization, settingsOverlayCoordinator));
        private final MapPresentationCoordinator presentation = new MapPresentationCoordinator(
                neuronService, workspace, neuronViews, connectionViews, rotationHandles,
                neuronController, connectionController,
                new NeuronLayerOrderManager(neuronViews, connectionViews, model::connections));

        /** Створює сцену тестового редактора до синхронізації моделей і подань. */
        private Fixture() {
            Scene scene = new Scene(settingsOverlayHost, 1_000.0, 700.0);
            scene.getRoot().applyCss();
            scene.getRoot().layout();
        }

        /** Оновлює презентацію через її єдиного власника після дій, які виконує контролер. */
        private void refreshPresentation() {
            presentation.refreshAll();
        }

        /** Звільняє контролери й меню після тесту, не залишаючи активних фонових ресурсів. */
        private void dispose() {
            neuronController.dispose();
            simulationController.shutdown();
            connectionController.dispose();
            mainMenu.dispose();
            workspace.dispose();
        }
    }
}
