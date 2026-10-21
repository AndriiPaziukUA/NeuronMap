package com.example.neuronmap.controller;

import com.example.neuronmap.view.support.EditorKeyboardShortcutBoundary;
import com.example.neuronmap.view.support.SceneKeyboardModality;
import com.example.neuronmap.view.editor.WorkspaceView;
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

/** Перевіряє маршрутизацію гарячих клавіш редактора та звільнення підписок на події. */
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

    /** Перевіряє, що Esc у режимі додавання скасовує його перед передаванням події фільтру меню. */
    @Test
    void prioritizesAddModeCancellationOverMenuEscapeHandler() throws Exception {
        runOnFxThread(() -> {
            StackPane root = new StackPane();
            TextField textField = new TextField();
            root.getChildren().add(textField);
            Scene scene = new Scene(root);
            AtomicBoolean addModeActive = new AtomicBoolean(true);
            AtomicInteger cancelCalls = new AtomicInteger();
            AtomicInteger menuCalls = new AtomicInteger();
            EditorKeyboardController controller = new EditorKeyboardController(
                    () -> false,
                    () -> false,
                    () -> false,
                    () -> {
                        cancelCalls.incrementAndGet();
                        addModeActive.set(false);
                    },
                    () -> { },
                    () -> false,
                    root,
                    addModeActive::get
            );
            controller.install(scene);
            scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (!event.isConsumed() && event.getCode() == KeyCode.ESCAPE) {
                    menuCalls.incrementAndGet();
                    event.consume();
                }
            });

            fireKey(textField, KeyCode.ESCAPE, false);

            assertEquals(1, cancelCalls.get());
            assertEquals(0, menuCalls.get(), "Esc у режимі розміщення не має відкривати головне меню");
            controller.dispose();
        });
    }

    /** Перевіряє, що пробіл перемикає паузу лише на дошці, коли команда доступна, і не повторює дію при автоповторі. */
    @Test
    void routesSpaceOnlyOnBoardWhenAvailableAndOncePerKeyPress() throws Exception {
        runOnFxThread(() -> {
            AtomicInteger pauseCalls = new AtomicInteger();
            AtomicBoolean available = new AtomicBoolean(true);
            WorkspaceView workspace = new WorkspaceView();
            StackPane board = workspace.node();
            StackPane outsideBoard = new StackPane();
            TextField textField = new TextField();
            board.getChildren().add(textField);
            StackPane sceneRoot = new StackPane(board, outsideBoard);
            EditorKeyboardController controller = new EditorKeyboardController(
                    () -> true,
                    () -> true,
                    () -> true,
                    () -> { },
                    pauseCalls::incrementAndGet,
                    available::get,
                    board
            );
            controller.install(new Scene(sceneRoot));
            assertTrue(board.isFocusTraversable(), "Дошка має приймати клавіатурний фокус");

            fireKey(board, KeyCode.SPACE, false);
            fireKey(board, KeyCode.SPACE, false);
            assertEquals(1, pauseCalls.get(), "Автоповтор не має повторно перемикати паузу");
            fireKeyReleased(board, KeyCode.SPACE);

            fireKey(textField, KeyCode.SPACE, false);
            assertEquals(1, pauseCalls.get(), "Пробіл у текстовому полі залишається звичайним введенням");
            fireKeyReleased(textField, KeyCode.SPACE);

            fireKey(outsideBoard, KeyCode.SPACE, false);
            assertEquals(1, pauseCalls.get(), "Поза дошкою гаряча клавіша не має працювати");
            fireKeyReleased(outsideBoard, KeyCode.SPACE);

            available.set(false);
            fireKey(board, KeyCode.SPACE, false);
            assertEquals(1, pauseCalls.get(), "Команда недоступна, коли кнопки симуляції приховані");
            fireKeyReleased(board, KeyCode.SPACE);

            available.set(true);
            fireKey(board, KeyCode.SPACE, true);
            assertEquals(1, pauseCalls.get(), "Комбінація з Ctrl не є командою паузи");
            fireKeyReleased(board, KeyCode.SPACE);

            fireKey(board, KeyCode.SPACE, false);
            assertEquals(2, pauseCalls.get(), "Нове натискання після відпускання знову перемикає паузу");
            fireKeyReleased(board, KeyCode.SPACE);
            controller.dispose();
            workspace.dispose();
        });
    }

    /** Перевіряє, що активна модальність вимикає глобальну клавіатурну команду навіть поза накладкою. */
    @Test
    void doesNotRouteSpaceWhileSceneHasModalKeyboardBoundary() throws Exception {
        runOnFxThread(() -> {
            AtomicInteger pauseCalls = new AtomicInteger();
            WorkspaceView workspace = new WorkspaceView();
            StackPane board = workspace.node();
            Scene scene = new Scene(board);
            EditorKeyboardController controller = new EditorKeyboardController(
                    () -> true, () -> true, () -> true, () -> { },
                    pauseCalls::incrementAndGet, () -> true, board);
            ShortcutBoundaryPane boundary = new ShortcutBoundaryPane();
            controller.install(scene);
            SceneKeyboardModality.register(scene, boundary);
            try {
                fireKey(board, KeyCode.SPACE, false);
                assertEquals(0, pauseCalls.get());
            } finally {
                fireKeyReleased(board, KeyCode.SPACE);
                SceneKeyboardModality.unregister(scene, boundary);
                controller.dispose();
                workspace.dispose();
            }
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

    /**
     * Надсилає клавіатурну подію натискання цільовому вузлу та запускає стандартний маршрут JavaFX.
     *
     * @param target вузол, якому надсилається подія.
     * @param code код натиснутої клавіші.
     * @param controlDown чи натиснуто Ctrl разом із клавішею.
     */
    private static void fireKey(javafx.scene.Node target, KeyCode code, boolean controlDown) {
        Event.fireEvent(target, new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", code, false, controlDown, false, false));
    }

    /**
     * Надсилає подію відпускання клавіші для завершення поточного натискання.
     *
     * @param target вузол, якому надсилається подія.
     * @param code код відпущеної клавіші.
     */
    private static void fireKeyReleased(javafx.scene.Node target, KeyCode code) {
        Event.fireEvent(target, new KeyEvent(
                KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false));
    }

    /**
     * Виконує перевірку на потоці JavaFX і передає помилки в тестовий потік.
     *
     * @param action перевірка, яку потрібно виконати в потоці JavaFX.
     * @throws Exception якщо очікування JavaFX-потоку перервано або завершилося помилкою.
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
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError("Помилка тесту маршрутизатора клавіатури", failure[0]);
        }
    }

    /** Тестова модальна межа, яка блокує глобальні гарячі клавіші редактора. */
    private static final class ShortcutBoundaryPane extends StackPane implements EditorKeyboardShortcutBoundary {

        /** Повертає ознаку блокування глобальних команд для тестової модальної межі. */
        @Override
        public boolean blocksEditorShortcuts() {
            return true;
        }
    }
}
