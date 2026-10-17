package com.example.neuronmap.controller;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що зупинка сигналів очищує runtime-стан і не запускає збереження карти. */
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
        runOnFxThread(() -> {
            NeuronMapModel model = new NeuronMapModel();
            var neuron = model.createNeuron(NeuronType.EXCITATORY, 50.0, 50.0);
            WorkspaceView workspace = new WorkspaceView();
            AtomicInteger saveCalls = new AtomicInteger();
            AtomicReference<String> latestStatus = new AtomicReference<>();
            List<Boolean> pausedStates = new ArrayList<>();
            List<Boolean> activityStates = new ArrayList<>();
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace, java.util.Map.of(), () -> { }, new PulseAnimationController(workspace));
            SimulationController controller = new SimulationController(
                    new NeuronService(model), new SimulationService(), presenter,
                    saveCalls::incrementAndGet, latestStatus::set, pausedStates::add, activityStates::add,
                    500.0, 10.0, 500.0);

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
            }
        });
    }

    /** Виконує перевірку на JavaFX Application Thread і передає помилки в основний потік тесту. */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure.get() != null) {
            throw new AssertionError("JavaFX test failed", failure.get());
        }
    }
}
