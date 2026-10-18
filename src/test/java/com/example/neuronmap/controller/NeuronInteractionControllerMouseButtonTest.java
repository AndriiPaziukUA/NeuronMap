package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.coordinator.MapPresentationCoordinator;
import com.example.neuronmap.coordinator.NeuronSettingsOverlayCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronMapModel;
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
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;

import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє реакцію взаємодії з нейроном на різні кнопки миші та видимість контекстного меню. */
final class NeuronInteractionControllerMouseButtonTest {

    /** Перевіряє, що ліве клацання показує ручку обертання без контекстного меню. */
    @Test
    void leftClickShowsRotationHandleWithoutMenu() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = fixture();
            click(fixture.neuronView(), MouseButton.PRIMARY);
            assertTrue(fixture.handle().isVisible());
            assertFalse(hasVisibleMenu(fixture.workspace()));
        });
    }

    /** Перевіряє, що праве клацання показує контекстне меню й приховує ручку обертання. */
    @Test
    void rightClickShowsMenuAndHidesRotationHandle() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = fixture();

            click(fixture.neuronView(), MouseButton.PRIMARY);
            assertTrue(fixture.handle().isVisible());

            click(fixture.neuronView(), MouseButton.SECONDARY);

            assertTrue(hasVisibleMenu(fixture.workspace()));
            assertFalse(fixture.handle().isVisible());
        });
    }

    /** Створює контролери редактора з явним координатором модальної накладки та власником синхронізації подань. */
    private static Fixture fixture() {
        NeuronMapModel model = new NeuronMapModel();
        var neuron = model.createNeuron(NeuronType.EXCITATORY, 100.0, 80.0);
        NeuronService neuronService = new NeuronService(model);
        GroupService groupService = new GroupService(model);
        ConnectionService connectionService = new ConnectionService(model);
        EditorState state = new EditorState(1.0, 0.0, 0.0);
        WorkspaceView workspace = new WorkspaceView();
        Map<String, NeuronView> neuronViews = new HashMap<>();
        Map<String, ConnectionView> connectionViews = new HashMap<>();
        Map<String, RotationHandleView> rotationHandles = new HashMap<>();

        SelectionController selectionController = new SelectionController(
                groupService,
                state,
                () -> { },
                () -> { },
                ignored -> { }
        );

        SimulationStepPresenter stepPresenter = new SimulationStepPresenter(
                workspace,
                neuronViews,
                () -> { },
                new PulseAnimationController(workspace)
        );
        SimulationController simulation = new SimulationController(
                neuronService,
                new SimulationService(),
                stepPresenter,
                () -> { },
                ignored -> { },
                ignored -> { },
                ignored -> { },
                650.0,
                50.0,
                5000.0
        );

        ConnectionController connections = new ConnectionController(
                connectionService,
                state,
                workspace,
                neuronViews,
                () -> { },
                () -> { },
                ignored -> { }
        );

        LocalizationService localization = new LocalizationService(Locale.ENGLISH);
        HBox toolbar = new HBox();
        NeuronMenuView neuronMenuView = new NeuronMenuView(
                toolbar,
                state::selectedNeuronForMenu,
                ignored -> { },
                localization
        );

        NeuronInteractionController controller = new NeuronInteractionController(
                neuronService,
                groupService,
                selectionController,
                new NeuronClipboardService(neuronService, groupService),
                state,
                workspace,
                neuronViews,
                rotationHandles,
                connections,
                simulation,
                () -> { },
                () -> { },
                ignored -> { },
                neuronMenuView,
                localization,
                new NeuronSettingsOverlayCoordinator(workspace, localization)
        );

        new MapPresentationCoordinator(
                neuronService,
                workspace,
                neuronViews,
                connectionViews,
                rotationHandles,
                controller,
                connections,
                new NeuronLayerOrderManager(neuronViews, connectionViews, model::connections)
        );

        Pane root = new Pane(workspace.node());
        Scene scene = new Scene(root, 1000.0, 700.0);
        scene.getRoot().applyCss();
        root.layout();
        controller.loadViews();

        NeuronView neuronView = neuronViews.get(neuron.id());
        assertNotNull(neuronView);

        return new Fixture(controller, workspace, neuronView, rotationHandles.get(neuron.id()), scene);
    }

    /** Викликає події натискання, відпускання та клацання миші для зазначеного подання нейрона. */
    private static void click(NeuronView neuronView, MouseButton button) {
        fire(neuronView, MouseEvent.MOUSE_PRESSED, button);
        fire(neuronView, MouseEvent.MOUSE_RELEASED, button);
        fire(neuronView, MouseEvent.MOUSE_CLICKED, button);
    }

    /** Надсилає подію миші до подання нейрона. */
    private static void fire(
            NeuronView neuronView,
            javafx.event.EventType<MouseEvent> type,
            MouseButton button
    ) {
        MouseEvent event = new MouseEvent(
                type,
                10.0,
                10.0,
                10.0,
                10.0,
                button,
                1,
                false,
                false,
                false,
                false,
                button == MouseButton.PRIMARY,
                false,
                button == MouseButton.SECONDARY,
                false,
                false,
                false,
                null
        );
        javafx.event.Event.fireEvent(neuronView, event);
    }

    /** Перевіряє, чи містить полотно видиме контекстне меню. */
    private static boolean hasVisibleMenu(WorkspaceView workspace) {
        return workspace.overlayLayer().getChildren().stream()
                .anyMatch(node -> node instanceof javafx.scene.layout.VBox && node.isVisible());
    }

    /** Зберігає залежності тестового редактора та вузли, на яких перевіряється взаємодія миші. */
    private record Fixture(
            NeuronInteractionController controller,
            WorkspaceView workspace,
            NeuronView neuronView,
            RotationHandleView handle,
            Scene scene
    ) {
    }
}
