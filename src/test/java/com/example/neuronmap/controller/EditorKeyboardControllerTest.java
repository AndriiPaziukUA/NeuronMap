package com.example.neuronmap.controller;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє маршрутизацію гарячих клавіш редактора та звільнення підписки на події. */
final class EditorKeyboardControllerTest {

    /** Запускає JavaFX toolkit перед тестами маршрутизатора клавіатури. */
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

    /** Перевіряє, що Delete делегується один раз і не перехоплюється при фокусі в текстовому полі. */
    @Test
    void routesDeleteOnceAndLeavesTextInputUntouched() throws Exception {
        runOnFxThread(() -> {
            AtomicInteger deleteCalls = new AtomicInteger();
            EditorKeyboardController controller = new EditorKeyboardController(
                    () -> true,
                    () -> true,
                    () -> {
                        deleteCalls.incrementAndGet();
                        return true;
                    },
                    () -> { }
            );
            TextField textField = new TextField();
            StackPane root = new StackPane(textField);
            Scene scene = new Scene(root);
            controller.install(scene);
            controller.install(scene);

            fireKey(root, KeyCode.DELETE, false);
            assertEquals(1, deleteCalls.get());

            fireKey(textField, KeyCode.DELETE, false);
            assertEquals(1, deleteCalls.get());
            controller.dispose();
        });
    }

    /** Перевіряє, що Ctrl+Z, Ctrl+Y та Escape спрямовуються до відповідних дій редактора. */
    @Test
    void routesHistoryAndEscapeCommands() throws Exception {
        runOnFxThread(() -> {
            AtomicInteger undoCalls = new AtomicInteger();
            AtomicInteger redoCalls = new AtomicInteger();
            AtomicInteger cancelCalls = new AtomicInteger();
            EditorKeyboardController controller = new EditorKeyboardController(
                    () -> {
                        undoCalls.incrementAndGet();
                        return true;
                    },
                    () -> {
                        redoCalls.incrementAndGet();
                        return true;
                    },
                    () -> false,
                    cancelCalls::incrementAndGet
            );
            StackPane root = new StackPane();
            controller.install(new Scene(root));

            fireKey(root, KeyCode.Z, true);
            fireKey(root, KeyCode.Y, true);
            fireKey(root, KeyCode.ESCAPE, false);

            assertEquals(1, undoCalls.get());
            assertEquals(1, redoCalls.get());
            assertEquals(1, cancelCalls.get());
            controller.dispose();
        });
    }

    /** Перевіряє від'єднання попередньої сцени під час перенесення маршрутизатора та після завершення роботи. */
    @Test
    void detachesFromPreviousSceneAndOnDispose() throws Exception {
        runOnFxThread(() -> {
            AtomicInteger deleteCalls = new AtomicInteger();
            EditorKeyboardController controller = new EditorKeyboardController(
                    () -> false,
                    () -> false,
                    () -> {
                        deleteCalls.incrementAndGet();
                        return true;
                    },
                    () -> { }
            );
            StackPane firstRoot = new StackPane();
            StackPane secondRoot = new StackPane();
            Scene firstScene = new Scene(firstRoot);
            Scene secondScene = new Scene(secondRoot);

            controller.install(firstScene);
            controller.install(secondScene);
            fireKey(firstRoot, KeyCode.DELETE, false);
            assertEquals(0, deleteCalls.get());

            fireKey(secondRoot, KeyCode.DELETE, false);
            assertEquals(1, deleteCalls.get());

            controller.dispose();
            controller.dispose();
            fireKey(secondRoot, KeyCode.DELETE, false);
            assertEquals(1, deleteCalls.get());
        });
    }

    /** Надсилає клавіатурну подію цільовому вузлу та запускає звичайний JavaFX-маршрут події. */
    private static void fireKey(javafx.scene.Node target, KeyCode code, boolean controlDown) {
        Event.fireEvent(
                target,
                new KeyEvent(
                        KeyEvent.KEY_PRESSED,
                        "",
                        "",
                        code,
                        false,
                        controlDown,
                        false,
                        false
                )
        );
    }

    /** Виконує дію в потоці JavaFX та передає помилку виконання до JUnit-потоку. */
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
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError("Помилка тесту маршрутизатора клавіатури", failure[0]);
        }
    }
}
