package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronDragPreviewView;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє активацію інструмента структури, підсвічування кнопки й появу попереднього перегляду. */
final class NeuronToolDragControllerStructureTest {

    /** Перевіряє, що зареєстрований структурний інструмент активується та показує прев’ю під курсором. */
    @Test
    void structureToolActivatesHighlightsAndShowsPreview() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            EditorState state = new EditorState(1.0, 0.0, 0.0);
            WorkspaceView workspace = new WorkspaceView();
            Scene scene = new Scene(workspace.node(), 600.0, 400.0);
            assertSame(scene, workspace.node().getScene(), "Робоча область має бути прикріплена до тестової сцени");
            Button structureButton = new Button("Структура");
            structureButton.getStyleClass().add("tool-button");
            NeuronToolDragController controller = new NeuronToolDragController(
                    state, workspace, (_, _) -> { });

            controller.configureToolbarButtons(Map.of(NeuronType.STRUCTURE, structureButton));
            controller.install();
            controller.setPlacementMode(NeuronType.STRUCTURE);

            assertTrue(structureButton.getStyleClass().contains("tool-button-active"),
                    "Активна кнопка структури має отримувати стандартний клас підсвічування");

            workspace.node().fireEvent(new MouseEvent(
                    MouseEvent.MOUSE_MOVED, 180.0, 140.0, 180.0, 140.0, MouseButton.NONE, 0,
                    false, false, false, false, false, false, false, false, false, false, null));
            assertEquals(1, workspace.overlayLayer().getChildren().size(),
                    "Під час переміщення вказівника має з’являтися один вузол прев’ю");
            assertInstanceOf(NeuronDragPreviewView.class, workspace.overlayLayer().getChildren().getFirst());

            controller.dispose();
            workspace.dispose();
        });
    }

}
