package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.event.Event;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє, що контролер перетягування знімає лише власні обробники кнопок. */
final class NeuronToolDragControllerLifecycleTest {

    /** Перевіряє, що після dispose подія відпускання не змінює курсор кнопки, а callback не запускається. */
    @Test
    void disposeDetachesButtonHandlersAndRestoresOriginalCursor() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            AtomicInteger drops = new AtomicInteger();
            NeuronToolDragController controller = new NeuronToolDragController(
                    new EditorState(1.0, 0.0, 0.0),
                    workspace,
                    (NeuronType ignored, javafx.geometry.Point2D point) -> drops.incrementAndGet()
            );
            Button button = new Button("Neuron");
            button.setCursor(Cursor.WAIT);
            controller.configureToolbarButtons(button, null);
            controller.install();
            controller.dispose();
            controller.dispose();

            Event.fireEvent(button, mouseReleased());

            assertEquals(Cursor.WAIT, button.getCursor());
            assertEquals(0, drops.get());
        });
    }

    /** Перевіряє, що dispose не перезаписує курсор, змінений власником кнопки після підключення. */
    @Test
    void disposePreservesCursorChangedByButtonOwner() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronToolDragController controller = new NeuronToolDragController(
                    new EditorState(1.0, 0.0, 0.0),
                    new WorkspaceView(),
                    (NeuronType ignored, javafx.geometry.Point2D point) -> { }
            );
            Button button = new Button("Neuron");
            button.setCursor(Cursor.WAIT);
            controller.configureToolbarButtons(button, null);
            button.setCursor(Cursor.TEXT);

            controller.dispose();

            assertEquals(Cursor.TEXT, button.getCursor());
        });
    }

    /** Створює подію відпускання миші для перевірки від'єднання обробника кнопки. */
    private static MouseEvent mouseReleased() {
        return new MouseEvent(
                MouseEvent.MOUSE_RELEASED, 0.0, 0.0, 0.0, 0.0,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                false, false, false, false, false, true, null
        );
    }
}
