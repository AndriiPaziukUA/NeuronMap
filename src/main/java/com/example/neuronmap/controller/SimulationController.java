package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationSession;
import com.example.neuronmap.simulation.SimulationSpeed;
import com.example.neuronmap.simulation.SimulationStep;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.math.BigInteger;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Керує життєвим циклом симуляції, її паузою, швидкістю, обробкою тактів і повідомленням інтерфейсу про стан виконання.
 */
public final class SimulationController {

    private final NeuronService neuronService;
    private final SimulationStepPresenter stepPresenter;
    private final Runnable save;
    private final Consumer<String> status;
    private final Consumer<Boolean> pausedStateConsumer;
    private final Consumer<Boolean> simulationActivityConsumer;
    private final LocalizationService localization;
    private final double minTickMillis;
    private final double maxTickMillis;
    private final PauseTransition finishDelay;

    private Timeline timeline;
    private SimulationSession session;
    private double tickMillis;
    private boolean paused;
    private boolean modalSuspended;

    /**
     * Створює екземпляр SimulationController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param neuronService служба операцій над нейронами.
     * @param stepPresenter компонент, що відображає результат такту симуляції.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
     * @param pausedStateConsumer callback, що отримує новий стан паузи.
     * @param simulationActivityConsumer callback, що отримує ознаку активності симуляції.
     * @param initialTickMillis початкова тривалість такту симуляції в мілісекундах.
     * @param minTickMillis мінімально допустима тривалість такту.
     * @param maxTickMillis максимально допустима тривалість такту.
     */
    public SimulationController(
            NeuronService neuronService,
            SimulationStepPresenter stepPresenter,
            Runnable save,
            Consumer<String> status,
            Consumer<Boolean> pausedStateConsumer,
            Consumer<Boolean> simulationActivityConsumer,
            double initialTickMillis,
            double minTickMillis,
            double maxTickMillis
    ) {
        this(
                neuronService,
                stepPresenter,
                save,
                status,
                pausedStateConsumer,
                simulationActivityConsumer,
                initialTickMillis,
                minTickMillis,
                maxTickMillis,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Створює екземпляр SimulationController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param neuronService служба операцій над нейронами.
     * @param stepPresenter компонент, що відображає результат такту симуляції.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
     * @param pausedStateConsumer callback, що отримує новий стан паузи.
     * @param simulationActivityConsumer callback, що отримує ознаку активності симуляції.
     * @param initialTickMillis початкова тривалість такту симуляції в мілісекундах.
     * @param minTickMillis мінімально допустима тривалість такту.
     * @param maxTickMillis максимально допустима тривалість такту.
     * @param localization служба локалізації інтерфейсу.
     */
    public SimulationController(
            NeuronService neuronService,
            SimulationStepPresenter stepPresenter,
            Runnable save,
            Consumer<String> status,
            Consumer<Boolean> pausedStateConsumer,
            Consumer<Boolean> simulationActivityConsumer,
            double initialTickMillis,
            double minTickMillis,
            double maxTickMillis,
            LocalizationService localization
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.stepPresenter = Objects.requireNonNull(stepPresenter, "stepPresenter");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.pausedStateConsumer = Objects.requireNonNull(pausedStateConsumer, "pausedStateConsumer");
        this.simulationActivityConsumer = Objects.requireNonNull(simulationActivityConsumer, "simulationActivityConsumer");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.minTickMillis = minTickMillis;
        this.maxTickMillis = maxTickMillis;
        this.tickMillis = SimulationSpeed.requireMillis(
                initialTickMillis,
                minTickMillis,
                maxTickMillis
        );
        this.finishDelay = new PauseTransition(Duration.millis(tickMillis));

        finishDelay.setOnFinished(event -> {
            if (session != null && session.hasPendingWork()) {
                startTimelineIfNeeded();
                return;
            }

            stepPresenter.clearRuntime();
            session = null;
            timeline = null;
            stepPresenter.refresh();
            save.run();
            status.accept(localization.text("status.simulation_finished"));
            updateSimulationControlsVisibility();
        });

        simulationActivityConsumer.accept(false);
        pausedStateConsumer.accept(false);
    }

    /**
     * Додає ручний запуск указаного нейрона до сеансу симуляції та запускає обробку тактів за потреби.
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     */
    public void emitPulse(String sourceNeuronId) {
        if (neuronService.find(sourceNeuronId) == null) {
            return;
        }

        if (session == null) {
            session = SimulationSession.manual(
                    neuronService.model(),
                    sourceNeuronId
            );
            paused = false;
            modalSuspended = false;
            notifyPausedState();
            finishDelay.stop();

            updateSimulationControlsVisibility();
            processNextStep();

            if (session == null) {
                updateSimulationControlsVisibility();
                return;
            }

            if (!session.isFinished()) {
                startTimelineIfNeeded();
            }

            updateSimulationControlsVisibility();
            return;
        }

        session.queueManualStart(sourceNeuronId);
        finishDelay.stop();

        if (!paused) {
            startTimelineIfNeeded();
        }

        updateSimulationControlsVisibility();
    }

    /**
     * Перемикає симуляцію між призупиненим станом і виконанням.
     */
    public void pauseOrResume() {
        if (!hasActiveSimulation()) {
            return;
        }
        if (paused) {
            resume();
        } else {
            pause();
        }
    }

/**
 * Призупиняє симуляцію для показу модального вікна та повідомляє, чи потрібно буде її відновити.
 */
public boolean pauseForModal() {
        if (!hasActiveSimulation() || paused) {
            modalSuspended = false;
            return false;
        }

        modalSuspended = true;
        pauseInternal();
        return true;
    }

/**
 * Відновлює симуляцію після закриття модального вікна, якщо вона була активною до показу діалогу.
 */
public void resumeFromModal() {
        if (!modalSuspended) {
            return;
        }
        modalSuspended = false;
        if (!hasActiveSimulation()) {
            return;
        }
        resumeInternal();
    }

    /**
     * Перевіряє, чи paused за поточного стану компонента.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Перевіряє, чи є active simulation у поточному стані.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean hasActiveSimulation() {
        return session != null
                || timeline != null
                || finishDelay.getStatus() != Animation.Status.STOPPED
                || stepPresenter.hasActiveAnimations();
    }

    /**
     * Зупиняє поширення сигналів і скидає незавершену роботу симуляції.
     */
    public void stopSignals() {
        resetRuntime();
        paused = false;
        modalSuspended = false;
        notifyPausedState();
        stepPresenter.refresh();
        save.run();
        updateSimulationControlsVisibility();
        status.accept(localization.text("status.simulation_stopped"));
    }

    /**
     * Установлює тривалість одного такту симуляції в мілісекундах.
     *
     * @param millis тривалість такту в мілісекундах.
     */
    public void setTickDurationMillis(double millis) {
        tickMillis = SimulationSpeed.requireMillis(
                millis,
                minTickMillis,
                maxTickMillis
        );

        if (timeline == null) {
            finishDelay.setDuration(Duration.millis(tickMillis));
            return;
        }

        boolean wasPaused = paused;
        timeline.stop();
        createTimeline();

        if (wasPaused) {
            timeline.pause();
        } else {
            timeline.play();
        }

        finishDelay.setDuration(Duration.millis(tickMillis));
        status.accept(
                localization.text("status.speed_changed", formatMillis(tickMillis))
        );
    }

    /**
     * Зупиняє  та очищає пов’язаний активний стан.
     */
    public void stop() {
        resetRuntime();
        paused = false;
        modalSuspended = false;
        notifyPausedState();
        stepPresenter.refresh();
        updateSimulationControlsVisibility();
    }

    /**
     * Зупиняє таймери й анімацію та звільняє ресурси симуляції.
     */
    public void shutdown() {
        resetRuntime();
        paused = false;
        modalSuspended = false;
        notifyPausedState();
        updateSimulationControlsVisibility();
    }

    /**
     * Призупиняє виконання симуляції, не скидаючи її поточний стан.
     */
    private void pause() {
        if (!hasActiveSimulation()) {
            return;
        }
        pauseInternal();
        status.accept(localization.text("status.simulation_paused"));
    }

    /**
     * Призупиняє внутрішню часову шкалу симуляції без повторного перемикання зовнішнього стану паузи.
     */
    private void pauseInternal() {
        paused = true;
        if (timeline != null) {
            timeline.pause();
        }
        if (finishDelay.getStatus() == Animation.Status.RUNNING) {
            finishDelay.pause();
        }
        stepPresenter.pauseAnimations();
        notifyPausedState();
        updateSimulationControlsVisibility();
    }

    /**
     * Відновлює виконання симуляції з поточного сеансу, якщо залишилася запланована робота.
     */
    private void resume() {
        resumeInternal();
        status.accept(localization.text("status.simulation_resumed"));
    }

    /**
     * Запускає внутрішню часову шкалу симуляції, якщо сеанс має наступний такт для обробки.
     */
    private void resumeInternal() {
        paused = false;
        startTimelineIfNeeded();
        if (finishDelay.getStatus() == Animation.Status.PAUSED) {
            finishDelay.play();
        }
        stepPresenter.resumeAnimations();
        notifyPausedState();
        updateSimulationControlsVisibility();
    }

    /**
     * Обчислює наступний такт сеансу, передає його результат поданню й планує продовження симуляції.
     */
    private void processNextStep() {
        if (session == null) {
            return;
        }

        SimulationStep step = session.nextStep();
        if (step == null) {
            finishSimulation();
            return;
        }

        stepPresenter.apply(step);

        status.accept(
                localization.text(
                        "status.tick",
                        step.tick().add(BigInteger.ONE)
                )
        );

        if (session.isFinished()) {
            finishSimulation();
        }
    }

    /**
     * Завершує симуляцію, оновлює індикатори стану й зберігає потрібні параметри.
     */
    private void finishSimulation() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }

        if (finishDelay.getDuration().toMillis() != tickMillis) {
            finishDelay.setDuration(Duration.millis(tickMillis));
        }

        finishDelay.playFromStart();
        updateSimulationControlsVisibility();
    }

