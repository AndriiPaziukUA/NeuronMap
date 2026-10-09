package com.example.neuronmap.controller;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.Node;

import java.util.Map;

/**
 * Передає результат такту симуляції компонентам, які відображають його в інтерфейсі.
 */
public final class SimulationStepPresenter {

    private final NeuronService neuronService;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refreshNeuronViews;
    private final PulseAnimationController pulseAnimations;

    /**
     * Повертає результат операції «крок».
     *
     * @param neuronService значення, що визначає нейрон служба для цієї операції.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param refreshNeuronViews значення, що визначає нейрон для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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

    /**
     * Обробляє «потрібні дані».
     *
     * @param step результат одного такту симуляції.
     */
    public void apply(SimulationStep step) {
        clearDisplayedInputSignals();
        neuronService.clearActivations();

        step.inputSums().forEach((neuronId, sum) -> {
            Neuron neuron = neuronService.find(neuronId);
            if (neuron == null) {
                return;
            }

            neuronService.setActivation(neuronId, sum);
        });

        showIncomingSignals(step.nextInputSums());

        refreshNeuronViews.run();

        for (String neuronId : step.activatedNeuronIds()) {
            animateOutgoingConnections(neuronId);
        }
    }

    /**
     * Видаляє або скидає дані, повʼязані з «відповідну операцію».
     */
    public void clearRuntime() {
        neuronService.clearActivations();
        clearDisplayedInputSignals();
        pulseAnimations.stopAll();
    }

    /**
     * Обробляє «потрібні дані».
     */
    public void refresh() {
        refreshNeuronViews.run();
    }

    /**
     * Перевіряє, чи виконується умова «анімації».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean hasActiveAnimations() {
        return pulseAnimations.hasActiveAnimations();
    }

    /**
     * Виконує операцію «анімації».
     */
    public void pauseAnimations() {
        pulseAnimations.pauseAll();
    }

    /**
     * Виконує операцію «анімації».
     */
    public void resumeAnimations() {
        pulseAnimations.resumeAll();
    }

    /**
     * Відображає «сигнали» в інтерфейсі.
     *
     * @param inputSums значення, що визначає вхід для цієї операції.
     */
    private void showIncomingSignals(Map<String, Integer> inputSums) {
        inputSums.forEach((neuronId, sum) -> {
            NeuronView neuronView = neuronViews.get(neuronId);
            if (neuronView != null) {
                neuronView.showInputSignal(sum);
            }
        });
    }

    /**
     * Видаляє або скидає дані, повʼязані з «вхід сигнали».
     */
    private void clearDisplayedInputSignals() {
        for (NeuronView neuronView : neuronViews.values()) {
            neuronView.clearDisplayedInputSignal();
        }
    }

    /**
     * Виконує операцію «звʼязки».
     *
     * @param neuronId ідентифікатор нейрона.
     */
    private void animateOutgoingConnections(String neuronId) {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView
                    && connectionView.model().sourceId().equals(neuronId)) {
                pulseAnimations.play(connectionView);
            }
        }
    }
}
