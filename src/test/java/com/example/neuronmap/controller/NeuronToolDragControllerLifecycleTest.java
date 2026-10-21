package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє життєвий цикл джерел перетягування та постійного режиму розміщення нейронів. */
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
                    (NeuronType ignored, Point2D point) -> drops.incrementAndGet()
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
            workspace.dispose();
        });
    }

    /** Перевіряє, що dispose не перезаписує курсор, змінений власником кнопки після підключення. */
    @Test
    void disposePreservesCursorChangedByButtonOwner() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            NeuronToolDragController controller = new NeuronToolDragController(
                    new EditorState(1.0, 0.0, 0.0),
                    workspace,
                    (NeuronType ignored, Point2D point) -> { }
            );
            Button button = new Button("Neuron");
            button.setCursor(Cursor.WAIT);
            controller.configureToolbarButtons(button, null);
            button.setCursor(Cursor.TEXT);

            controller.dispose();

            assertEquals(Cursor.TEXT, button.getCursor());
            workspace.dispose();
        });
    }

    /** Перевіряє стійкість попереднього перегляду й взаємовиключне виділення кнопок для довільного набору типів. */
    @Test
    void placementPreviewPersistsAndOnlyTheSelectedButtonIsHighlighted() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            new Scene(workspace.node(), 500.0, 400.0);
            NeuronToolDragController controller = new NeuronToolDragController(
                    new EditorState(1.0, 0.0, 0.0), workspace,
                    (NeuronType ignored, Point2D point) -> { }
            );
            Button excitatory = new Button("Excitatory");
            Button inhibitory = new Button("Inhibitory");
            controller.configureToolbarButtons(Map.of(
                    NeuronType.EXCITATORY, excitatory,
                    NeuronType.INHIBITORY, inhibitory
            ));
            controller.install();
            try {
                controller.setPlacementMode(NeuronType.EXCITATORY);
                Event.fireEvent(workspace.node(), mouseMoved(100.0, 120.0));
                assertTrue(excitatory.getStyleClass().contains("tool-button-active"));
                assertFalse(inhibitory.getStyleClass().contains("tool-button-active"));
                assertEquals(1, workspace.overlayLayer().getChildren().size());
                assertTrue(workspace.overlayLayer().getChildren().getFirst().isMouseTransparent());

                Event.fireEvent(workspace.node(), mouseMoved(180.0, 220.0));
                assertEquals(1, workspace.overlayLayer().getChildren().size(),
                        "Рух курсора має переміщувати попередній перегляд, а не видаляти його");

                controller.setPlacementMode(NeuronType.INHIBITORY);
                assertFalse(excitatory.getStyleClass().contains("tool-button-active"));
                assertTrue(inhibitory.getStyleClass().contains("tool-button-active"));
                Event.fireEvent(workspace.node(), mouseMoved(250.0, 270.0));
                assertEquals(1, workspace.overlayLayer().getChildren().size());

                controller.setPlacementMode(null);
                assertFalse(excitatory.getStyleClass().contains("tool-button-active"));
                assertFalse(inhibitory.getStyleClass().contains("tool-button-active"));
                assertTrue(workspace.overlayLayer().getChildren().isEmpty());
            } finally {
                controller.dispose();
                workspace.dispose();
            }
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

    /** Створює подію руху вказівника для перевірки переміщення попереднього перегляду. */
    private static MouseEvent mouseMoved(double x, double y) {
        return new MouseEvent(
                MouseEvent.MOUSE_MOVED, x, y, x, y,
                MouseButton.NONE, 0,
                false, false, false, false,
                false, false, false, false, false, true, null
        );
    }
}
