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
 * Повʼязує керування симуляцією в інтерфейсі з обчисленням тактів і показом результатів.
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
     * Повертає результат операції «відповідну операцію».
     *
     * @param neuronService значення, що визначає нейрон служба для цієї операції.
     *
     * @param stepPresenter значення, що визначає крок для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     *
     * @param pausedStateConsumer значення, що визначає стан для цієї операції.
     *
     * @param simulationActivityConsumer значення, що визначає відповідну операцію для цієї операції.
     *
     * @param initialTickMillis значення, що визначає такт для цієї операції.
     *
     * @param minTickMillis значення, що визначає такт для цієї операції.
     *
     * @param maxTickMillis значення, що визначає такт для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Повертає результат операції «відповідну операцію».
     *
     * @param neuronService значення, що визначає нейрон служба для цієї операції.
     *
     * @param stepPresenter значення, що визначає крок для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     *
     * @param pausedStateConsumer значення, що визначає стан для цієї операції.
     *
     * @param simulationActivityConsumer значення, що визначає відповідну операцію для цієї операції.
     *
     * @param initialTickMillis значення, що визначає такт для цієї операції.
     *
     * @param minTickMillis значення, що визначає такт для цієї операції.
     *
     * @param maxTickMillis значення, що визначає такт для цієї операції.
     *
     * @param localization значення, що визначає локалізація для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Виконує операцію «імпульс».
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
     * Виконує операцію «або».
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
 * Повертає результат операції «для».
 *
 * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
 * Виконує операцію «із».
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
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean hasActiveSimulation() {
        return session != null
                || timeline != null
                || finishDelay.getStatus() != Animation.Status.STOPPED
                || stepPresenter.hasActiveAnimations();
    }

    /**
     * Завершує або скасовує дію, повʼязану з «сигнали».
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
     * Задає або оновлює значення, повʼязані з «такт».
     *
     * @param millis значення, що визначає відповідну операцію для цієї операції.
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
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
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
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    public void shutdown() {
        resetRuntime();
        paused = false;
        modalSuspended = false;
        notifyPausedState();
        updateSimulationControlsVisibility();
    }

    /**
     * Виконує операцію «відповідну операцію».
     */
    private void pause() {
        if (!hasActiveSimulation()) {
            return;
        }
        pauseInternal();
        status.accept(localization.text("status.simulation_paused"));
    }

    /**
     * Виконує операцію «відповідну операцію».
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
     * Виконує операцію «відповідну операцію».
     */
    private void resume() {
        resumeInternal();
        status.accept(localization.text("status.simulation_resumed"));
    }

    /**
     * Виконує операцію «відповідну операцію».
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
     * Обробляє «наступний крок».
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
     * Завершує або скасовує дію, повʼязану з «відповідну операцію».
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
     * Видаляє або скидає дані, повʼязані з «відповідну операцію».
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
     * Створює обʼєкт із переданих даних «відповідну операцію».
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
     * Запускає або планує дію, повʼязану з «якщо».
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
     * Обробляє «стан».
     */
    private void notifyPausedState() {
        pausedStateConsumer.accept(paused);
    }

    /**
     * Задає або оновлює значення, повʼязані з «відповідну операцію».
     */
    private void updateSimulationControlsVisibility() {
        simulationActivityConsumer.accept(hasActiveSimulation());
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param value значення, яке потрібно передати або зберегти.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    private static String formatMillis(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001
                ? String.format(java.util.Locale.ROOT, "%.0f", value)
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