    /**
     * Скидає поточний сеанс, таймер і тимчасові сигнали симуляції.
     */
    private void resetRuntime() {
        if (timeline != null) {
            timeline.stop();
        }
        finishDelay.stop();
        timeline = null;
        session = null;
        stepPresenter.clearRuntime();
    }

    /**
     * Створює timeline з переданих параметрів.
     */
    private void createTimeline() {
        timeline = new Timeline(
                new KeyFrame(
                        Duration.millis(tickMillis),
                        event -> processNextStep()
                )
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    /**
     * Запускає часову шкалу лише тоді, коли симуляція має роботу й таймер ще не виконується.
     */
    private void startTimelineIfNeeded() {
        if (session == null || paused || !session.hasPendingWork()) {
            return;
        }

        if (timeline == null) {
            createTimeline();
        }

        if (timeline.getStatus() != Animation.Status.RUNNING) {
            timeline.play();
        }
    }

    /**
     * Передає поточний стан паузи зареєстрованому споживачу стану інтерфейсу.
     */
    private void notifyPausedState() {
        pausedStateConsumer.accept(paused);
    }

    /**
     * Показує або приховує елементи керування симуляцією відповідно до її поточного стану.
     */
    private void updateSimulationControlsVisibility() {
        simulationActivityConsumer.accept(hasActiveSimulation());
    }

    /**
     * Форматує тривалість такту в мілісекундах для показу в полі швидкості симуляції.
     *
     * @param value значення, яке потрібно зберегти або перевірити.
     */
    private static String formatMillis(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001
                ? String.format(java.util.Locale.ROOT, "%.0f", value)
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
