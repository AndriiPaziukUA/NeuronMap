package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє заборону ручки обертання для структури з відкритою шухлядкою. */
final class NeuronRotationControllerStructureDrawerTest {

    private static final String STRUCTURE_ID = "rotation-structure-drawer-test";

    /** Перевіряє, що ручка доступна для закритої структури, але приховується при відкритій шухлядці. */
    @Test
    void rotationHandleIsHiddenWhileStructureDrawerIsExpanded() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            model.addNeuron(STRUCTURE_ID, NeuronType.STRUCTURE, 0, 120.0, 100.0, 0.0);
            NeuronView structureView = new NeuronView(model.presentation(STRUCTURE_ID));
            WorkspaceView workspace = new WorkspaceView();
            workspace.nodeLayer().getChildren().add(structureView);
            RotationHandleView handle = new RotationHandleView();
            workspace.overlayLayer().getChildren().add(handle);
            new Scene(new StackPane(workspace.node()), 800.0, 600.0);
            workspace.node().resize(800.0, 600.0);
            workspace.node().layout();

            NeuronRotationController controller = new NeuronRotationController(
                    new NeuronService(model),
                    new EditorState(1.0, 0.0, 0.0),
                    workspace,
                    Map.of(STRUCTURE_ID, structureView),
                    Map.of(STRUCTURE_ID, handle),
                    new LocalizationService(Locale.forLanguageTag("uk")),
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { }
            );

            try {
                controller.installHandleHandlers(structureView, handle);
                controller.show(STRUCTURE_ID);
                assertTrue(handle.isVisible(), "Закрита структура повинна дозволяти обертання");

                structureView.toggleStructureDrawer();
                controller.show(STRUCTURE_ID);
                assertFalse(handle.isVisible(), "Відкрита шухлядка повинна блокувати появу ручки");

                structureView.toggleStructureDrawer();
                controller.show(STRUCTURE_ID);
                assertTrue(handle.isVisible(), "Після закриття шухлядки обертання знову доступне");
            } finally {
                controller.dispose();
                workspace.dispose();
            }
        });
    }
}
