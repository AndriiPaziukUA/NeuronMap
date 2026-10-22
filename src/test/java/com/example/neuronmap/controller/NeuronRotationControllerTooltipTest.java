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
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Перевіряє, що контролер приховує підказку на час обертання та відновлює її після жесту. */
final class NeuronRotationControllerTooltipTest {

    private static final String NEURON_ID = "rotation-tooltip-test";

    /** Перевіряє приховування підказки під час перетягування та її відновлення після відпускання кнопки миші. */
    @Test
    void tooltipIsHiddenDuringRotationAndRestoredAfterRelease() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            model.addNeuron(NEURON_ID, NeuronType.EXCITATORY, 0, 120.0, 100.0, 0.0);
            NeuronView neuronView = new NeuronView(model.presentation(NEURON_ID));
            neuronView.relocate(120.0, 100.0);

            WorkspaceView workspace = new WorkspaceView();
            workspace.nodeLayer().getChildren().add(neuronView);
            RotationHandleView handle = new RotationHandleView();
            workspace.overlayLayer().getChildren().add(handle);
            new Scene(new StackPane(workspace.node()), 800.0, 600.0);
            workspace.node().resize(800.0, 600.0);
            workspace.node().layout();

            EditorState state = new EditorState(1.0, 0.0, 0.0);
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            NeuronRotationController controller = new NeuronRotationController(
                    new NeuronService(model),
                    state,
                    workspace,
                    Map.of(NEURON_ID, neuronView),
                    Map.of(NEURON_ID, handle),
                    localization,
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { }
            );
            controller.installHandleHandlers(neuronView, handle);
            Tooltip tooltip = handle.getTooltip();

            try {
                fireMouseEvent(handle, MouseEvent.MOUSE_PRESSED, 180.0, 150.0);
                assertNull(handle.getTooltip());
                assertEquals("Rotate", handle.getAccessibleText());
                assertEquals(EditorState.Mode.ROTATE_NEURON, state.mode());

                fireMouseEvent(handle, MouseEvent.MOUSE_DRAGGED, 220.0, 130.0);
                assertNull(handle.getTooltip(), "Підказка не повинна з'являтися під час перетягування ручки");

                fireMouseEvent(handle, MouseEvent.MOUSE_RELEASED, 220.0, 130.0);
                assertSame(tooltip, handle.getTooltip());
                assertEquals("Rotate", handle.getTooltip().getText());
                assertEquals(EditorState.Mode.IDLE, state.mode());
            } finally {
                controller.dispose();
                workspace.dispose();
            }
        });
    }

    /** Надсилає подію основної кнопки миші без прив'язки тесту до фізичного пристрою введення. */
    private static void fireMouseEvent(
            RotationHandleView handle,
            EventType<MouseEvent> type,
            double sceneX,
            double sceneY
    ) {
        boolean primaryButtonDown = type == MouseEvent.MOUSE_PRESSED || type == MouseEvent.MOUSE_DRAGGED;
        MouseEvent event = new MouseEvent(
                type,
                sceneX,
                sceneY,
                sceneX,
                sceneY,
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
                false,
                null
        );
        Event.fireEvent(handle, event);
    }
}
