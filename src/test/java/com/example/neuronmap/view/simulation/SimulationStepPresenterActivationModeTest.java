package com.example.neuronmap.view.simulation;

import com.example.neuronmap.controller.PulseAnimationController;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.simulation.SimulationSession;
import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє узгодженість числового індикатора та кольору вхідного порту з режимом активації. */
final class SimulationStepPresenterActivationModeTest {

    /** Перевіряє, що достатній знаковий лічильник відображається числом і позначає порт як достатній. */
    @Test
    void countModeUsesSimulationThresholdResultForInputPortColor() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron firstSource = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
            firstSource.setSignalStrength(7);
            Neuron secondSource = model.createNeuron(NeuronType.EXCITATORY, 100.0, 0.0);
            Neuron thirdSource = model.createNeuron(NeuronType.EXCITATORY, 200.0, 0.0);
            Neuron target = model.createNeuron(NeuronType.EXCITATORY, 300.0, 0.0);
            target.setParameters(new NeuronParameters(1, 33, 1, 1, NeuronActivationMode.SIGNAL_COUNT));
            assertTrue(model.createConnection(firstSource.id(), target.id()));
            assertTrue(model.createConnection(secondSource.id(), target.id()));
            assertTrue(model.createConnection(thirdSource.id(), target.id()));

            SimulationStep step = Objects.requireNonNull(
                    SimulationSession.manual(model, firstSource.id()).nextStep(),
                    "Для тестового сценарію має бути доступний перший крок симуляції");
            assertEquals(Integer.valueOf(1), step.nextInputSums().get(target.id()));
            assertTrue(step.nextActivatedNeuronIds().contains(target.id()));

            WorkspaceView workspace = new WorkspaceView();
            NeuronView targetView = new NeuronView(model.presentation(target.id()));
            Map<String, NeuronView> views = Map.of(target.id(), targetView);
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace,
                    views,
                    () -> views.values().forEach(view -> view.refreshVisuals(false)),
                    new PulseAnimationController(workspace)
            );
            try {
                presenter.applyWithoutAnimations(step);

                assertEquals("1", ((Label) targetView.getChildren().get(1)).getText());
                Circle inputPort = (Circle) targetView.getChildren().get(3);
                assertTrue(inputPort.getStyleClass().contains("port-input-sufficient"));
            } finally {
                presenter.clearRuntime();
                workspace.dispose();
            }
        });
    }
}
