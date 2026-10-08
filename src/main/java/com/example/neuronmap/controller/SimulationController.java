package com.example.neuronmap.controller;

import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.simulation.SimulationSession;
import com.example.neuronmap.simulation.SimulationSpeed;
import com.example.neuronmap.simulation.SimulationStep;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.Consumer;

/** Coordinates simulation lifecycle while delegating step rendering to a presenter. */
public final class SimulationController {

    private final NeuronService neuronService;
    private final SimulationStepPresenter stepPresenter;
    private final Runnable save;
    private final Consumer<String> status;
    private final Consumer<Boolean> pausedStateConsumer;
    private final Consumer<Boolean> simulationActivityConsumer;
    private final double minTickMillis;
    private final double maxTickMillis;
    private final PauseTransition finishDelay;

    private Timeline timeline;
    private SimulationSession session;
    private double tickMillis;
    private boolean paused;

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
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.stepPresenter = Objects.requireNonNull(stepPresenter, "stepPresenter");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.pausedStateConsumer = Objects.requireNonNull(pausedStateConsumer, "pausedStateConsumer");
        this.simulationActivityConsumer = Objects.requireNonNull(simulationActivityConsumer, "simulationActivityConsumer");
        this.minTickMillis = minTickMillis;
        this.maxTickMillis = maxTickMillis;
        this.tickMillis = SimulationSpeed.requireMillis(
                initialTickMillis,
                minTickMillis,
                maxTickMillis
        );
        this.finishDelay = new PauseTransition(Duration.millis(tickMillis));

        finishDelay.setOnFinished(event -> {
            stepPresenter.clearRuntime();
            session = null;
            timeline = null;
            stepPresenter.refresh();
            save.run();
            status.accept("Імпульс завершено.");
            updateSimulationControlsVisibility();
        });

        simulationActivityConsumer.accept(false);
        pausedStateConsumer.accept(false);
    }

    public void emitPulse(String sourceNeuronId) {
        if (neuronService.find(sourceNeuronId) == null) {
            return;
        }

        resetRuntime();
        paused = false;
        notifyPausedState();

        session = SimulationSession.manual(
                neuronService.model(),
                sourceNeuronId,
                SimulationService.MAX_TICKS
        );
        updateSimulationControlsVisibility();

        processNextStep();

        if (session == null) {
            updateSimulationControlsVisibility();
            return;
        }

        if (session.isFinished()) {
            finishSimulation();
            return;
        }

        createTimeline();
        timeline.play();
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
        notifyPausedState();
        stepPresenter.refresh();
        save.run();
        updateSimulationControlsVisibility();
        status.accept("Передачу імпульсів повністю зупинено.");
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
                "Швидкість імпульсів: " + formatMillis(tickMillis) + " мс/такт."
        );
    }

    public void stop() {
        resetRuntime();
        paused = false;
        notifyPausedState();
        stepPresenter.refresh();
        updateSimulationControlsVisibility();
    }

    public void shutdown() {
        resetRuntime();
        paused = false;
        notifyPausedState();
        updateSimulationControlsVisibility();
    }

    private void pause() {
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
        status.accept("Передачу імпульсів призупинено.");
    }

    private void resume() {
        paused = false;
        if (timeline != null) {
            timeline.play();
        }
        if (finishDelay.getStatus() == Animation.Status.PAUSED) {
            finishDelay.play();
        }
        stepPresenter.resumeAnimations();
        notifyPausedState();
        updateSimulationControlsVisibility();
        status.accept("Передачу імпульсів продовжено.");
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
                "Такт " + (step.tick() + 1)
                        + ": сигнали підсумовано одночасно."
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
