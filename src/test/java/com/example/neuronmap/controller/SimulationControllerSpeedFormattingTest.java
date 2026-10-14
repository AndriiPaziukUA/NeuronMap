package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє форматування локалізованого повідомлення контролера після зміни швидкості активної симуляції.
 */
final class SimulationControllerSpeedFormattingTest {

    private static final double MIN_TICK_MILLIS = 10.0;
    private static final double MAX_TICK_MILLIS = 500.0;

    /** Запускає JavaFX перед тестом, що створює таймери й подання симуляції. */
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

    /** Перевіряє, що локалізоване повідомлення використовує спільний стабільний формат швидкості. */
    @Test
    void speedChangedStatusUsesSharedDurationFormatting() throws Exception {
        AtomicReference<String> status = new AtomicReference<>();
        LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));

        runOnFxThread(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron source = model.createNeuron(NeuronType.EXCITATORY, 50.0, 50.0);
            Neuron target = model.createNeuron(NeuronType.EXCITATORY, 150.0, 50.0);
            assertTrue(model.createConnection(source.id(), target.id()));

            WorkspaceView workspace = new WorkspaceView();
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace,
                    Map.of(),
                    () -> { },
                    new PulseAnimationController(workspace)
            );
            SimulationController controller = new SimulationController(
                    new NeuronService(model),
                    new SimulationService(),
                    presenter,
                    () -> { },
                    status::set,
                    ignored -> { },
                    ignored -> { },
                    100.0,
                    MIN_TICK_MILLIS,
                    MAX_TICK_MILLIS,
                    localization
            );

            try {
                controller.emitPulse(source.id());
                controller.setTickDurationMillis(125.5);

                assertEquals(
                        "Швидкість імпульсів: 125.5 мс/такт.",
                        status.get()
                );
            } finally {
                controller.shutdown();
            }
        });
    }

    /** Виконує дію в JavaFX Application Thread і повертає будь-яку помилку в основний потік тесту. */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }

        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
