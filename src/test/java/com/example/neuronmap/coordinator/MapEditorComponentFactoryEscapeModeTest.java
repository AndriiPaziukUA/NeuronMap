package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.EditorKeyboardController;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє пріоритет оброблення Esc для інструментів редактора та режимів роботи зі зв'язками. */
final class MapEditorComponentFactoryEscapeModeTest {

    /** Перевіряє пріоритет для додавання нейрона й обох режимів роботи зі зв'язками. */
    @Test
    void prioritizesEscapeForNeuronAdditionAndConnectionModes() {
        EditorState state = new EditorState(1.0, 0.0, 0.0);

        state.enterAddNeuronMode(NeuronType.EXCITATORY);
        assertTrue(MapEditorComponentFactory.requiresPriorityEscapeCancellation(state));

        state.enterCreateConnectionMode("source");
        assertTrue(MapEditorComponentFactory.requiresPriorityEscapeCancellation(state));

        state.enterDeleteConnectionMode("source");
        assertTrue(MapEditorComponentFactory.requiresPriorityEscapeCancellation(state));
    }

    /** Перевіряє, що звичайний стан редактора не перехоплює Esc у головного меню. */
    @Test
    void leavesEscapeAvailableToMenuWhenNoPriorityModeIsActive() {
        EditorState state = new EditorState(1.0, 0.0, 0.0);

        assertFalse(MapEditorComponentFactory.requiresPriorityEscapeCancellation(state));

        state.enterRotateMode();
        assertFalse(MapEditorComponentFactory.requiresPriorityEscapeCancellation(state));
    }

    /**
     * Перевіряє повний маршрут Esc: режим видалення скасовується до запуску фільтра головного меню.
     *
     * @throws Exception якщо виконання JavaFX-події завершиться помилкою.
     */
    @Test
    void cancelsConnectionDeletionBeforeMenuEscapeHandler() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            EditorState state = new EditorState(1.0, 0.0, 0.0);
            state.enterDeleteConnectionMode("source");
            StackPane root = new StackPane();
            Scene scene = new Scene(root);
            AtomicInteger cancelCalls = new AtomicInteger();
            AtomicInteger menuCalls = new AtomicInteger();
            EditorKeyboardController keyboardController = new EditorKeyboardController(
                    () -> false,
                    () -> false,
                    () -> false,
                    () -> {
                        cancelCalls.incrementAndGet();
                        state.resetToIdle();
                    },
                    () -> { },
                    () -> false,
                    root,
                    () -> MapEditorComponentFactory.requiresPriorityEscapeCancellation(state)
            );
            keyboardController.install(scene);
            scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (!event.isConsumed() && event.getCode() == KeyCode.ESCAPE) {
                    menuCalls.incrementAndGet();
                    event.consume();
                }
            });

            try {
                Event.fireEvent(root, new KeyEvent(
                        KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));

                assertEquals(1, cancelCalls.get());
                assertEquals(EditorState.Mode.IDLE, state.mode());
                assertEquals(0, menuCalls.get(), "Esc не має відкривати меню, поки активне видалення зв'язків");
            } finally {
                keyboardController.dispose();
            }
        });
    }
}
