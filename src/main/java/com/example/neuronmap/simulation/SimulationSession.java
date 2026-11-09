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
 * Адаптивна сила змінюється лише під час доставки сигналу й лише після проходження порога активації.
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

    /** Створює сеанс симуляції для моделі. */
    private SimulationSession(NeuronMapModel model) {
        this.model = model;
        structureSignalIndicators = new StructureSignalIndicatorCalculator(model);
        structureActivationCoordinator = new StructureActivationCoordinator(model);
        strongestNeuronSelector = new StrongestNeuronSelector(model);
    }

    /** Створює сеанс симуляції та ставить звичайний нейрон у чергу ручного запуску. */
    public static SimulationSession manual(NeuronMapModel model, String sourceNeuronId) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        SimulationSession session = new SimulationSession(model);
        session.queueManualStart(sourceNeuronId);
        return session;
    }

    /** Додає звичайний нейрон до черги запуску на наступному такті. */
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

    /** Активує звичайний нейрон у поточному такті без просування глобального лічильника. */
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
        scheduleActivationDelay(Set.of(sourceNeuronId));
        if (remainingEmissionDelayTicks.get(sourceNeuronId) == 0) {
            emitFirstSignalImmediately(sourceNeuron);
        }
        updateFinishedStateAfterImmediateActivation();
        lastStep = createImmediateActivationStep(sourceNeuron.id());
        return lastStep;
    }

    /** Планує затримку для щойно активованих нейронів. */
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

    /** Передає перший сигнал після фактичного завершення затримки. */
    private void emitFirstSignalImmediately(Neuron neuron) {
        remainingEmissionDelayTicks.remove(neuron.id());
        remainingActiveTicks.put(neuron.id(), neuron.activeTicks());
        emitSignalsForReadyNeurons(Set.of(neuron.id()), pendingSignals);
        emittingNeuronIdsThisTick.add(neuron.id());
    }

    /** Оновлює ознаку завершення негайного ручного запуску. */
    private void updateFinishedStateAfterImmediateActivation() {
        finished = !hasPendingWork();
        if (finished) {
            remainingActiveTicks.clear();
            remainingEmissionDelayTicks.clear();
        }
    }

    /** Формує знімок ручної активації без навчання за ще не доставленими сигналами. */
    private SimulationStep createImmediateActivationStep(String sourceNeuronId) {
        Set<String> currentActivations = new LinkedHashSet<>();
        Set<String> currentEmitters = new LinkedHashSet<>();
        Map<String, Integer> currentInputs = Map.of();
        BigInteger currentTick = tick.value().signum() > 0
                ? tick.value().subtract(BigInteger.ONE) : BigInteger.ZERO;
        if (lastStep != null) {
            currentInputs = lastStep.inputSums();
            currentTick = lastStep.tick();
            currentActivations.addAll(lastStep.activatedNeuronIds());
            currentEmitters.addAll(lastStep.emittingNeuronIds());
        }
        currentActivations.add(sourceNeuronId);
        currentEmitters.addAll(emittingNeuronIdsThisTick);
        Set<String> nextActivatedNeuronIds = new LinkedHashSet<>();
        collectThresholdActivations(pendingSignals, nextActivatedNeuronIds, false);
        return createStep(currentTick, currentInputs, currentActivations, currentEmitters,
                pendingSignals, remainingActiveTicks, nextActivatedNeuronIds);
    }

    /** Перевіряє, чи завершено сеанс. */
    public boolean isFinished() { return finished; }

    /** Повертає номер наступного глобального такту симуляції. */
    public BigInteger tick() { return tick.value(); }

    /** Перевіряє, чи є робота для наступного такту. */
    public boolean hasPendingWork() {
        return hasPendingThresholdActivation() || !pendingManualStarts.isEmpty()
                || !remainingEmissionDelayTicks.isEmpty() || hasRemainingEmissionWork();
    }

    /** Перевіряє наявність доставленого сигналу, який активує звичайний нейрон. */
    private boolean hasPendingThresholdActivation() {
        Set<String> activatedNeuronIds = new LinkedHashSet<>();
        collectThresholdActivations(pendingSignals, activatedNeuronIds, false);
        return !activatedNeuronIds.isEmpty();
    }

    /** Перевіряє наявність чинного вихідного зв'язку в нейрона із залишком активних тактів. */
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

    /** Перевіряє, що зв'язок веде від звичайного нейрона до наявного звичайного нейрона. */
    private boolean isOutgoingConnectionToExistingNeuron(Connection connection, String sourceNeuronId) {
        return connection.sourceId().equals(sourceNeuronId)
                && isSignalNeuron(model.neuron(connection.sourceId()))
                && isSignalNeuron(model.neuron(connection.targetId()));
    }

    /** Обробляє один такт, доставляє сигнали й обчислює індикатори структур. */
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
        // Лише цей виклик обробляє вже доставлені сигнали й може змінити персональну силу нейрона.
        collectThresholdActivations(inputSums, activatedNeuronIds, true);
        scheduleActivationDelay(activatedNeuronIds);
        Set<String> emittingNeuronIds = emitReadySignals(activatedNeuronIds, nextSignals);
        emittingNeuronIdsThisTick.addAll(emittingNeuronIds);
        Set<String> nextActivatedNeuronIds = new LinkedHashSet<>();
        // Прогноз наступного такту не повинен передчасно змінювати стан моделі.
        collectThresholdActivations(nextSignals, nextActivatedNeuronIds, false);
        pendingSignals.putAll(nextSignals);
        finished = !hasPendingWork();
        if (finished) {
            remainingActiveTicks.clear();
            remainingEmissionDelayTicks.clear();
        }
        SimulationStep step = createStep(tick.value(), inputSums, activatedNeuronIds, emittingNeuronIds,
                nextSignals, remainingActiveTicks, nextActivatedNeuronIds);
        lastStep = step;
        tick = tick.next();
        return step;
    }

    /** Додає до набору активацій звичайні нейрони з ручної черги. */
    private void collectManualActivations(Set<String> manualStarts, Set<String> activatedNeuronIds) {
        for (String neuronId : manualStarts) {
            Neuron neuron = model.neuron(neuronId);
            if (isSignalNeuron(neuron)) {
                activatedNeuronIds.add(neuron.id());
            }
        }
    }

    /**
     * Відбирає нейрони, що пройшли поріг, і за потреби навчає їх за фактично доставленою сумою сигналу.
     * Навчання виконується до відбору переможців структури, адже сам факт проходження порога є достатньою умовою.
     *
     * @param inputSums суми сигналів, доставлені кандидатам.
     * @param activatedNeuronIds набір автоматично активованих нейронів.
     * @param updateAdaptiveStrength чи дозволено змінювати персональну силу сигналу.
     */
    private void collectThresholdActivations(Map<String, Integer> inputSums, Set<String> activatedNeuronIds,
                                             boolean updateAdaptiveStrength) {
        Set<String> eligibleNeuronIds = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : inputSums.entrySet()) {
            Neuron neuron = model.neuron(entry.getKey());
            int inputValue = entry.getValue();
            if (!isSignalNeuron(neuron) || !hasReachedActivationThreshold(neuron, inputValue)) {
                continue;
            }
            if (updateAdaptiveStrength) {
                neuron.learnSignalStrength(inputValue);
            }
            eligibleNeuronIds.add(neuron.id());
        }
        activatedNeuronIds.addAll(strongestNeuronSelector.selectWinners(inputSums, eligibleNeuronIds));
    }

    /** Визначає, чи досягнуто порога нейрона в режимі сили або кількості вхідних сигналів. */
    private boolean hasReachedActivationThreshold(Neuron neuron, int inputValue) {
        if (neuron.activationMode() == NeuronActivationMode.SIGNAL_STRENGTH) {
            return inputValue >= neuron.activationThreshold();
        }
        return inputValue >= requiredActivatingSignalCount(neuron);
    }

    /** Обчислює мінімальну кількість активних входів за відсотковим порогом. */
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

    /** Витрачає один активний такт після фактичного випуску сигналу. */
    private void consumeActivationTick(String neuronId) {
        int remaining = remainingActiveTicks.getOrDefault(neuronId, 0) - 1;
        if (remaining <= 0) {
            remainingActiveTicks.remove(neuronId);
        } else {
            remainingActiveTicks.put(neuronId, remaining);
        }
    }

    /** Визначає готових випромінювачів і передає їхні сигнали після завершення затримки. */
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
            emittingNeuronIds.add(neuronId);
        }
        emitSignalsForReadyNeurons(emittingNeuronIds, nextSignals);
        return emittingNeuronIds;
    }

    /** Готує спільні центри для готових випромінювачів і лише після цього передає їхні сигнали. */
    private void emitSignalsForReadyNeurons(Set<String> emittingNeuronIds, Map<String, Integer> nextSignals) {
        if (emittingNeuronIds.isEmpty()) {
            return;
        }
        structureActivationCoordinator.prepareSharedCenters(emittingNeuronIds);
        for (String neuronId : emittingNeuronIds) {
            collectOutgoingSignals(model.neuron(neuronId), nextSignals);
            consumeActivationTick(neuronId);
        }
    }

    /** Зменшує затримку на один такт і повертає ознаку очікування. */
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

    /** Додає вихідні сигнали до звичайних цільових нейронів, зберігаючи знак гальмівного сигналу. */
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
                    ? Integer.signum(neuron.signalWeight()) : neuron.signalWeight();
            nextSignals.merge(target.id(), signalValue, Integer::sum);
        }
    }

    /** Створює незмінний знімок такту з агрегованими індикаторами структур. */
    private SimulationStep createStep(BigInteger stepTick, Map<String, Integer> inputSums,
                                      Set<String> activatedNeuronIds, Set<String> emittingNeuronIds,
                                      Map<String, Integer> nextInputSums, Map<String, Integer> activeTicks,
                                      Set<String> nextActivatedNeuronIds) {
        StructureSignalIndicatorCalculator.Summary summary =
                structureSignalIndicators.calculate(nextActivatedNeuronIds, emittingNeuronIds);
        return new SimulationStep(stepTick, inputSums, activatedNeuronIds, emittingNeuronIds,
                nextInputSums, activeTicks, nextActivatedNeuronIds,
                summary.structureInputActivationCounts(), summary.structureEmittingNeuronCounts(),
                summary.structureConnectionIdsToAnimate());
    }

    /** Перевіряє, що нейрон є звичайним учасником, а не індикаторним контейнером структури. */
    private boolean isSignalNeuron(Neuron neuron) {
        return neuron != null && neuron.type().participatesInSignalPropagation();
    }
}
