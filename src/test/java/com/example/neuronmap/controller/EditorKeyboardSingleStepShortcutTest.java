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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що Ctrl+Space запускає один такт лише на ручній паузі й не підміняє звичайний Space. */
final class EditorKeyboardSingleStepShortcutTest {

    /** Запускає JavaFX перед тестуванням обробників клавіатурних подій. */
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

    /** Перевіряє, що Ctrl+Space викликає тільки однотактну дію та враховує умову доступності. */
    @Test
    void controlSpaceRunsOnlyWhenStepShortcutIsAvailable() throws Exception {
        runOnFxThread(() -> {
            StackPane root = new StackPane();
            Scene scene = new Scene(root);
            AtomicInteger pauseCalls = new AtomicInteger();
            AtomicInteger stepCalls = new AtomicInteger();
            AtomicBoolean normalPauseAvailable = new AtomicBoolean(true);
            AtomicBoolean stepAvailable = new AtomicBoolean(false);
            EditorKeyboardController controller = new EditorKeyboardController(
                    () -> true, () -> true, () -> true, () -> { },
                    pauseCalls::incrementAndGet, normalPauseAvailable::get, root);
            controller.configureSimulationStepShortcut(stepCalls::incrementAndGet, stepAvailable::get);
            try {
                controller.install(scene);
                fireSpace(root, true);
                assertEquals(0, stepCalls.get(), "Під час звичайного виконання Ctrl+Space нічого не запускає");
                assertEquals(0, pauseCalls.get(), "Ctrl+Space не повинен перемикати звичайну паузу");
                releaseSpace(root);

                stepAvailable.set(true);
                fireSpace(root, true);
                assertEquals(1, stepCalls.get());
                assertEquals(0, pauseCalls.get());
                fireSpace(root, true);
                assertEquals(1, stepCalls.get(), "Автоповтор натиснутої клавіші не дублює однотактну дію");
                releaseSpace(root);

                stepAvailable.set(false);
                fireSpace(root, true);
                assertEquals(1, stepCalls.get(), "Команда не працює після зняття паузи");
                assertEquals(0, pauseCalls.get());
                releaseSpace(root);

                fireSpace(root, false);
                assertEquals(1, pauseCalls.get(), "Звичайний Space продовжує перемикати ручну паузу");
                assertEquals(1, stepCalls.get());
                releaseSpace(root);
            } finally {
                controller.dispose();
            }
        });
    }

    /** Перевіряє, що Ctrl+Space не перехоплюється в текстовому полі, навіть за доступної команди. */
    @Test
    void controlSpaceLeavesTextInputUntouched() throws Exception {
        runOnFxThread(() -> {
            TextField field = new TextField();
            Scene scene = new Scene(new StackPane(field));
            AtomicInteger stepCalls = new AtomicInteger();
            EditorKeyboardController controller = new EditorKeyboardController(
                    () -> false, () -> false, () -> false, () -> { }, () -> { }, () -> true, field);
            controller.configureSimulationStepShortcut(stepCalls::incrementAndGet, () -> true);
            try {
                controller.install(scene);
                fireSpace(field, true);
                assertEquals(0, stepCalls.get());
                releaseSpace(field);
            } finally {
                controller.dispose();
            }
        });
    }

    /**
     * Надсилає пробіл з обраним станом Ctrl до звичайного маршруту обробки JavaFX.
     *
     * @param target вузол, якому надсилається подія.
     * @param controlDown чи натиснуто Ctrl разом із пробілом.
     */
    private static void fireSpace(javafx.scene.Node target, boolean controlDown) {
        Event.fireEvent(target, new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, controlDown, false, false));
    }

    /**
     * Надсилає подію відпускання пробілу для дозволу наступного натискання.
     *
     * @param target вузол, якому надсилається подія.
     */
    private static void releaseSpace(javafx.scene.Node target) {
        Event.fireEvent(target, new KeyEvent(
                KeyEvent.KEY_RELEASED, "", "", KeyCode.SPACE, false, false, false, false));
    }

    /**
     * Виконує перевірку в потоці JavaFX і передає помилки до потоку JUnit.
     *
     * @param action перевірка, яку потрібно виконати у JavaFX Application Thread.
     * @throws Exception якщо очікування потоку перервано або перевищено час очікування.
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
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure[0] != null) {
            throw new AssertionError("Тест гарячої клавіші завершився помилкою", failure[0]);
        }
    }
}
