package com.example.neuronmap.view.simulation;

import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.Node;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Відображає результати такту симуляції через індикатори нейронів, структур і анімації сигналів. */
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
     * Відображає результати такту й запускає лише підтверджені анімації сигналів.
     *
     * @param step результат одного такту симуляції.
     */
    public void apply(SimulationStep step) {
        Objects.requireNonNull(step, "step");
        restoreVisualState(step);
        startAnimations(step);
    }

    /**
     * Відображає обчислений такт без запуску його анімацій.
     *
     * @param step результат такту, індикатори якого потрібно оновити.
     */
    public void applyWithoutAnimations(SimulationStep step) {
        restoreVisualState(Objects.requireNonNull(step, "step"));
    }

    /**
     * Запускає анімації звичайних виходів та структурних зв'язків, підтверджених внутрішніми сигналами.
     *
     * @param step результат такту з набором фактичних випромінювачів і дозволених структурних анімацій.
     */
    public void startAnimations(SimulationStep step) {
        Objects.requireNonNull(step, "step");
        for (String neuronId : step.emittingNeuronIds()) {
            animateOutgoingConnections(neuronId);
        }
        animateStructureConnections(step.structureConnectionIdsToAnimate());
    }

    /**
     * Негайно оновлює карту після ручної активації та анімує виходи лише тоді, коли нейрон справді випустив сигнал.
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
        if (step.emittingNeuronIds().contains(sourceNeuronId)) {
            animateOutgoingConnections(sourceNeuronId, remainingMillis);
        }
        animateStructureConnections(step.structureConnectionIdsToAnimate(), remainingMillis);
    }

    /**
     * Відновлює індикатори поточного такту, залишки активності та лічильники структури без запуску анімацій.
     *
     * @param step останній оброблений такт симуляції.
     * @throws NullPointerException якщо результат такту не задано.
     */
    public void restoreVisualState(SimulationStep step) {
        Objects.requireNonNull(step, "step");
        clearDisplayedInputSignals();
        clearOutputSignalHighlights();
        refreshNeuronViews.run();
        showIncomingSignals(step.nextInputSums(), step.nextActivatedNeuronIds());
        showRemainingActiveTicks(step.remainingActiveTicks());
        showPendingActivationCounters(step.nextActivatedNeuronIds());
        for (String neuronId : step.emittingNeuronIds()) {
            highlightOutputSignal(neuronId);
        }
        showStructureSignalCounts(step);
    }

    /** Прибирає індикатори сигналів, лічильники активності й анімації завершеного сеансу. */
    public void clearRuntime() {
        clearDisplayedInputSignals();
        clearDisplayedRemainingActiveTicks();
        clearOutputSignalHighlights();
        pulseAnimations.stopAll();
    }

    /** Завершує візуальну доставку імпульсів попереднього такту перед обробкою сигналів на новій межі. */
    public void completeAnimationsAtTickBoundary() {
        pulseAnimations.completeAtTickBoundary();
    }

    /** Оновлює подання нейронів і зв'язків після зміни моделі. */
    public void refresh() {
        refreshNeuronViews.run();
    }

    /**
     * Оновлює тривалість анімацій відповідно до поточної тривалості такту симуляції.
     *
     * @param tickDurationMillis тривалість одного такту в мілісекундах.
     */
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

    /** Показує вхідні суми звичайних нейронів із фактичним результатом перевірки порога. */
    private void showIncomingSignals(Map<String, Integer> inputSums, Set<String> nextActivatedNeuronIds) {
        inputSums.forEach((neuronId, sum) -> {
            NeuronView neuronView = neuronViews.get(neuronId);
            if (neuronView != null) {
                boolean thresholdReached = neuronView.model().activationMode() == NeuronActivationMode.SIGNAL_STRENGTH
                        ? sum >= neuronView.model().activationThreshold()
                        : nextActivatedNeuronIds.contains(neuronId);
                neuronView.showInputSignal(sum, thresholdReached);
            }
        });
    }

    /** Оновлює індикатори залишку активних тактів звичайних нейронів. */
    private void showRemainingActiveTicks(Map<String, Integer> remainingActiveTicks) {
        neuronViews.forEach((neuronId, neuronView) ->
                neuronView.setRemainingActiveTicks(remainingActiveTicks.getOrDefault(neuronId, 0)));
    }

    /** Негайно показує повний лічильник для нейронів, що активуються в наступному такті. */
    private void showPendingActivationCounters(Set<String> neuronIds) {
        for (String neuronId : neuronIds) {
            NeuronView neuronView = neuronViews.get(neuronId);
            if (neuronView != null) {
                neuronView.setRemainingActiveTicks(neuronView.model().activeTicks());
            }
        }
    }

    /**
     * Задає числа структур за агрегованим станом внутрішніх нейронів, не використовуючи параметри контейнера.
     *
     * @param step результат такту з підрахованими активними входами й випромінювачами кожної структури.
     */
    private void showStructureSignalCounts(SimulationStep step) {
        neuronViews.forEach((neuronId, neuronView) -> {
            if (neuronView.model().type() != NeuronType.STRUCTURE) {
                return;
            }
            int inputActivationCount = step.structureInputActivationCounts().getOrDefault(neuronId, 0);
            int emittingNeuronCount = step.structureEmittingNeuronCounts().getOrDefault(neuronId, 0);
            if (inputActivationCount > 0) {
                neuronView.showInputSignal(inputActivationCount, true);
            } else {
                neuronView.clearDisplayedInputSignal();
            }
            neuronView.setRemainingActiveTicks(emittingNeuronCount);
            neuronView.setOutputSignalActive(emittingNeuronCount > 0);
        });
    }

    /** Скидає числові індикатори залишку активних тактів і структурних лічильників. */
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

    /** Позначає звичайний вихід нейрона активним у поточному такті симуляції. */
    private void highlightOutputSignal(String neuronId) {
        NeuronView neuronView = neuronViews.get(neuronId);
        if (neuronView != null) {
            neuronView.setOutputSignalActive(true);
        }
    }

    /**
     * Запускає імпульси лише на вихідних зв'язках указаного звичайного нейрона.
     *
     * @param neuronId ID нейрона, чиї вихідні зв'язки потрібно анімувати.
     */
    private void animateOutgoingConnections(String neuronId) {
        animateOutgoingConnections(neuronId, pulseAnimations::play);
    }

    /**
     * Запускає імпульси звичайних виходів з індивідуальною тривалістю.
     *
     * @param neuronId ID нейрона, чиї вихідні зв'язки потрібно анімувати.
     * @param durationMillis тривалість анімації в мілісекундах.
     */
    private void animateOutgoingConnections(String neuronId, double durationMillis) {
        animateOutgoingConnections(neuronId, view -> pulseAnimations.play(view, durationMillis));
    }

    /**
     * Знаходить зв'язки за ID джерела й передає їх аніматору, не дублюючи обхід полотна.
     *
     * @param neuronId ID джерела вихідного зв'язку.
     * @param animate дія запуску анімації для кожного знайденого зв'язку.
     */
    private void animateOutgoingConnections(String neuronId, Consumer<ConnectionView> animate) {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView
                    && connectionView.model().sourceId().equals(neuronId)) {
                animate.accept(connectionView);
            }
        }
    }

    /**
     * Анімує лише ті структурні зв'язки, ID яких підтверджені фактичним передаванням внутрішнього сигналу.
     *
     * @param connectionIds ID структурних зв'язків, підтверджених обчисленням симуляції.
     */
    private void animateStructureConnections(Set<String> connectionIds) {
        animateStructureConnections(connectionIds, pulseAnimations::play);
    }

    /**
     * Запускає підтверджені структурні імпульси на залишок поточного такту.
     *
     * @param connectionIds ID структурних зв'язків, підтверджених обчисленням симуляції.
     * @param durationMillis залишок часу поточного такту в мілісекундах.
     */
    private void animateStructureConnections(Set<String> connectionIds, double durationMillis) {
        animateStructureConnections(connectionIds, view -> pulseAnimations.play(view, durationMillis));
    }

    /**
     * Знаходить лише структурні зв'язки з переданого списку ID і передає їх аніматору.
     *
     * @param connectionIds ID зв'язків, які підтверджені внутрішньою передачею сигналу.
     * @param animate дія запуску анімації для кожного знайденого зв'язку.
     */
    private void animateStructureConnections(Set<String> connectionIds, Consumer<ConnectionView> animate) {
        if (connectionIds.isEmpty()) {
            return;
        }
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView
                    && connectionIds.contains(connectionView.model().id())) {
                animate.accept(connectionView);
            }
        }
    }

}
