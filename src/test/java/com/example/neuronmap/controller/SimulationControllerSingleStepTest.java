package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє межу однотактного виконання, повторне використання показаного такту та звичайну паузу. */
final class SimulationControllerSingleStepTest {

    private static final double TICK_MILLIS = 220.0;
    private static final double ASYNC_MARGIN_MILLIS = 120.0;

    /** Запускає JavaFX перед тестами, що керують часовими шкалами симуляції. */
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

    /**
     * Перевіряє, що покроковий режим показує наступний стан без його анімації, а відновлення запускає її без дублювання такту.
     */
    @Test
    void oneStepPresentsNextTickBeforeAnimationAndResumeDoesNotReprocessIt() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Fixture fixture;
            try {
                fixture = createFixture();
            } catch (Throwable throwable) {
                failure.set(throwable);
                completed.countDown();
                return;
            }
            try {
                fixture.controller().emitPulse(fixture.sourceId());
                schedule(fixture, 55.0, () -> {
                    fixture.controller().pauseOrResume();
                    assertTrue(fixture.controller().isPaused());
                    assertEquals(1, fixture.tickCount().get());

                    fixture.controller().continueForOneTick();
                    assertTrue(fixture.controller().isSingleStepRunning());

                    schedule(fixture, TICK_MILLIS + ASYNC_MARGIN_MILLIS, () -> {
                        assertTrue(fixture.controller().isPaused(),
                                "Після однотактного виконання ручна пауза має залишатися активною");
                        assertFalse(fixture.controller().isSingleStepRunning());
                        assertEquals(2, fixture.tickCount().get(),
                                "На межі кроку має обчислитися й відобразитися початок наступного такту");
                        assertTrue(fixture.workspace().pulseLayer().getChildren().isEmpty(),
                                "Анімація наступного такту не починається до відновлення симуляції");

                        fixture.controller().pauseOrResume();

                        assertFalse(fixture.controller().isPaused());
                        assertEquals(2, fixture.tickCount().get(),
                                "Відновлення не повинно повторно обчислювати вже показаний такт");
                        assertFalse(fixture.workspace().pulseLayer().getChildren().isEmpty(),
                                "Відновлення має запустити анімацію раніше показаного такту");

                        schedule(fixture, TICK_MILLIS + ASYNC_MARGIN_MILLIS, () -> {
                            assertEquals(3, fixture.tickCount().get(),
                                    "Наступний такт обчислюється лише після завершення анімації показаного такту");
                            finish(fixture, completed);
                        }, failure, completed);
                    }, failure, completed);
                }, failure, completed);
            } catch (Throwable throwable) {
                failAndFinish(fixture, throwable, failure, completed);
            }
        });

        assertTrue(completed.await(5, TimeUnit.SECONDS), "Перевірка покрокового виконання не завершилася");
        rethrowFailure(failure);
    }

    /** Перевіряє, що звичайна пауза може перервати однотактний інтервал і зупинити його до нового натискання. */
    @Test
    void ordinaryPauseInterruptsSingleStepAndCanLaterResumeContinuously() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Fixture fixture;
            try {
                fixture = createFixture();
            } catch (Throwable throwable) {
                failure.set(throwable);
                completed.countDown();
                return;
            }
            try {
                fixture.controller().emitPulse(fixture.sourceId());
                schedule(fixture, 45.0, () -> {
                    fixture.controller().pauseOrResume();
                    fixture.controller().continueForOneTick();
                    assertTrue(fixture.controller().isSingleStepRunning());

                    schedule(fixture, 40.0, () -> {
                        fixture.controller().pauseOrResume();
                        assertTrue(fixture.controller().isPaused(),
                                "Звичайна пауза має зупинити однотактне виконання");
                        assertFalse(fixture.controller().isSingleStepRunning());

                        schedule(fixture, TICK_MILLIS + ASYNC_MARGIN_MILLIS, () -> {
                            assertEquals(1, fixture.tickCount().get(),
                                    "Після звичайної паузи такт не має продовжуватися самостійно");
                            assertTrue(fixture.controller().isPaused());

                            fixture.controller().pauseOrResume();
                            assertFalse(fixture.controller().isPaused());

                            schedule(fixture, TICK_MILLIS + ASYNC_MARGIN_MILLIS, () -> {
                                assertTrue(fixture.tickCount().get() >= 2,
                                        "Після зняття звичайної паузи симуляція має працювати безперервно");
                                finish(fixture, completed);
                            }, failure, completed);
                        }, failure, completed);
                    }, failure, completed);
                }, failure, completed);
            } catch (Throwable throwable) {
                failAndFinish(fixture, throwable, failure, completed);
            }
        });

        assertTrue(completed.await(5, TimeUnit.SECONDS), "Перевірка звичайної паузи не завершилася");
        rethrowFailure(failure);
    }

    /**
     * Створює симуляцію з ланцюгом нейронів і реальними поданнями, щоб тест міг перевірити анімації імпульсів.
     *
     * @return контролер, полотно, початковий нейрон і лічильник оброблених тактів.
     */
    private static Fixture createFixture() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 50.0, 50.0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 250.0, 50.0);
        Neuron finalTarget = model.createNeuron(NeuronType.EXCITATORY, 450.0, 50.0);
        assertTrue(model.createConnection(source.id(), target.id()));
        assertTrue(model.createConnection(target.id(), finalTarget.id()));

        WorkspaceView workspace = new WorkspaceView();
        new Scene(workspace.node(), 800.0, 600.0);
        Map<String, NeuronView> neuronViews = new HashMap<>();
        for (Neuron neuron : model.neurons()) {
            neuronViews.put(neuron.id(), new NeuronView(model.presentation(neuron.id())));
        }
        workspace.nodeLayer().getChildren().addAll(neuronViews.values());

        for (Connection connection : model.connections()) {
            workspace.edgeLayer().getChildren().add(
                    new ConnectionView(connection, neuronViews::get, model::neuron));
        }

        AtomicInteger tickCount = new AtomicInteger();
        SimulationStepPresenter presenter = new SimulationStepPresenter(
                workspace,
                neuronViews,
                () -> neuronViews.values().forEach(view -> view.refreshVisuals(false)),
                new PulseAnimationController(workspace));
        SimulationController controller = new SimulationController(
                new NeuronService(model),
                new SimulationService(),
                presenter,
                () -> { },
                message -> {
                    if (message.startsWith("Tick ")) {
                        tickCount.incrementAndGet();
                    }
                },
                ignored -> { },
                ignored -> { },
                TICK_MILLIS,
                10.0,
                500.0,
                new LocalizationService(Locale.ENGLISH));
        return new Fixture(controller, workspace, source.id(), tickCount);
    }

    /**
     * Запускає відкладену перевірку в потоці JavaFX і звільняє ресурси, якщо вона завершується помилкою.
     *
     * @param fixture тестове оточення симуляції.
     * @param delayMillis затримка перед виконанням перевірки.
     * @param action перевірка, яку потрібно виконати.
     * @param failure сховище першої помилки тесту.
     * @param completed сигнал завершення асинхронного тесту.
     */
    private static void schedule(
            Fixture fixture,
            double delayMillis,
            Runnable action,
            AtomicReference<Throwable> failure,
            CountDownLatch completed
    ) {
        PauseTransition delay = new PauseTransition(Duration.millis(delayMillis));
        delay.setOnFinished(event -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failAndFinish(fixture, throwable, failure, completed);
            }
        });
        delay.play();
    }

    /**
     * Зупиняє симуляцію й звільняє робочу область перед сигналізацією про завершення тесту.
     *
     * @param fixture тестове оточення, ресурси якого потрібно звільнити.
     * @param completed сигнал завершення асинхронного тесту.
     */
    private static void finish(Fixture fixture, CountDownLatch completed) {
        fixture.controller().shutdown();
        fixture.workspace().dispose();
        completed.countDown();
    }

    /**
     * Зберігає помилку асинхронної перевірки та гарантовано звільняє JavaFX-ресурси.
     *
     * @param fixture тестове оточення, ресурси якого потрібно звільнити.
     * @param throwable помилка основної перевірки.
     * @param failure сховище першої помилки тесту.
     * @param completed сигнал завершення асинхронного тесту.
     */
    private static void failAndFinish(
            Fixture fixture,
            Throwable throwable,
            AtomicReference<Throwable> failure,
            CountDownLatch completed
    ) {
        failure.compareAndSet(null, throwable);
        try {
            finish(fixture, completed);
        } catch (Throwable cleanupFailure) {
            failure.get().addSuppressed(cleanupFailure);
            completed.countDown();
        }
    }

    /**
     * Передає помилку з JavaFX-потоку до потоку JUnit після завершення асинхронної перевірки.
     *
     * @param failure сховище помилки, що могла виникнути у JavaFX-потоці.
     */
    private static void rethrowFailure(AtomicReference<Throwable> failure) {
        if (failure.get() != null) {
            throw new AssertionError("Перевірка однотактного виконання завершилася помилкою", failure.get());
        }
    }

    /**
     * Об'єднує залежності одного тестового сеансу та його графічного подання.
     *
     * @param controller контролер тестової симуляції.
     * @param workspace графічне полотно з анімаціями сигналів.
     * @param sourceId ідентифікатор початкового нейрона.
     * @param tickCount лічильник відображених тактів.
     */
    private record Fixture(
            SimulationController controller,
            WorkspaceView workspace,
            String sourceId,
            AtomicInteger tickCount
    ) {
    }
}
