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

/**
 * Перевіряє обробку натискань різних кнопок миші.
 */
final class NeuronInteractionControllerMouseButtonTest {

    /**
     * Запускає або планує дію, повʼязану з «відповідну операцію».
     */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /**
     * Перевіряє очікувану поведінку: показує обертання обробити без меню.
     */
    @Test
    void leftClickShowsRotationHandleWithoutMenu() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture();
            click(fixture.neuronView(), MouseButton.PRIMARY);
            assertTrue(fixture.handle().isVisible());
            assertFalse(hasVisibleMenu(fixture.workspace()));
        });
    }

    /**
     * Перевіряє очікувану поведінку: показує меню і приховує обертання обробити.
     */
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

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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

    /**
     * Виконує операцію «відповідну операцію».
     *
     * @param neuronView значення, що визначає нейрон відображення для цієї операції.
     *
     * @param button значення, що визначає кнопка для цієї операції.
     */
    private static void click(NeuronView neuronView, MouseButton button) {
        fire(neuronView, MouseEvent.MOUSE_PRESSED, button);
        fire(neuronView, MouseEvent.MOUSE_RELEASED, button);
        fire(neuronView, MouseEvent.MOUSE_CLICKED, button);
    }

    /**
     * Виконує операцію «відповідну операцію».
     *
     * @param neuronView значення, що визначає нейрон відображення для цієї операції.
     *
     * @param type тип обʼєкта.
     *
     * @param button значення, що визначає кнопка для цієї операції.
     */
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

    /**
     * Перевіряє, чи виконується умова «меню».
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    private static boolean hasVisibleMenu(WorkspaceView workspace) {
        return workspace.overlayLayer().getChildren().stream()
                .anyMatch(node -> node instanceof javafx.scene.layout.VBox
                        && node.isVisible());
    }

    /**
     * Запускає або планує дію, повʼязану з «відповідну операцію».
     *
     * @param action дія, яку потрібно виконати.
     */
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
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure[0] != null) {
            /**
             * Повертає результат операції «відповідну операцію».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param controller значення, що визначає відповідну операцію для цієї операції.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronView значення, що визначає нейрон відображення для цієї операції.
     *
     * @param handle значення, що визначає обробити для цієї операції.
     *
     * @param scene значення, що визначає сцена для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    /**
     * Набір модульних тестів типу Fixture. Перевіряє його основну поведінку та обробку некоректних або крайових даних.
     */
    private record Fixture(
            NeuronInteractionController controller,
            WorkspaceView workspace,
            NeuronView neuronView,
            RotationHandleView handle,
            Scene scene
    ) {
    }
}
