package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє повне від'єднання підписок камери та очищення відкладеного збереження. */
final class CameraControllerLifecycleTest {

    /** Перевіряє, що повторний dispose від'єднує обробники й більше не змінює стан камери. */
    @Test
    void disposeIsIdempotentAndDetachesWorkspaceHandlers() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            EditorState state = new EditorState(1.0, 0.0, 0.0);
            CameraController controller = new CameraController(
                    state, workspace, () -> { }, () -> { }, () -> false, ignored -> false,
                    ignored -> { }, ignored -> { }, 1.2,
                    new LocalizationService(Locale.forLanguageTag("uk")));
            Scene scene = new Scene(new StackPane(workspace.node()), 600.0, 400.0);
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            controller.install();
            controller.install();
            fireMouseEvent(workspace, MouseEvent.MOUSE_PRESSED, 100.0, 80.0, true);
            fireMouseEvent(workspace, MouseEvent.MOUSE_DRAGGED, 125.0, 96.0, true);
            fireMouseEvent(workspace, MouseEvent.MOUSE_RELEASED, 125.0, 96.0, false);
            assertEquals(25.0, state.panX(), 0.001);
            assertEquals(16.0, state.panY(), 0.001);

            controller.dispose();
            controller.dispose();
            fireMouseEvent(workspace, MouseEvent.MOUSE_PRESSED, 100.0, 80.0, true);
            fireMouseEvent(workspace, MouseEvent.MOUSE_DRAGGED, 180.0, 130.0, true);
            fireMouseEvent(workspace, MouseEvent.MOUSE_RELEASED, 180.0, 130.0, false);
            assertEquals(25.0, state.panX(), 0.001);
            assertEquals(16.0, state.panY(), 0.001);
        });
    }

    /** Перевіряє, що звільнення контролера не залишає активним відкладений запис камери. */
    @Test
    void disposeCancelsPendingSave() throws Exception {
        AtomicReference<CameraController> controllerRef = new AtomicReference<>();
        AtomicInteger saveCalls = new AtomicInteger();
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            CameraController controller = new CameraController(
                    new EditorState(1.0, 0.0, 0.0), workspace, () -> { }, saveCalls::incrementAndGet,
                    () -> false, ignored -> false, ignored -> { }, ignored -> { }, 1.2,
                    new LocalizationService(Locale.ENGLISH));
            controller.install();
            Scene scene = new Scene(new StackPane(workspace.node()), 600.0, 400.0);
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            controllerRef.set(controller);
            fireMouseEvent(workspace, MouseEvent.MOUSE_PRESSED, 100.0, 80.0, true);
            fireMouseEvent(workspace, MouseEvent.MOUSE_DRAGGED, 110.0, 90.0, true);
            fireMouseEvent(workspace, MouseEvent.MOUSE_RELEASED, 110.0, 90.0, false);
            controller.dispose();
        });
        Thread.sleep(350L);
        assertEquals(0, saveCalls.get());
        JavaFxTestSupport.runAndWait(controllerRef.get()::dispose);
    }

    /** Перевіряє перенесення глобальних фільтрів курсора зі старої сцени до нової. */
    @Test
    void changingWorkspaceSceneDetachesPointerFiltersFromPreviousScene() throws Exception {
        AtomicInteger clearedCoordinates = new AtomicInteger();
        AtomicInteger cursorUpdates = new AtomicInteger();
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            Pane firstRoot = new StackPane(workspace.node());
            Scene firstScene = new Scene(firstRoot, 600.0, 400.0);
            CameraController controller = new CameraController(
                    new EditorState(1.0, 0.0, 0.0), workspace, () -> { }, () -> { },
                    () -> false, ignored -> false, ignored -> { }, point -> {
                        if (!Double.isFinite(point.getX()) || !Double.isFinite(point.getY())) {
                            clearedCoordinates.incrementAndGet();
                        } else {
                            cursorUpdates.incrementAndGet();
                        }
                    }, 1.2, new LocalizationService(Locale.ENGLISH));
            firstRoot.applyCss();
            firstRoot.layout();
            controller.install();
            fireMouseEvent(workspace, MouseEvent.MOUSE_MOVED, 30.0, 40.0, false);

            firstRoot.getChildren().remove(workspace.node());
            Scene secondScene = new Scene(new StackPane(workspace.node()), 600.0, 400.0);
            secondScene.getRoot().applyCss();
            secondScene.getRoot().layout();
            fireMouseEvent(workspace, MouseEvent.MOUSE_MOVED, 35.0, 45.0, false);
            assertTrue(cursorUpdates.get() > 0, "Активна сцена має оновлювати координати курсора");
            clearedCoordinates.set(0);

            Event.fireEvent(firstScene, new MouseEvent(
                    MouseEvent.MOUSE_MOVED, 20.0, 20.0, 20.0, 20.0,
                    MouseButton.NONE, 0,
                    false, false, false, false,
                    false, false, false, false, false, true, null));
            assertEquals(0, clearedCoordinates.get(), "Стара сцена не повинна зберігати фільтр курсора");

            controller.dispose();
            assertEquals(1, clearedCoordinates.get(), "Звільнення має очистити координати курсора");
        });
    }

    /** Надсилає узгоджену подію миші до кореневого вузла робочої області. */
    private static void fireMouseEvent(
            WorkspaceView workspace,
            javafx.event.EventType<MouseEvent> type,
            double x,
            double y,
            boolean primaryDown
    ) {
        boolean press = type == MouseEvent.MOUSE_PRESSED;
        boolean release = type == MouseEvent.MOUSE_RELEASED;
        Event.fireEvent(workspace.node(), new MouseEvent(
                type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, primaryDown,
                false, false, false, false, press || (!release && primaryDown), null));
    }
}
