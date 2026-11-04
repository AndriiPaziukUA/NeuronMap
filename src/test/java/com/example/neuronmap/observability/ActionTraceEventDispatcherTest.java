package com.example.neuronmap.observability;

import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє інтеграцію трасування з JavaFX-сценою та збереження звичайної маршрутизації подій. */
class ActionTraceEventDispatcherTest {

    /** Перевіряє, що рух вказівника проходить через сцену всередині активного контексту дії. */
    @Test
    void tracesPointerMovementWithoutChangingEventDelivery() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Pane root = new Pane();
            Scene scene = new Scene(root);
            var originalDispatcher = scene.getEventDispatcher();
            AtomicReference<ActionTraceContext.ActionSession> observedSession = new AtomicReference<>();
            root.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
                assertNotNull(event);
                observedSession.set(ActionTraceContext.currentSession());
            });

            ActionTraceEventDispatcher dispatcher = ActionTraceEventDispatcher.install(scene);
            Event.fireEvent(root, mouseEvent(MouseEvent.MOUSE_MOVED, MouseButton.NONE, false));

            assertNotNull(observedSession.get());
            assertEquals(1L, observedSession.get().eventCount());
            assertTrue(observedSession.get().identifier().startsWith("A"));
            dispatcher.close();
            assertSame(originalDispatcher, scene.getEventDispatcher());
            assertEquals(1L, observedSession.get().eventCount());
        });
    }

    /** Перевіряє, що фізичний жест миші отримує один ID для натискання, відпускання та клацання. */
    @Test
    void groupsMouseGestureEventsIntoOneAction() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Pane root = new Pane();
            Scene scene = new Scene(root);
            List<ActionTraceContext.ActionSession> observedSessions = new ArrayList<>();
            root.addEventHandler(MouseEvent.ANY, event -> {
                assertNotNull(event);
                observedSessions.add(ActionTraceContext.currentSession());
            });
            ActionTraceEventDispatcher dispatcher = ActionTraceEventDispatcher.install(scene);

            Event.fireEvent(root, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY, true));
            Event.fireEvent(root, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY, false));
            Event.fireEvent(root, mouseEvent(MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY, false));

            assertEquals(3, observedSessions.size());
            assertNotNull(observedSessions.getFirst());
            assertSame(observedSessions.getFirst(), observedSessions.get(1));
            assertSame(observedSessions.getFirst(), observedSessions.get(2));
            assertEquals(3L, observedSessions.getFirst().eventCount());
            dispatcher.close();
        });
    }

    /** Перевіряє групування клавіатурного циклу без розкриття введеного символу. */
    @Test
    void groupsKeyboardCycleIntoOneAction() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Pane root = new Pane();
            Scene scene = new Scene(root);
            List<ActionTraceContext.ActionSession> observedSessions = new ArrayList<>();
            root.addEventHandler(KeyEvent.ANY, event -> {
                assertNotNull(event);
                observedSessions.add(ActionTraceContext.currentSession());
            });
            ActionTraceEventDispatcher dispatcher = ActionTraceEventDispatcher.install(scene);

            Event.fireEvent(root, keyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.A));
            Event.fireEvent(root, keyEvent(KeyEvent.KEY_TYPED, "x", "x", KeyCode.UNDEFINED));
            Event.fireEvent(root, keyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.A));

            assertEquals(3, observedSessions.size());
            assertNotNull(observedSessions.getFirst());
            assertSame(observedSessions.getFirst(), observedSessions.get(1));
            assertSame(observedSessions.getFirst(), observedSessions.get(2));
            assertEquals(3L, observedSessions.getFirst().eventCount());
            dispatcher.close();
        });
    }

    /** Перевіряє, що виклики під час CSS/layout одного кадру об'єднуються в окрему дію. */
    @Test
    void capturesMethodsInsideScenePulse() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Scene scene = new Scene(new Pane());
            ActionTraceEventDispatcher dispatcher = ActionTraceEventDispatcher.install(scene);

            dispatcher.beginPulseTrace();
            ActionTraceContext.ActionSession pulse = ActionTraceContext.currentSession();
            assertNotNull(pulse);
            ActionTraceContext.MethodInvocation invocation = ActionTraceContext.enterMethod(
                    "WorkspaceView.layoutChildren()", null);
            assertNotNull(invocation);
            invocation.close();
            dispatcher.finishPulseTrace();

            assertNull(ActionTraceContext.currentSession());
            assertTrue(pulse.isFinished());
            assertEquals(1L, pulse.methodCallCount());
            dispatcher.close();
        });
    }

    /** Перевіряє відновлення після пропущеного post-layout callback попереднього кадру. */
    @Test
    void closesStalePulseBeforeStartingNextFrame() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Scene scene = new Scene(new Pane());
            ActionTraceEventDispatcher dispatcher = ActionTraceEventDispatcher.install(scene);

            dispatcher.beginPulseTrace();
            ActionTraceContext.ActionSession firstPulse = ActionTraceContext.currentSession();
            dispatcher.beginPulseTrace();
            ActionTraceContext.ActionSession secondPulse = ActionTraceContext.currentSession();

            assertNotNull(firstPulse);
            assertNotNull(secondPulse);
            assertTrue(firstPulse.isFinished());
            dispatcher.finishPulseTrace();
            assertTrue(secondPulse.isFinished());
            dispatcher.close();
        });
    }

    /** Перевіряє, що межа кадру не створює вкладену дію всередині вже активної UI-взаємодії. */
    @Test
    void keepsScenePulseInsideExistingAction() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Scene scene = new Scene(new Pane());
            ActionTraceEventDispatcher dispatcher = ActionTraceEventDispatcher.install(scene);
            ActionTraceContext.ActionSession action = ActionTraceContext.startEvent("Натискання", "test");
            try (ActionTraceContext.SessionBinding ignored = ActionTraceContext.bind(action)) {
                dispatcher.beginPulseTrace();
                assertSame(action, ActionTraceContext.currentSession());
                dispatcher.finishPulseTrace();
                assertSame(action, ActionTraceContext.currentSession());
            }
            action.finish();
            dispatcher.close();
        });
    }

    /** Перевіряє завершення одноразової програмної дії та збереження її викликів у дереві. */
    @Test
    void finishesStandaloneActionEvent() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Pane root = new Pane();
            Scene scene = new Scene(root);
            AtomicReference<ActionTraceContext.ActionSession> observedSession = new AtomicReference<>();
            root.addEventHandler(ActionEvent.ACTION, event -> {
                assertNotNull(event);
                observedSession.set(ActionTraceContext.currentSession());
                ActionTraceContext.MethodInvocation invocation = ActionTraceContext.enterMethod(
                        "ExampleController.handleAction()", null);
                assertNotNull(invocation);
                invocation.close();
            });
            ActionTraceEventDispatcher dispatcher = ActionTraceEventDispatcher.install(scene);

            Event.fireEvent(root, new ActionEvent());

            assertNotNull(observedSession.get());
            assertTrue(observedSession.get().isFinished());
            assertEquals(1L, observedSession.get().methodCallCount());
            dispatcher.close();
        });
    }

    /**
     * Створює синтетичну клавіатурну подію без залежності від реального пристрою введення.
     *
     * @param eventType тип клавіатурної події.
     * @param character текстовий символ події.
     * @param text текст події.
     * @param code код клавіші.
     * @return подія з указаними параметрами.
     */
    private static KeyEvent keyEvent(
            javafx.event.EventType<KeyEvent> eventType,
            String character,
            String text,
            KeyCode code
    ) {
        return new KeyEvent(eventType, character, text, code, false, false, false, false);
    }

    /**
     * Створює синтетичну подію миші без залежності від реального пристрою введення.
     *
     * @param eventType тип події миші.
     * @param button кнопка, пов'язана з подією.
     * @param primaryButtonDown чи утримується основна кнопка миші.
     * @return подія з указаними параметрами.
     */
    private static MouseEvent mouseEvent(
            javafx.event.EventType<? extends MouseEvent> eventType,
            MouseButton button,
            boolean primaryButtonDown
    ) {
        return new MouseEvent(
                eventType,
                15.0,
                25.0,
                15.0,
                25.0,
                button,
                1,
                false,
                false,
                false,
                false,
                primaryButtonDown,
                false,
                false,
                true,
                false,
                true,
                null
        );
    }
}
