package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.RotationHandleView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NeuronInteractionControllerMouseButtonTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    @Test
    void leftClickShowsRotationHandleWithoutMenu() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture();
            click(fixture.neuronView(), MouseButton.PRIMARY);
            assertTrue(fixture.handle().isVisible());
            assertFalse(hasVisibleMenu(fixture.workspace()));
        });
    }

    @Test
    void rightClickShowsMenuAndHidesRotationHandle() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture();

            click(fixture.neuronView(), MouseButton.PRIMARY);
            assertTrue(fixture.handle().isVisible());

            click(fixture.neuronView(), MouseButton.SECONDARY);

            assertTrue(hasVisibleMenu(fixture.workspace()));
            assertFalse(fixture.handle().isVisible());
        });
    }

    private static Fixture fixture() {
        NeuronMapModel model = new NeuronMapModel();
        var neuron = model.createNeuron(
                NeuronType.EXCITATORY,
                100.0,
                80.0
        );

        NeuronService neuronService = new NeuronService(model);
        GroupService groupService = new GroupService(model);
        ConnectionService connectionService = new ConnectionService(model);
        EditorState state = new EditorState(1.0, 0.0, 0.0);
        WorkspaceView workspace = new WorkspaceView();
        Map<String, NeuronView> neuronViews = new HashMap<>();
        Map<String, RotationHandleView> rotationHandles = new HashMap<>();

        SelectionController selectionController = new SelectionController(
                groupService,
                state,
                () -> { },
                () -> { },
                ignored -> { }
        );

        SimulationStepPresenter stepPresenter = new SimulationStepPresenter(
                neuronService,
                workspace,
                neuronViews,
                () -> { }
        );
        SimulationController simulation = new SimulationController(
                neuronService,
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

        HBox toolbar = new HBox();
        NeuronMenuCustomizer menuCustomizer = new NeuronMenuCustomizer(
                toolbar,
                state::selectedNeuronForMenu,
                ignored -> { }
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
                menuCustomizer
        );

        Pane root = new Pane(workspace.node());
        Scene scene = new Scene(root, 1000.0, 700.0);
        scene.getRoot().applyCss();
        root.layout();

        controller.loadViews();

        NeuronView neuronView = neuronViews.get(neuron.id());
        assertNotNull(neuronView);

        return new Fixture(
                controller,
                workspace,
                neuronView,
                rotationHandles.get(neuron.id()),
                scene
        );
    }

    private static void click(NeuronView neuronView, MouseButton button) {
        fire(neuronView, MouseEvent.MOUSE_PRESSED, button);
        fire(neuronView, MouseEvent.MOUSE_RELEASED, button);
        fire(neuronView, MouseEvent.MOUSE_CLICKED, button);
    }

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

    private static boolean hasVisibleMenu(WorkspaceView workspace) {
        return workspace.overlayLayer().getChildren().stream()
                .anyMatch(node -> node instanceof javafx.scene.layout.VBox
                        && node.isVisible());
    }

    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }

    private record Fixture(
            NeuronInteractionController controller,
            WorkspaceView workspace,
            NeuronView neuronView,
            RotationHandleView handle,
            Scene scene
    ) {
    }
}
