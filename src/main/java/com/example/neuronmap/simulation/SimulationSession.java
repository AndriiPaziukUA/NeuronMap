package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronMapModel;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Обробляє глобальні такти симуляції, накопичує вхідні сигнали та керує затримкою і тривалістю їх передавання.
 * Для активованих внутрішніх нейронів готує спільні центри цільових структур до випуску вихідних сигналів.
 */
public final class SimulationSession {

    private final NeuronMapModel model;
    private final StructureSignalIndicatorCalculator structureSignalIndicators;
    private final StructureActivationCoordinator structureActivationCoordinator;
    private final StrongestNeuronSelector strongestNeuronSelector;
    private final Map<String, Integer> pendingSignals = new LinkedHashMap<>();
    private final Set<String> pendingManualStarts = new LinkedHashSet<>();
    private final Map<String, Integer> remainingActiveTicks = new LinkedHashMap<>();
    private final Map<String, Integer> remainingEmissionDelayTicks = new LinkedHashMap<>();
    private final Set<String> emittingNeuronIdsThisTick = new LinkedHashSet<>();

    private SimulationTick tick = new SimulationTick();
    private SimulationStep lastStep;
    private boolean finished;

    /**
     * Створює сеанс симуляції для моделі.
     *
     * @param model модель, поширення сигналів у якій обчислюватиме сеанс.
     */
    private SimulationSession(NeuronMapModel model) {
        this.model = model;
        structureSignalIndicators = new StructureSignalIndicatorCalculator(model);
        structureActivationCoordinator = new StructureActivationCoordinator(model);
        strongestNeuronSelector = new StrongestNeuronSelector(model);
    }

