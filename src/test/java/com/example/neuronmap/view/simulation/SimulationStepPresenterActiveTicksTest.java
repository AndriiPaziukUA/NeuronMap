package com.example.neuronmap.view.simulation;

import com.example.neuronmap.controller.PulseAnimationController;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє передавання лічильників із результату такту до числових індикаторів нейронів. */
final class SimulationStepPresenterActiveTicksTest {

    /**
     * Перевіряє оновлення всіх лічильників під час застосування такту та їх очищення після зупинки.
     *
     * @throws Exception якщо синхронне виконання дії в JavaFX-потоці завершується помилкою.
     */
    @Test
    void presenterAppliesAndClearsRemainingTicks() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            NeuronView activeView = createView("presenter-active-neuron");
            NeuronView idleView = createView("presenter-idle-neuron");
            Map<String, NeuronView> neuronViews = Map.of(
                    activeView.model().id(), activeView,
                    idleView.model().id(), idleView
            );
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace,
                    neuronViews,
                    () -> neuronViews.values().forEach(view -> view.refreshVisuals(false)),
                    new PulseAnimationController(workspace)
            );
            Label activeTicksLabel = (Label) activeView.getChildren().get(2);
            Label idleTicksLabel = (Label) idleView.getChildren().get(2);

            try {
                presenter.apply(new SimulationStep(
                        BigInteger.ZERO,
                        Map.of(),
                        Set.of(),
                        Set.of(),
                        Map.of(),
                        Map.of(activeView.model().id(), 2)
                ));

                assertEquals("2", activeTicksLabel.getText());
                assertEquals("0", idleTicksLabel.getText());

                presenter.clearRuntime();

                assertEquals("0", activeTicksLabel.getText());
                assertEquals("0", idleTicksLabel.getText());
            } finally {
                presenter.clearRuntime();
                workspace.dispose();
            }
        });
    }

    /**
     * Перевіряє, що лічильник миттєво показує повну тривалість, коли видимий вхідний сигнал досягає порога.
     *
     * @throws Exception якщо синхронне виконання дії в JavaFX-потоці завершується помилкою.
     */
    @Test
    void presenterShowsCounterAsSoonAsIncomingSignalCrossesThreshold() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            NeuronView targetView = createView("presenter-pending-activation");
            targetView.model().setActivationThreshold(3);
            targetView.model().setActiveTicks(4);
            Map<String, NeuronView> neuronViews = Map.of(targetView.model().id(), targetView);
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace,
                    neuronViews,
                    () -> targetView.refreshVisuals(false),
                    new PulseAnimationController(workspace)
            );
            Label inputSignalLabel = (Label) targetView.getChildren().get(1);
            Label remainingTicksLabel = (Label) targetView.getChildren().get(2);

            try {
                presenter.apply(new SimulationStep(
                        BigInteger.ZERO,
                        Map.of(),
                        Set.of(),
                        Set.of(),
                        Map.of(targetView.model().id(), 3),
                        Map.of(),
                        Set.of(targetView.model().id())
                ));

                assertEquals("3", inputSignalLabel.getText());
                assertEquals("4", remainingTicksLabel.getText());
            } finally {
                presenter.clearRuntime();
                workspace.dispose();
            }
        });
    }

    /** Створює просте подання нейрона для перевірки індикатора активних тактів. */
    private static NeuronView createView(String id) {
        Neuron neuron = new Neuron(id, NeuronType.EXCITATORY, 0);
        return new NeuronView(new NeuronPresentation(neuron, 40.0, 50.0, 0.0));
    }
}
