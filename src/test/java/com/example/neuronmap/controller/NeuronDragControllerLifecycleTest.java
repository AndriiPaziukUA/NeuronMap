package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Перевіряє, що незавершений жест перетягування можна скасувати без збереження карти. */
final class NeuronDragControllerLifecycleTest {

    /** Перевіряє очищення стану перетягування та повернення курсора до завершення життєвого циклу. */
    @Test
    void cancelDraggingEndsGestureAndRestoresCursor() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            NeuronService neuronService = new NeuronService(model);
            GroupService groupService = new GroupService(model);
            EditorState state = new EditorState(1.0, 0.0, 0.0);
            WorkspaceView workspace = new WorkspaceView();
            SelectionController selectionController = new SelectionController(
                    groupService,
                    state,
                    () -> { },
                    () -> { },
                    ignored -> { }
            );
            NeuronDragController controller = new NeuronDragController(
                    neuronService,
                    groupService,
                    selectionController,
                    state,
                    workspace,
                    () -> { },
                    ignored -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { }
            );
            var neuron = model.createNeuron(NeuronType.EXCITATORY, 20.0, 30.0);
            NeuronView neuronView = new NeuronView(model.presentation(neuron.id()));
            neuronView.beginDrag(10.0, 10.0, false, false);
            workspace.node().setCursor(javafx.scene.Cursor.CLOSED_HAND);

            controller.cancelDragging(neuronView);

            assertFalse(neuronView.isDragging());
            assertEquals(javafx.scene.Cursor.DEFAULT, workspace.node().getCursor());
        });
    }
}
