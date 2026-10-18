package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що закриття контролера зв'язків скасовує режими та від'єднує фільтри полотна. */
final class ConnectionControllerLifecycleTest {

    /** Перевіряє, що dispose прибирає попередній перегляд, скидає режим і безпечний при повторному виклику. */
    @Test
    void disposeCancelsConnectionCreationAndIsIdempotent() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron sourceModel = model.createNeuron(NeuronType.EXCITATORY, 100.0, 80.0);
            Neuron targetModel = model.createNeuron(NeuronType.INHIBITORY, 240.0, 160.0);
            NeuronView source = new NeuronView(model.presentation(sourceModel.id()));
            NeuronView target = new NeuronView(model.presentation(targetModel.id()));
            WorkspaceView workspace = new WorkspaceView();
            workspace.nodeLayer().getChildren().addAll(source, target);
            Scene scene = new Scene(new StackPane(workspace.node()), 640.0, 480.0);
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            EditorState state = new EditorState(1.0, 0.0, 0.0);
            ConnectionService service = new ConnectionService(model);
            ConnectionController controller = new ConnectionController(
                    service, state, workspace,
                    new HashMap<>(Map.of(sourceModel.id(), source, targetModel.id(), target)),
                    () -> { }, () -> { }, ignored -> { }, new LocalizationService(Locale.ENGLISH));
            controller.install();
            controller.install();
            controller.beginCreate(sourceModel.id());
            assertEquals(EditorState.Mode.CREATE_CONNECTION, state.mode());
            assertEquals(1, workspace.overlayLayer().getChildren().size());

            controller.dispose();
            controller.dispose();

            assertEquals(EditorState.Mode.IDLE, state.mode());
            assertEquals(Cursor.DEFAULT, workspace.node().getCursor());
            assertTrue(workspace.overlayLayer().getChildren().isEmpty());
        });
    }
}
