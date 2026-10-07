
package com.example.neuronmap.controller;

import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.simulation.SimulationSession;
import com.example.neuronmap.simulation.SimulationSpeed;
import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Coordinates the synchronous simulation and detached pulse visuals. */
public final class SimulationController {

    private final NeuronService neuronService;
    private final SimulationService simulationService;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refreshNeuronViews;
    private final Runnable save;
    private final Consumer<String> status;
    private final Consumer<Boolean> pausedStateConsumer;
    private final Consumer<Boolean> simulationActivityConsumer;
    private final PulseAnimationController pulseAnimations;
    private final double minTickMillis;
    private final double maxTickMillis;

    private final PauseTransition finishDelay;

    private Timeline timeline;
    private SimulationSession session;
    private double tickMillis;
    private boolean paused;

    /**
     * Compatibility constructor for existing callers/tests that still depend
     * on the application facade. New code should pass NeuronService directly.
     */
    public SimulationController(
            com.example.neuronmap.application.NeuronMapApplicationService application,
            SimulationService simulationService,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refreshNeuronViews,
            Runnable save,
            Consumer<String> status,
            Consumer<Boolean> pausedStateConsumer,
            Consumer<Boolean> simulationActivityConsumer,
            double initialTickMillis,
            double minTickMillis,
            double maxTickMillis
    ) {
        this(
                Objects.requireNonNull(application, "application").neurons(),
                simulationService,
                workspace,
                neuronViews,
                refreshNeuronViews,
                save,
                status,
                pausedStateConsumer,
                simulationActivityConsumer,
                initialTickMillis,
                minTickMillis,
                maxTickMillis
        );
    }

    public SimulationController(
            NeuronService neuronService,
            SimulationService simulationService,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refreshNeuronViews,
            Runnable save,
            Consumer<String> status,
            Consumer<Boolean> pausedStateConsumer,
            Consumer<Boolean> simulationActivityConsumer,
            double initialTickMillis,
            double minTickMillis,
            double maxTickMillis
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.simulationService = Objects.requireNonNull(
                simulationService,
                "simulationService"
        );
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = Objects.requireNonNull(
                neuronViews,
                "neuronViews"
        );
        this.refreshNeuronViews = Objects.requireNonNull(
                refreshNeuronViews,
                "refreshNeuronViews"
        );
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.pausedStateConsumer = Objects.requireNonNull(
                pausedStateConsumer,
                "pausedStateConsumer"
        );
        this.simulationActivityConsumer = Objects.requireNonNull(
                simulationActivityConsumer,
                "simulationActivityConsumer"
        );
        this.minTickMillis = minTickMillis;
        this.maxTickMillis = maxTickMillis;
        this.tickMillis = SimulationSpeed.requireMillis(
                initialTickMillis,
                minTickMillis,
                maxTickMillis
        );
        this.pulseAnimations = new PulseAnimationController(
                workspace,
                this::updateSimulationControlsVisibility
        );
        this.finishDelay = new PauseTransition(
                Duration.millis(tickMillis)
        );

        finishDelay.setOnFinished(event -> {
            neuronService.model().clearActivations();
            clearDisplayedInputSignals();
            session = null;
            timeline = null;
            refreshNeuronViews.run();
            save.run();
            status.accept("Імпульс завершено.");
            updateSimulationControlsVisibility();
        });

        simulationActivityConsumer.accept(false);
        pausedStateConsumer.accept(false);
    }

    public void emitPulse(String sourceNeuronId) {
        if (neuronService.model().neuron(sourceNeuronId) == null) {
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
                || pulseAnimations.hasActiveAnimations();
    }

    /** Fully cancels the current signal chain and every active pulse visual. */
    public void stopSignals() {
        resetRuntime();
        paused = false;
        notifyPausedState();
        refreshNeuronViews.run();
        save.run();
        updateSimulationControlsVisibility();
        status.accept("Передачу імпульсів повністю зупинено.");
    }

    /** Changes the interval between synchronous simulation ticks. */
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
        refreshNeuronViews.run();
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

        pulseAnimations.pauseAll();
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

        pulseAnimations.resumeAll();
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

        applyStep(step);

        if (session.isFinished()) {
            finishSimulation();
        }
    }

    private void applyStep(SimulationStep step) {
        neuronService.model().clearActivations();
        clearDisplayedInputSignals();

        step.inputSums().forEach(
                (neuronId, sum) -> {
                    Neuron neuron =
                            neuronService.model().neuron(neuronId);

                    if (neuron == null) {
                        return;
                    }

                    neuronService.setActivation(neuronId, sum);

                    NeuronView neuronView =
                            neuronViews.get(neuronId);

                    if (neuronView != null) {
                        neuronView.showInputSignal(sum);
                    }
                }
        );

        step.nextInputSums().forEach(
                (neuronId, sum) -> {
                    if (step.inputSums().containsKey(neuronId)) {
                        return;
                    }

                    NeuronView neuronView =
                            neuronViews.get(neuronId);

                    if (neuronView != null) {
                        neuronView.showInputSignal(sum);
                    }
                }
        );

        for (String neuronId : step.activatedNeuronIds()) {
            NeuronView neuronView = neuronViews.get(neuronId);
            if (neuronView != null) {
                neuronView.hideInputSignal();
            }
        }

        refreshNeuronViews.run();

        for (String neuronId : step.activatedNeuronIds()) {
            animateOutgoingConnections(neuronId);
        }

        updateSimulationControlsVisibility();
        status.accept(
                "Такт " + (step.tick() + 1)
                        + ": сигнали підсумовано одночасно."
        );
    }

    private void clearDisplayedInputSignals() {
        for (NeuronView neuronView : neuronViews.values()) {
            neuronView.clearDisplayedInputSignal();
        }
    }

    private void animateOutgoingConnections(String neuronId) {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView
                    && connectionView.model().sourceId().equals(neuronId)) {
                pulseAnimations.play(connectionView);
            }
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
        pulseAnimations.stopAll();

        neuronService.model().clearActivations();
        clearDisplayedInputSignals();
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