    /**
     * Створює сеанс симуляції та ставить звичайний нейрон у чергу ручного запуску.
     *
     * @param model модель карти нейронів.
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     * @return новий сеанс симуляції.
     * @throws IllegalArgumentException якщо модель не задано.
     */
    public static SimulationSession manual(NeuronMapModel model, String sourceNeuronId) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        SimulationSession session = new SimulationSession(model);
        session.queueManualStart(sourceNeuronId);
        return session;
    }

    /**
     * Додає звичайний нейрон до черги запуску на наступному такті; повторне додавання не створює дубліката.
     * Контейнери структур не можуть бути джерелами сигналу. Якщо сеанс уже завершено, старі сигнали відкидаються.
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     * @return {@code true}, якщо нейрон прийнято до черги; інакше {@code false}.
     */
    public boolean queueManualStart(String sourceNeuronId) {
        if (sourceNeuronId == null || sourceNeuronId.isBlank()
                || !isSignalNeuron(model.neuron(sourceNeuronId))) {
            return false;
        }
        if (finished) {
            pendingSignals.clear();
        }
        pendingManualStarts.add(sourceNeuronId);
        finished = false;
        return true;
    }

    /**
     * Активує звичайний нейрон у поточному такті без просування глобального лічильника.
     * Перед першим вихідним сигналом готує спільні центри структур, до яких ведуть структурні зв'язки.
     *
     * @param sourceNeuronId ідентифікатор нейрона для негайної активації.
     * @return знімок поточного стану або {@code null}, якщо нейрон відсутній, є структурою
     *         чи вже передавав сигнал цього такту.
     */
    public SimulationStep activateManualStartNow(String sourceNeuronId) {
        if (sourceNeuronId == null || sourceNeuronId.isBlank()) {
            return null;
        }
        Neuron sourceNeuron = model.neuron(sourceNeuronId);
        if (!isSignalNeuron(sourceNeuron)) {
            return null;
        }

        pendingManualStarts.remove(sourceNeuronId);
        if (emittingNeuronIdsThisTick.contains(sourceNeuronId)) {
            return null;
        }
        structureActivationCoordinator.prepareSharedCenters(Set.of(sourceNeuronId));
        scheduleActivationDelay(Set.of(sourceNeuronId));
        if (remainingEmissionDelayTicks.get(sourceNeuronId) == 0) {
            emitFirstSignalImmediately(sourceNeuron);
        }
        updateFinishedStateAfterImmediateActivation();
        lastStep = createImmediateActivationStep(sourceNeuron.id());
        return lastStep;
    }

    /** Планує затримку для щойно активованих звичайних нейронів, не витрачаючи їхніх активних тактів. */
    private void scheduleActivationDelay(Set<String> activatedNeuronIds) {
        for (String neuronId : activatedNeuronIds) {
            Neuron neuron = model.neuron(neuronId);
            if (!isSignalNeuron(neuron)) {
                continue;
            }
            remainingActiveTicks.remove(neuronId);
            remainingEmissionDelayTicks.put(neuronId, neuron.delayTicks() - 1);
        }
    }

    /** Передає перший сигнал після затримки та запускає лічильник активних тактів звичайного нейрона. */
    private void emitFirstSignalImmediately(Neuron neuron) {
        remainingEmissionDelayTicks.remove(neuron.id());
        remainingActiveTicks.put(neuron.id(), neuron.activeTicks());
        collectOutgoingSignals(neuron, pendingSignals);
        consumeActivationTick(neuron.id());
        emittingNeuronIdsThisTick.add(neuron.id());
    }

    /** Оновлює завершення негайного ручного запуску з урахуванням сигналів і незавершених затримок. */
    private void updateFinishedStateAfterImmediateActivation() {
        finished = !hasPendingWork();
        if (finished) {
            remainingActiveTicks.clear();
            remainingEmissionDelayTicks.clear();
        }
    }

    /** Формує знімок ручної активації, зберігаючи актуальні індикатори та розрахунки структур. */
    private SimulationStep createImmediateActivationStep(String sourceNeuronId) {
        Set<String> currentActivations = new LinkedHashSet<>();
        Set<String> currentEmitters = new LinkedHashSet<>();
        Map<String, Integer> currentInputs = Map.of();
        BigInteger currentTick = tick.value().signum() > 0
                ? tick.value().subtract(BigInteger.ONE)
                : BigInteger.ZERO;
        if (lastStep != null) {
            currentInputs = lastStep.inputSums();
            currentTick = lastStep.tick();
            currentActivations.addAll(lastStep.activatedNeuronIds());
            currentEmitters.addAll(lastStep.emittingNeuronIds());
        }
        currentActivations.add(sourceNeuronId);
        currentEmitters.addAll(emittingNeuronIdsThisTick);

        Set<String> nextActivatedNeuronIds = new LinkedHashSet<>();
        collectThresholdActivations(pendingSignals, nextActivatedNeuronIds);
        return createStep(
                currentTick,
                currentInputs,
                currentActivations,
                currentEmitters,
                pendingSignals,
                remainingActiveTicks,
                nextActivatedNeuronIds
        );
    }

    /** Перевіряє, чи завершено сеанс. */
    public boolean isFinished() {
        return finished;
    }

    /** Повертає номер наступного глобального такту симуляції. */
    public BigInteger tick() {
        return tick.value();
    }

    /**
     * Перевіряє, чи є робота для наступного такту, включно з пороговими активаціями та затримками.
     *
     * @return {@code true}, якщо є очікувана робота; інакше {@code false}.
     */
    public boolean hasPendingWork() {
        return hasPendingThresholdActivation()
                || !pendingManualStarts.isEmpty()
                || !remainingEmissionDelayTicks.isEmpty()
                || hasRemainingEmissionWork();
    }

    /** Перевіряє наявність сигналу, який активує щонайменше один звичайний нейрон. */
    private boolean hasPendingThresholdActivation() {
        Set<String> activatedNeuronIds = new LinkedHashSet<>();
        collectThresholdActivations(pendingSignals, activatedNeuronIds);
        return !activatedNeuronIds.isEmpty();
    }

    /** Перевіряє, чи має хоча б один звичайний нейрон із залишком активних тактів чинний вихідний зв'язок. */
    private boolean hasRemainingEmissionWork() {
        for (String neuronId : remainingActiveTicks.keySet()) {
            for (Connection connection : model.connections()) {
                if (isOutgoingConnectionToExistingNeuron(connection, neuronId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Перевіряє, чи веде зв'язок від звичайного нейрона до наявного звичайного нейрона призначення.
     * Структурні зв'язки не є каналами прямого передавання сигналів.
     *
     * @param connection зв'язок для перевірки.
     * @param sourceNeuronId ідентифікатор нейрона-випромінювача.
     * @return {@code true}, якщо зв'язок є чинним нейронним виходом; інакше {@code false}.
     */
    private boolean isOutgoingConnectionToExistingNeuron(Connection connection, String sourceNeuronId) {
        return connection.sourceId().equals(sourceNeuronId)
                && isSignalNeuron(model.neuron(connection.sourceId()))
                && isSignalNeuron(model.neuron(connection.targetId()));
    }

    /**
     * Обробляє один такт, доставляє сигнали й обчислює індикатори для зовнішніх структурних зв'язків.
     * Спільні центри цільових структур створюються до передавання сигналів активованих нейронів.
     *
     * @return незмінний результат такту або {@code null}, якщо роботи немає.
     */
    public SimulationStep nextStep() {
        if (finished || !hasPendingWork()) {
            finished = true;
            remainingActiveTicks.clear();
            remainingEmissionDelayTicks.clear();
            return null;
        }

        emittingNeuronIdsThisTick.clear();
        Map<String, Integer> inputSums = new LinkedHashMap<>(pendingSignals);
        inputSums.keySet().removeIf(neuronId -> !isSignalNeuron(model.neuron(neuronId)));
        pendingSignals.clear();
        Set<String> manualStarts = new LinkedHashSet<>(pendingManualStarts);
        pendingManualStarts.clear();

        Set<String> activatedNeuronIds = new LinkedHashSet<>();
        Map<String, Integer> nextSignals = new LinkedHashMap<>();
        collectManualActivations(manualStarts, activatedNeuronIds);
        collectThresholdActivations(inputSums, activatedNeuronIds);
        structureActivationCoordinator.prepareSharedCenters(activatedNeuronIds);
        scheduleActivationDelay(activatedNeuronIds);
        Set<String> emittingNeuronIds = emitReadySignals(activatedNeuronIds, nextSignals);
        emittingNeuronIdsThisTick.addAll(emittingNeuronIds);
        Set<String> nextActivatedNeuronIds = new LinkedHashSet<>();
        collectThresholdActivations(nextSignals, nextActivatedNeuronIds);

        pendingSignals.putAll(nextSignals);
        finished = !hasPendingWork();
        if (finished) {
            remainingActiveTicks.clear();
            remainingEmissionDelayTicks.clear();
        }

        SimulationStep step = createStep(
                tick.value(), inputSums, activatedNeuronIds, emittingNeuronIds,
                nextSignals, remainingActiveTicks, nextActivatedNeuronIds);
        lastStep = step;
        tick = tick.next();
        return step;
    }

    /** Додає до набору активацій лише наявні звичайні нейрони з ручної черги. */
    private void collectManualActivations(Set<String> manualStarts, Set<String> activatedNeuronIds) {
        for (String neuronId : manualStarts) {
            Neuron neuron = model.neuron(neuronId);
            if (isSignalNeuron(neuron)) {
                activatedNeuronIds.add(neuron.id());
            }
        }
    }

    /**
     * Відбирає нейрони, що пройшли поріг, і застосовує до них режим найсильнішого нейрона кожної структури.
     * Явні ручні запуски передаються окремим шляхом і не вилучаються з набору автоматичних кандидатів.
     *
     * @param inputSums суми сигналів, доставлені кандидатам у поточному наборі.
     * @param activatedNeuronIds набір, до якого додаються автоматично активовані нейрони.
     */
    private void collectThresholdActivations(Map<String, Integer> inputSums, Set<String> activatedNeuronIds) {
        Set<String> eligibleNeuronIds = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : inputSums.entrySet()) {
            Neuron neuron = model.neuron(entry.getKey());
            if (isSignalNeuron(neuron) && hasReachedActivationThreshold(neuron, entry.getValue())) {
                eligibleNeuronIds.add(neuron.id());
            }
        }
        activatedNeuronIds.addAll(strongestNeuronSelector.selectWinners(inputSums, eligibleNeuronIds));
    }

    /** Визначає, чи досягнуто порога звичайного нейрона в режимі сили або кількості вхідних сигналів. */
    private boolean hasReachedActivationThreshold(Neuron neuron, int inputValue) {
        if (neuron.activationMode() == NeuronActivationMode.SIGNAL_STRENGTH) {
            return inputValue >= neuron.activationThreshold();
        }
        return inputValue >= requiredActivatingSignalCount(neuron);
    }

    /**
     * Обчислює мінімальну кількість активних входів за відсотковим порогом.
     * До знаменника входять лише звичайні збуджувальні нейрони; структурні контейнери не є джерелами сигналу.
     *
     * @param neuron нейрон, чий поріг потрібно оцінити.
     * @return мінімальна кількість активуючих сигналів, округлена вгору, але не менша за одиницю.
     */
    private int requiredActivatingSignalCount(Neuron neuron) {
        int activatingInputCount = 0;
        for (Connection connection : model.connections()) {
            if (!connection.targetId().equals(neuron.id())) {
                continue;
            }
            Neuron source = model.neuron(connection.sourceId());
            if (isSignalNeuron(source) && source.type().isActivating()) {
                activatingInputCount++;
            }
        }

        long percentageProduct = (long) activatingInputCount * neuron.activationThreshold();
        long roundedUpCount = (percentageProduct + 99L) / 100L;
        return (int) Math.max(1L, roundedUpCount);
    }

    /** Витрачає один активний такт лише після фактичного випуску звичайним нейроном вихідного сигналу. */
    private void consumeActivationTick(String neuronId) {
        int remaining = remainingActiveTicks.getOrDefault(neuronId, 0) - 1;
        if (remaining <= 0) {
            remainingActiveTicks.remove(neuronId);
        } else {
            remainingActiveTicks.put(neuronId, remaining);
        }
    }

    /** Передає сигнали звичайних нейронів, у яких завершилася затримка або триває період активності. */
    private Set<String> emitReadySignals(Set<String> activatedNeuronIds, Map<String, Integer> nextSignals) {
        Set<String> candidates = new LinkedHashSet<>(activatedNeuronIds);
        candidates.addAll(remainingActiveTicks.keySet());
        candidates.addAll(remainingEmissionDelayTicks.keySet());
        Set<String> emittingNeuronIds = new LinkedHashSet<>();

        for (String neuronId : candidates) {
            Neuron neuron = model.neuron(neuronId);
            if (!isSignalNeuron(neuron)) {
                remainingActiveTicks.remove(neuronId);
                remainingEmissionDelayTicks.remove(neuronId);
                continue;
            }
            if (shouldWaitForEmissionDelay(neuronId)) {
                continue;
            }
            if (!remainingActiveTicks.containsKey(neuronId)) {
                remainingActiveTicks.put(neuronId, neuron.activeTicks());
            }
            collectOutgoingSignals(neuron, nextSignals);
            emittingNeuronIds.add(neuronId);
            consumeActivationTick(neuronId);
        }
        return emittingNeuronIds;
    }

    /** Зменшує очікування на один такт і повертає {@code true}, якщо сигнал ще не можна передавати. */
    private boolean shouldWaitForEmissionDelay(String neuronId) {
        Integer remainingDelay = remainingEmissionDelayTicks.get(neuronId);
        if (remainingDelay == null) {
            return false;
        }
        if (remainingDelay > 0) {
            remainingEmissionDelayTicks.put(neuronId, remainingDelay - 1);
            return true;
        }
        remainingEmissionDelayTicks.remove(neuronId);
        return false;
    }

    /** Додає сигнали до звичайних цільових нейронів; структурні контейнери залишаються поза прямим потоком сигналів. */
    private void collectOutgoingSignals(Neuron neuron, Map<String, Integer> nextSignals) {
        if (!isSignalNeuron(neuron)) {
            return;
        }
        for (Connection connection : model.connections()) {
            if (!isOutgoingConnectionToExistingNeuron(connection, neuron.id())) {
                continue;
            }
            Neuron target = model.neuron(connection.targetId());
            int signalValue = target.activationMode() == NeuronActivationMode.SIGNAL_COUNT
                    ? Integer.signum(neuron.signalWeight())
                    : neuron.signalWeight();
            nextSignals.merge(target.id(), signalValue, Integer::sum);
        }
    }

    /**
     * Створює результат такту разом із похідними індикаторами всіх структур.
     *
     * @param stepTick номер такту, який описує результат.
     * @param inputSums суми сигналів, доставлених у цьому такті.
     * @param activatedNeuronIds нейрони, які активувалися в цьому такті.
     * @param emittingNeuronIds нейрони, які фактично випустили сигнал.
     * @param nextInputSums суми сигналів, запланованих на наступний такт.
     * @param activeTicks залишки активних тактів нейронів.
     * @param nextActivatedNeuronIds нейрони, чий вхідний індикатор засвітиться в наступному стані.
     * @return незмінний знімок такту з агрегованими індикаторами структур.
     */
    private SimulationStep createStep(
            BigInteger stepTick,
            Map<String, Integer> inputSums,
            Set<String> activatedNeuronIds,
            Set<String> emittingNeuronIds,
            Map<String, Integer> nextInputSums,
            Map<String, Integer> activeTicks,
            Set<String> nextActivatedNeuronIds
    ) {
        StructureSignalIndicatorCalculator.Summary summary =
                structureSignalIndicators.calculate(nextActivatedNeuronIds, emittingNeuronIds);
        return new SimulationStep(stepTick, inputSums, activatedNeuronIds, emittingNeuronIds,
                nextInputSums, activeTicks, nextActivatedNeuronIds,
                summary.structureInputActivationCounts(),
                summary.structureEmittingNeuronCounts(),
                summary.structureConnectionIdsToAnimate());
    }

    /**
     * Перевіряє, що нейрон є звичайним учасником, а не індикаторним контейнером структури.
     *
     * @param neuron нейрон для перевірки або {@code null}, якщо він відсутній.
     * @return {@code true}, якщо нейрон може приймати та випускати сигнали.
     */
    private boolean isSignalNeuron(Neuron neuron) {
        return neuron != null && neuron.type().participatesInSignalPropagation();
    }
}
