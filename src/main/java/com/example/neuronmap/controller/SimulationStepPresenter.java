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
 * Відображає результати такту симуляції: вхідні сигнали нейронів та анімацію імпульсів уздовж вихідних зв’язків.
 */
public final class SimulationStepPresenter {

    private final NeuronService neuronService;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refreshNeuronViews;
    private final PulseAnimationController pulseAnimations;

    /**
     * Створює екземпляр SimulationStepPresenter та зберігає передані залежності, потрібні для його роботи.
     *
     * @param neuronService служба операцій над нейронами.
     * @param workspace полотно редактора.
     * @param neuronViews мапа візуальних подань нейронів за їхніми ідентифікаторами.
     * @param refreshNeuronViews callback, який оновлює візуальні подання нейронів після зміни вибору.
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
     * Оновлює інтерфейс за результатом такту: показує активовані нейрони та запускає анімацію вихідних сигналів.
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
     * Прибирає індикатори сигналів і решту тимчасового відображення завершеного сеансу симуляції.
     */
    public void clearRuntime() {
        neuronService.clearActivations();
        clearDisplayedInputSignals();
        pulseAnimations.stopAll();
    }

    /**
     * Оновлює подання нейронів і зв’язків після зміни моделі.
     */
    public void refresh() {
        refreshNeuronViews.run();
    }

    /**
     * Перевіряє, чи відображаються анімації імпульсів, які ще не завершилися.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean hasActiveAnimations() {
        return pulseAnimations.hasActiveAnimations();
    }

    /**
     * Призупиняє «animations», зберігаючи можливість подальшого відновлення.
     */
    public void pauseAnimations() {
        pulseAnimations.pauseAll();
    }

    /**
     * Відновлює «animations» після призупинення.
     */
    public void resumeAnimations() {
        pulseAnimations.resumeAll();
    }

    /**
     * Показує на нейронах суми сигналів, отриманих у поточному такті.
     *
     * @param inputSums суми вхідних сигналів для нейронів на поточному такті.
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
     * Прибирає з нейронів індикатори вхідних сигналів.
     */
    private void clearDisplayedInputSignals() {
        for (NeuronView neuronView : neuronViews.values()) {
            neuronView.clearDisplayedInputSignal();
        }
    }

    /**
     * Запускає анімацію імпульсів на вихідних зв’язках указаного нейрона.
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
