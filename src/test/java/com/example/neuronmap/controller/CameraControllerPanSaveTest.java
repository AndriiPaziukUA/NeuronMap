package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що камера зберігається лише після фактичної зміни положення полотна. */
final class CameraControllerPanSaveTest {

    private static final long SAVE_DEBOUNCE_WAIT_MILLIS = 600L;

    /** Запускає JavaFX перед тестами, що створюють полотно та обробники миші. */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /** Перевіряє, що звичайне натискання й відпускання полотна не запускають збереження камери. */
    @Test
    void clickingCanvasWithoutMovingDoesNotSaveCamera() throws Exception {
        AtomicInteger saveCalls = new AtomicInteger();
        AtomicReference<CameraController> controllerReference = new AtomicReference<>();

        runOnFxThread(() -> {
            WorkspaceView workspace = new WorkspaceView();
            CameraController controller = createController(workspace, saveCalls);
            controller.install();
            controllerReference.set(controller);

            fireMouseEvent(workspace, MouseEvent.MOUSE_PRESSED, 120.0, 80.0, true);
            fireMouseEvent(workspace, MouseEvent.MOUSE_RELEASED, 120.0, 80.0, false);
        });

        Thread.sleep(SAVE_DEBOUNCE_WAIT_MILLIS);
        assertEquals(0, saveCalls.get(), "Clicking without moving the canvas must not save it");
        runOnFxThread(controllerReference.get()::cancelPendingSave);
    }

    /** Перевіряє, що фактичне перетягування полотна планує одне відкладене збереження. */
    @Test
    void draggingCanvasSchedulesSaveAfterMovement() throws Exception {
        AtomicInteger saveCalls = new AtomicInteger();
        AtomicReference<CameraController> controllerReference = new AtomicReference<>();

        runOnFxThread(() -> {
            WorkspaceView workspace = new WorkspaceView();
            EditorState state = new EditorState(1.0, 0.0, 0.0);
            CameraController controller = createController(workspace, state, saveCalls);
            controller.install();
            controllerReference.set(controller);

            fireMouseEvent(workspace, MouseEvent.MOUSE_PRESSED, 120.0, 80.0, true);
            fireMouseEvent(workspace, MouseEvent.MOUSE_DRAGGED, 132.0, 91.0, true);
            fireMouseEvent(workspace, MouseEvent.MOUSE_RELEASED, 132.0, 91.0, false);

            assertEquals(12.0, state.panX(), 0.0001);
            assertEquals(11.0, state.panY(), 0.0001);
            assertEquals(0, saveCalls.get(), "Saving is debounced until the camera interaction settles");
        });

        Thread.sleep(SAVE_DEBOUNCE_WAIT_MILLIS);
        assertEquals(1, saveCalls.get(), "A completed pan should save the changed camera exactly once");
        runOnFxThread(controllerReference.get()::cancelPendingSave);
    }

    /** Створює контролер камери з початковим станом для перевірки натискання та відпускання. */
    private static CameraController createController(WorkspaceView workspace, AtomicInteger saveCalls) {
        return createController(workspace, new EditorState(1.0, 0.0, 0.0), saveCalls);
    }

    /** Створює контролер із тестовим станом і лічильником збережень, не підключаючи сторонні операції. */
    private static CameraController createController(
            WorkspaceView workspace,
            EditorState state,
            AtomicInteger saveCalls
    ) {
        return new CameraController(
                state,
                workspace,
                () -> { },
                saveCalls::incrementAndGet,
                () -> false,
                ignored -> false,
                ignored -> { },
                ignored -> { },
                1.2,
                new LocalizationService(Locale.forLanguageTag("uk"))
        );
    }

    /** Надсилає мишачу подію полотну з узгодженим станом кнопки, як під час реальної взаємодії. */
    private static void fireMouseEvent(
            WorkspaceView workspace,
            EventType<MouseEvent> eventType,
            double x,
            double y,
            boolean primaryButtonDown
    ) {
        boolean pressEvent = eventType == MouseEvent.MOUSE_PRESSED;
        boolean releaseEvent = eventType == MouseEvent.MOUSE_RELEASED;
        MouseEvent event = new MouseEvent(
                eventType,
                x,
                y,
                x,
                y,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                primaryButtonDown,
                false,
                false,
                false,
                false,
                pressEvent || (!releaseEvent && primaryButtonDown),
                null
        );
        Event.fireEvent(workspace.node(), event);
    }

    /** Виконує перевірку на потоці JavaFX і повертає помилку в потік тесту. */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure.get() != null) {
            throw new AssertionError("JavaFX test failed", failure.get());
        }
    }
}
