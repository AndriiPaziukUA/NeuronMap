package com.example.neuronmap.view.simulation;

import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.Node;

import java.util.Map;
import java.util.Objects;

/** Відображає результати такту симуляції через індикатори вхідних сигналів, порти нейронів та анімації зв'язків. */
public class SimulationStepPresenter {

    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refreshNeuronViews;
    private final PulseAnimator pulseAnimations;

    /**
     * Створює подання результатів симуляції та пов'язує його з елементами редактора.
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
     * Відображає результати такту та запускає анімації всіх нейронів, які випустили сигнал у цьому такті.
     *
     * @param step результат одного такту симуляції.
     * @throws NullPointerException якщо результат такту не задано.
     */
    public void apply(SimulationStep step) {
        Objects.requireNonNull(step, "step");
        restoreVisualState(step);
        for (String neuronId : step.emittingNeuronIds()) {
            animateOutgoingConnections(neuronId);
        }
    }

    /**
     * Відновлює індикатори поточного такту без запуску нових імпульсів.
     * Використовується після відновлення моделі, коли вже наявні анімації мають продовжуватися з поточного прогресу.
     *
     * @param step останній оброблений такт симуляції.
     * @throws NullPointerException якщо результат такту не задано.
     */
    public void restoreVisualState(SimulationStep step) {
        Objects.requireNonNull(step, "step");
        clearDisplayedInputSignals();
        clearOutputSignalHighlights();
        showIncomingSignals(step.nextInputSums());
        refreshNeuronViews.run();
        for (String neuronId : step.emittingNeuronIds()) {
            highlightOutputSignal(neuronId);
        }
    }

    /** Прибирає індикатори сигналів, підсвічування виходів і тимчасові анімації завершеного сеансу. */
    public void clearRuntime() {
        clearDisplayedInputSignals();
        clearOutputSignalHighlights();
        pulseAnimations.stopAll();
    }

    /** Оновлює подання нейронів і зв'язків після зміни моделі. */
    public void refresh() {
        refreshNeuronViews.run();
    }

    /** Оновлює тривалість анімацій відповідно до поточної тривалості такту симуляції. */
    public void setTickDurationMillis(double tickDurationMillis) {
        pulseAnimations.setTickDurationMillis(tickDurationMillis);
    }

    /** Перевіряє, чи відображаються анімації імпульсів, які ще не завершилися. */
    public boolean hasActiveAnimations() {
        return pulseAnimations.hasActiveAnimations();
    }

    /** Призупиняє анімації імпульсів, зберігаючи можливість їх відновлення. */
    public void pauseAnimations() {
        pulseAnimations.pauseAll();
    }

    /** Відновлює призупинені анімації імпульсів. */
    public void resumeAnimations() {
        pulseAnimations.resumeAll();
    }

    /** Показує суми сигналів, запланованих для наступного такту. */
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

    /** Повертає всі вихідні порти до нейтрального стану перед відображенням нового такту. */
    private void clearOutputSignalHighlights() {
        for (NeuronView neuronView : neuronViews.values()) {
            neuronView.setOutputSignalActive(false);
        }
    }

    /** Позначає вихід нейрона активним у поточному такті симуляції. */
    private void highlightOutputSignal(String neuronId) {
        NeuronView neuronView = neuronViews.get(neuronId);
        if (neuronView != null) {
            neuronView.setOutputSignalActive(true);
        }
    }

    /** Запускає анімації на поточних вихідних зв'язках указаного нейрона. */
    private void animateOutgoingConnections(String neuronId) {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView
                    && connectionView.model().sourceId().equals(neuronId)) {
                pulseAnimations.play(connectionView);
            }
        }
    }
}
