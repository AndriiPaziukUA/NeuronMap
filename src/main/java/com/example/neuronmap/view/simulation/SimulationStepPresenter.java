package com.example.neuronmap.view.simulation;

import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.Node;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Відображає результати такту симуляції через суми вхідних сигналів, залишки активних тактів і анімації зв'язків. */
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
     * Негайно оновлює стан карти після ручної активації та запускає її імпульси лише на залишок поточного такту.
     * Уже активні анімації не перезапускаються, тому всі сигнали прибувають на спільній межі такту.
     *
     * @param step актуальний знімок моделі після негайної активації.
     * @param sourceNeuronId ідентифікатор нейрона, активованого користувачем.
     * @param remainingMillis час до завершення поточного такту в мілісекундах.
     * @throws NullPointerException якщо знімок або ідентифікатор нейрона не задано.
     * @throws IllegalArgumentException якщо залишок часу не є скінченним додатним числом.
     */
    public void applyImmediateActivation(
            SimulationStep step,
            String sourceNeuronId,
            double remainingMillis
    ) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(sourceNeuronId, "sourceNeuronId");
        if (!Double.isFinite(remainingMillis) || remainingMillis <= 0.0) {
            throw new IllegalArgumentException("remaining duration must be finite and positive");
        }
        restoreVisualState(step);
        animateOutgoingConnections(sourceNeuronId, remainingMillis);
    }

    /**
     * Відновлює індикатори поточного такту й лічильники нейронів, які активуються від сигналів у дорозі, без запуску нових імпульсів.
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
        showRemainingActiveTicks(step.remainingActiveTicks());
        showPendingActivationCounters(step.nextActivatedNeuronIds());
        for (String neuronId : step.emittingNeuronIds()) {
            highlightOutputSignal(neuronId);
        }
    }

    /** Прибирає індикатори сигналів, залишки активних тактів і анімації завершеного сеансу. */
    public void clearRuntime() {
        clearDisplayedInputSignals();
        clearDisplayedRemainingActiveTicks();
        clearOutputSignalHighlights();
        pulseAnimations.stopAll();
    }

    /**
     * Завершує візуальну доставку імпульсів попереднього такту перед обробкою сигналів на новій межі.
     */
    public void completeAnimationsAtTickBoundary() {
        pulseAnimations.completeAtTickBoundary();
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

    /** Оновлює числові індикатори залишку активних тактів для всіх нейронів. */
    private void showRemainingActiveTicks(Map<String, Integer> remainingActiveTicks) {
        neuronViews.forEach((neuronId, neuronView) ->
                neuronView.setRemainingActiveTicks(remainingActiveTicks.getOrDefault(neuronId, 0)));
    }

    /**
     * Негайно показує повний лічильник нейронів, які досягли порога від сигналів, що вже прямують до них.
     * Саму активацію симуляція виконає на найближчій межі такту; тут відображається її вже відомий результат.
     *
     * @param neuronIds нейрони, для яких запланована сума вхідних сигналів перетинає поріг активації.
     */
    private void showPendingActivationCounters(Set<String> neuronIds) {
        for (String neuronId : neuronIds) {
            NeuronView neuronView = neuronViews.get(neuronId);
            if (neuronView != null) {
                neuronView.setRemainingActiveTicks(neuronView.model().activeTicks());
            }
        }
    }

    /** Скидає залишок активних тактів усіх нейронів після очищення runtime-стану. */
    private void clearDisplayedRemainingActiveTicks() {
        neuronViews.values().forEach(neuronView -> neuronView.setRemainingActiveTicks(0));
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

    /** Запускає анімації на поточних вихідних зв'язках указаного нейрона зі стандартною тривалістю такту. */
    private void animateOutgoingConnections(String neuronId) {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView
                    && connectionView.model().sourceId().equals(neuronId)) {
                pulseAnimations.play(connectionView);
            }
        }
    }

    /** Запускає анімації вихідних зв'язків нейрона з індивідуальною тривалістю. */
    private void animateOutgoingConnections(String neuronId, double durationMillis) {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView
                    && connectionView.model().sourceId().equals(neuronId)) {
                pulseAnimations.play(connectionView, durationMillis);
            }
        }
    }
}
