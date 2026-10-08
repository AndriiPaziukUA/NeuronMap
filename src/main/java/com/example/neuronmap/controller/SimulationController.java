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

/** Coordinates one global synchronous simulation timeline. */
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

    /** Temporarily suspends the simulation for a modal in-window menu. */
    public boolean pauseForModal() {
        if (!hasActiveSimulation() || paused) {
            modalSuspended = false;
            return false;
        }

        modalSuspended = true;
        pauseInternal();
        return true;
    }

    /** Resumes the simulation only when this menu pause actually suspended it. */
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

    public boolean isPaused() {
        return paused;
    }

    public boolean hasActiveSimulation() {
        return session != null
                || timeline != null
                || finishDelay.getStatus() != Animation.Status.STOPPED
                || stepPresenter.hasActiveAnimations();
    }

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

    public void stop() {
        resetRuntime();
        paused = false;
        modalSuspended = false;
        notifyPausedState();
        stepPresenter.refresh();
        updateSimulationControlsVisibility();
    }

    public void shutdown() {
        resetRuntime();
        paused = false;
        modalSuspended = false;
        notifyPausedState();
        updateSimulationControlsVisibility();
    }

    private void pause() {
        if (!hasActiveSimulation()) {
            return;
        }
        pauseInternal();
        status.accept(localization.text("status.simulation_paused"));
    }

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

    private void resume() {
        resumeInternal();
        status.accept(localization.text("status.simulation_resumed"));
    }

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

    private void resetRuntime() {
        if (timeline != null) {
            timeline.stop();
        }
        finishDelay.stop();
        timeline = null;
        session = null;
        stepPresenter.clearRuntime();
    }

    private void createTimeline() {
        timeline = new Timeline(
                new KeyFrame(
                        Duration.millis(tickMillis),
                        event -> processNextStep()
                )
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

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

    private void notifyPausedState() {
        pausedStateConsumer.accept(paused);
    }

    private void updateSimulationControlsVisibility() {
        simulationActivityConsumer.accept(hasActiveSimulation());
    }

    private static String formatMillis(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001
                ? String.format(java.util.Locale.ROOT, "%.0f", value)
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
