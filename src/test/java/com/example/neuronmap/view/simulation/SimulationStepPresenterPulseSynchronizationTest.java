package com.example.neuronmap.view.simulation;

import com.example.neuronmap.controller.PulseAnimationController;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.simulation.PulseAnimationView;
import javafx.scene.Scene;
import javafx.scene.shape.Polygon;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що завершення одного такту не блокує анімацію сигналу наступного нейрона в ланцюгу. */
final class SimulationStepPresenterPulseSynchronizationTest {

    /**
     * Перевіряє завершення вхідного імпульсу перед запуском вихідного імпульсу активованого нейрона.
     *
     * @throws Exception якщо виконання дії в JavaFX-потоці завершується помилкою.
     */
    @Test
    void outgoingPulseCanBeRepeatedAfterPriorTickAnimationsAreCompleted() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron first = model.createNeuron(NeuronType.EXCITATORY, 50.0, 100.0);
            Neuron second = model.createNeuron(NeuronType.EXCITATORY, 250.0, 100.0);
            Neuron third = model.createNeuron(NeuronType.EXCITATORY, 450.0, 100.0);
            assertTrue(model.createConnection(first.id(), second.id()));
            assertTrue(model.createConnection(second.id(), third.id()));

            WorkspaceView workspace = new WorkspaceView();
            new Scene(workspace.node(), 800.0, 600.0);
            Map<String, NeuronView> neuronViews = new LinkedHashMap<>();
            for (Neuron neuron : model.neurons()) {
                NeuronView neuronView = new NeuronView(model.presentation(neuron.id()));
                neuronViews.put(neuron.id(), neuronView);
                workspace.nodeLayer().getChildren().add(neuronView);
            }
            for (Connection connection : model.connections()) {
                ConnectionView connectionView = new ConnectionView(connection, neuronViews::get, model::neuron);
                workspace.edgeLayer().getChildren().add(connectionView);
            }

            PulseAnimationController pulseController = new PulseAnimationController(workspace);
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace,
                    neuronViews,
                    () -> neuronViews.values().forEach(view -> view.refreshVisuals(false)),
                    pulseController
            );
            List<PulseAnimationView> previousPulses;
            try {
                presenter.apply(new SimulationStep(
                        BigInteger.ZERO,
                        Map.of(second.id(), first.signalWeight()),
                        Set.of(second.id()),
                        Set.of(first.id(), second.id()),
                        Map.of(second.id(), first.signalWeight(), third.id(), second.signalWeight()),
                        Map.of(),
                        Set.of(second.id(), third.id())
                ));
                assertEquals(2, workspace.pulseLayer().getChildren().size());
                previousPulses = workspace.pulseLayer().getChildren().stream()
                        .map(PulseAnimationView.class::cast)
                        .toList();

                presenter.completeAnimationsAtTickBoundary();

                assertTrue(previousPulses.stream().allMatch(PulseAnimationView::isFinished));
                assertTrue(workspace.pulseLayer().getChildren().isEmpty());
                for (PulseAnimationView previousPulse : previousPulses) {
                    Polygon arrivedArrow = (Polygon) previousPulse.getChildren().get(0);
                    double tipX = arrivedArrow.getPoints().get(0).doubleValue();
                    double tipY = arrivedArrow.getPoints().get(1).doubleValue();
                    boolean reachedSecond = Math.abs(neuronViews.get(second.id()).inputPoint().getX() - tipX) < 0.001
                            && Math.abs(neuronViews.get(second.id()).inputPoint().getY() - tipY) < 0.001;
                    boolean reachedThird = Math.abs(neuronViews.get(third.id()).inputPoint().getX() - tipX) < 0.001
                            && Math.abs(neuronViews.get(third.id()).inputPoint().getY() - tipY) < 0.001;
                    assertTrue(reachedSecond || reachedThird);
                }

                presenter.apply(new SimulationStep(
                        BigInteger.ONE,
                        Map.of(second.id(), first.signalWeight()),
                        Set.of(second.id()),
                        Set.of(second.id()),
                        Map.of(third.id(), second.signalWeight()),
                        Map.of(),
                        Set.of(third.id())
                ));

                assertEquals(1, workspace.pulseLayer().getChildren().size());
                PulseAnimationView nextPulse = (PulseAnimationView) workspace.pulseLayer().getChildren().get(0);
                assertTrue(previousPulses.stream().noneMatch(previousPulse -> previousPulse == nextPulse));
            } finally {
                presenter.clearRuntime();
                workspace.dispose();
            }
        });
    }
}
