package com.example.neuronmap.controller;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.Node;

import java.util.Map;

/** Applies simulation steps to the JavaFX representation and pulse visuals. */
public final class SimulationStepPresenter {

    private final NeuronService neuronService;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refreshNeuronViews;
    private final PulseAnimationController pulseAnimations;

    public SimulationStepPresenter(
            NeuronService neuronService,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refreshNeuronViews
    ) {
        this.neuronService = neuronService;
        this.workspace = workspace;
        this.neuronViews = neuronViews;
        this.refreshNeuronViews = refreshNeuronViews;
        this.pulseAnimations = new PulseAnimationController(
                workspace,
                () -> { }
        );
    }

    public void apply(SimulationStep step) {
        clearDisplayedInputSignals();
        neuronService.clearActivations();

        step.inputSums().forEach((neuronId, sum) -> {
            Neuron neuron = neuronService.find(neuronId);
            if (neuron == null) {
                return;
            }

            neuronService.setActivation(neuronId, sum);
            NeuronView neuronView = neuronViews.get(neuronId);
            if (neuronView != null) {
                neuronView.showInputSignal(sum);
            }
        });

        step.nextInputSums().forEach((neuronId, sum) -> {
            if (step.inputSums().containsKey(neuronId)) {
                return;
            }

            NeuronView neuronView = neuronViews.get(neuronId);
            if (neuronView != null) {
                neuronView.showInputSignal(sum);
            }
        });

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
    }

    public void clearRuntime() {
        neuronService.clearActivations();
        clearDisplayedInputSignals();
        pulseAnimations.stopAll();
    }

    public void refresh() {
        refreshNeuronViews.run();
    }

    public boolean hasActiveAnimations() {
        return pulseAnimations.hasActiveAnimations();
    }

    public void pauseAnimations() {
        pulseAnimations.pauseAll();
    }

    public void resumeAnimations() {
        pulseAnimations.resumeAll();
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
}
