package com.example.neuronmap.view.simulation;

import com.example.neuronmap.controller.PulseAnimationController;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.simulation.SimulationSession;
import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.shape.Circle;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що порти структурних учасників показують той самий переможний результат, що й симуляція. */
final class SimulationStepPresenterStrongestNeuronTest {

    /** Перевіряє жовтий порт переможця та червоний порт слабшого отримувача одночасного сигналу. */
    @Test
    void highlightsOnlyStrongestReceiverAsSufficient() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
            Neuron weakerMember = model.createNeuron(NeuronType.EXCITATORY, 100.0, 0.0);
            Neuron strongerMember = model.createNeuron(NeuronType.EXCITATORY, 200.0, 0.0);
            assertTrue(model.addStructureMember(structure.id(), weakerMember.id()));
            assertTrue(model.addStructureMember(structure.id(), strongerMember.id()));
            model.structure(structure.id()).settings().setStrongestNeuronOnly(true);

            Neuron weakerSource = model.createNeuron(NeuronType.EXCITATORY, 0.0, 100.0);
            Neuron strongerSource = model.createNeuron(NeuronType.EXCITATORY, 100.0, 100.0);
            weakerSource.setSignalStrength(2);
            strongerSource.setSignalStrength(7);
            assertTrue(model.createConnection(weakerSource.id(), weakerMember.id()));
            assertTrue(model.createConnection(strongerSource.id(), strongerMember.id()));

            SimulationSession session = SimulationSession.manual(model, weakerSource.id());
            assertTrue(session.queueManualStart(strongerSource.id()));
            SimulationStep step = Objects.requireNonNull(session.nextStep());
            assertTrue(step.nextActivatedNeuronIds().contains(strongerMember.id()));
            assertFalse(step.nextActivatedNeuronIds().contains(weakerMember.id()));

            WorkspaceView workspace = new WorkspaceView();
            NeuronView weakerView = new NeuronView(model.presentation(weakerMember.id()));
            NeuronView strongerView = new NeuronView(model.presentation(strongerMember.id()));
            Map<String, NeuronView> views = Map.of(
                    weakerMember.id(), weakerView,
                    strongerMember.id(), strongerView);
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace,
                    views,
                    () -> views.values().forEach(view -> view.refreshVisuals(false)),
                    new PulseAnimationController(workspace));
            try {
                presenter.applyWithoutAnimations(step);

                Circle weakerPort = (Circle) weakerView.getChildren().get(3);
                Circle strongerPort = (Circle) strongerView.getChildren().get(3);
                assertTrue(weakerPort.getStyleClass().contains("port-input-insufficient"));
                assertTrue(strongerPort.getStyleClass().contains("port-input-sufficient"));
            } finally {
                presenter.clearRuntime();
                workspace.dispose();
            }
        });
    }
}
