package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.simulation.PulseAnimationView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє негайну активацію нейрона, узгодження імпульсів і очищення runtime-стану під час зупинки. */
final class SimulationControllerStopSignalsTest {

    /** Запускає JavaFX перед тестами, які створюють таймери та анімації симуляції. */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /** Перевіряє, що зупинка активного сигналу не зберігає карту, завершує сеанс і оновлює стан контролів. */
    @Test
    void stoppingSignalsClearsRuntimeWithoutSavingMap() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            var neuron = model.createNeuron(NeuronType.EXCITATORY, 50.0, 50.0);
            WorkspaceView workspace = new WorkspaceView();
            AtomicInteger saveCalls = new AtomicInteger();
            AtomicReference<String> latestStatus = new AtomicReference<>();
            List<Boolean> pausedStates = new ArrayList<>();
            List<Boolean> activityStates = new ArrayList<>();
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace, Map.of(), () -> { }, new PulseAnimationController(workspace));
            SimulationController controller = new SimulationController(
                    new NeuronService(model), new SimulationService(), presenter,
                    saveCalls::incrementAndGet, latestStatus::set, pausedStates::add, activityStates::add,
                    500.0, 10.0, 500.0, new LocalizationService(Locale.ENGLISH));

            try {
                controller.emitPulse(neuron.id());
                assertTrue(controller.hasActiveSimulation());
                assertEquals(0, saveCalls.get());

                controller.stopSignals();

                assertFalse(controller.hasActiveSimulation());
                assertFalse(controller.isPaused());
                assertEquals(0, saveCalls.get());
                assertEquals(Boolean.FALSE, pausedStates.get(pausedStates.size() - 1));
                assertEquals(Boolean.FALSE, activityStates.get(activityStates.size() - 1));
                assertNotNull(latestStatus.get());
            } finally {
                controller.shutdown();
                workspace.dispose();
            }
        });
    }

    /** Перевіряє негайний запуск посеред такту, залишок часу анімації та синхронне оновлення індикаторів. */
    @Test
    void manualPulseDuringTickUpdatesNextNeuronImmediatelyAndUsesRemainingTime() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron firstSource = model.createNeuron(NeuronType.EXCITATORY, 50.0, 50.0);
            Neuron manualSource = model.createNeuron(NeuronType.EXCITATORY, 250.0, 50.0);
            Neuron target = model.createNeuron(NeuronType.EXCITATORY, 450.0, 50.0);
            manualSource.setActiveTicks(4);
            manualSource.setActivationThreshold(100);
            target.setActivationThreshold(100);
            assertTrue(model.createConnection(firstSource.id(), manualSource.id()));
            assertTrue(model.createConnection(manualSource.id(), target.id()));

            WorkspaceView workspace = new WorkspaceView();
            new Scene(workspace.node(), 800.0, 600.0);
            Map<String, NeuronView> neuronViews = new HashMap<>();
            for (Neuron neuron : model.neurons()) {
                neuronViews.put(neuron.id(), new NeuronView(model.presentation(neuron.id())));
            }
            workspace.nodeLayer().getChildren().addAll(neuronViews.values());
            List<ConnectionView> connectionViews = new ArrayList<>();
            for (Connection connection : model.connections()) {
                ConnectionView connectionView = new ConnectionView(connection, neuronViews::get, model::neuron);
                connectionViews.add(connectionView);
            }
            workspace.edgeLayer().getChildren().addAll(connectionViews);

            PulseAnimationController pulseController = new PulseAnimationController(workspace);
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace,
                    neuronViews,
                    () -> neuronViews.values().forEach(view -> view.refreshVisuals(false)),
                    pulseController
            );
            SimulationController controller = new SimulationController(
                    new NeuronService(model), new SimulationService(), presenter,
                    () -> { }, ignored -> { }, ignored -> { }, ignored -> { },
                    600.0, 10.0, 600.0, new LocalizationService(Locale.ENGLISH));

            controller.emitPulse(firstSource.id());
            PauseTransition waitForMidTick = new PauseTransition(Duration.millis(150.0));
            waitForMidTick.setOnFinished(event -> {
                try {
                    controller.emitPulse(manualSource.id());

                    Label targetInput = (Label) neuronViews.get(target.id()).getChildren().get(1);
                    Label manualRemainingTicks = (Label) neuronViews.get(manualSource.id()).getChildren().get(2);
                    assertEquals("1", targetInput.getText());
                    assertEquals("3", manualRemainingTicks.getText());
                    assertEquals(2, workspace.pulseLayer().getChildren().size());
                    PulseAnimationView immediatePulse = workspace.pulseLayer().getChildren().stream()
                            .map(PulseAnimationView.class::cast)
                            .filter(pulse -> pulse.getTickDurationMillis() < 600.0)
                            .findFirst()
                            .orElseThrow();
                    assertTrue(immediatePulse.getTickDurationMillis() > 350.0);
                    assertTrue(immediatePulse.getTickDurationMillis() < 550.0);
                } catch (Throwable throwable) {
                    failure.set(throwable);
                } finally {
                    controller.shutdown();
                    workspace.dispose();
                    completed.countDown();
                }
            });
            waitForMidTick.play();
        });

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError("JavaFX test failed", failure.get());
        }
    }
}
