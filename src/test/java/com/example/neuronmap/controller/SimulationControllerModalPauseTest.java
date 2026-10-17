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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє незалежність паузи модального інтерфейсу від індикатора ручної паузи симуляції. */
final class SimulationControllerModalPauseTest {

    /** Запускає JavaFX перед тестом, що створює таймери та анімації симуляції. */
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

    /** Перевіряє, що модальна пауза не змінює стан, який відображає кнопка паузи на панелі інструментів. */
    @Test
    void modalPauseDoesNotMasqueradeAsManualPause() throws Exception {
        runOnFxThread(() -> {
            NeuronMapModel model = new NeuronMapModel();
            var neuron = model.createNeuron(NeuronType.EXCITATORY, 50.0, 50.0);
            WorkspaceView workspace = new WorkspaceView();
            List<Boolean> publishedPausedStates = new ArrayList<>();
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace, Map.of(), () -> { }, new PulseAnimationController(workspace));
            SimulationController controller = new SimulationController(
                    new NeuronService(model), new SimulationService(), presenter,
                    () -> { }, ignored -> { }, publishedPausedStates::add, ignored -> { },
                    100.0, 10.0, 500.0);
            try {
                controller.emitPulse(neuron.id());
                int stateCountBeforeModal = publishedPausedStates.size();
                assertTrue(controller.pauseForModal());
                assertFalse(controller.isPaused());
                assertEquals(stateCountBeforeModal, publishedPausedStates.size());

                controller.resumeFromModal();

                assertFalse(controller.isPaused());
                assertEquals(stateCountBeforeModal, publishedPausedStates.size());

                controller.pauseOrResume();
                assertTrue(controller.isPaused());
                assertEquals(Boolean.TRUE, publishedPausedStates.getLast());
            } finally {
                controller.shutdown();
            }
        });
    }

    /** Виконує перевірку на потоці JavaFX і передає помилки в потік тесту. */
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
