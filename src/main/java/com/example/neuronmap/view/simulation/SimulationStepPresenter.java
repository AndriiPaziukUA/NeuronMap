package com.example.neuronmap.view.simulation;

import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.scene.Node;

import java.util.Map;
import java.util.Objects;

/**
 * Відображає результати такту симуляції через індикатори вхідних сигналів та анімації зв’язків.
 */
public class SimulationStepPresenter {

    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refreshNeuronViews;
    private final PulseAnimator pulseAnimations;

    /**
     * Створює подання результатів симуляції та пов’язує його з елементами редактора.
     *
     * @param workspace полотно редактора.
     * @param neuronViews візуальні подання нейронів за їхніми ідентифікаторами.
     * @param refreshNeuronViews дія оновлення візуальних подань нейронів.
     * @param pulseAnimations компонент керування анімаціями імпульсів.
     */
    public SimulationStepPresenter(
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refreshNeuronViews,
            PulseAnimator pulseAnimations
    ) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = Objects.requireNonNull(neuronViews, "neuronViews");
        this.refreshNeuronViews = Objects.requireNonNull(refreshNeuronViews, "refreshNeuronViews");
        this.pulseAnimations = Objects.requireNonNull(pulseAnimations, "pulseAnimations");
    }

    /**
     * Відображає суми вхідних сигналів і запускає анімацію вихідних сигналів для обробленого такту.
     *
     * @param step результат одного такту симуляції.
     */
    public void apply(SimulationStep step) {
        Objects.requireNonNull(step, "step");
        clearDisplayedInputSignals();
        showIncomingSignals(step.nextInputSums());
        refreshNeuronViews.run();
        for (String neuronId : step.activatedNeuronIds()) {
            animateOutgoingConnections(neuronId);
        }
    }

    /** Прибирає індикатори сигналів і тимчасові анімації завершеного сеансу симуляції. */
    public void clearRuntime() {
        clearDisplayedInputSignals();
        pulseAnimations.stopAll();
    }

    /** Оновлює подання нейронів і зв’язків після зміни моделі. */
    public void refresh() {
        refreshNeuronViews.run();
    }

    /**
     * Перевіряє, чи відображаються анімації імпульсів, які ще не завершилися.
     *
     * @return {@code true}, якщо є активні анімації; інакше {@code false}.
     */
    public boolean hasActiveAnimations() {
        return pulseAnimations.hasActiveAnimations();
    }

    /** Призупиняє анімації імпульсів, зберігаючи можливість їх відновлення. */
    public void pauseAnimations() {
        pulseAnimations.pauseAll();
    }

    /** Відновлює анімації імпульсів після призупинення. */
    public void resumeAnimations() {
        pulseAnimations.resumeAll();
    }

    /**
     * Показує на нейронах суми сигналів, запланованих для наступного такту.
     *
     * @param inputSums суми вхідних сигналів для нейронів наступного такту.
     */
    private void showIncomingSignals(Map<String, Integer> inputSums) {
        inputSums.forEach((neuronId, sum) -> {
            NeuronView neuronView = neuronViews.get(neuronId);
            if (neuronView != null) {
                neuronView.showInputSignal(sum);
            }
        });
    }

    /** Прибирає з нейронів індикатори вхідних сигналів. */
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
