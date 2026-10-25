package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Обробляє глобальні такти симуляції, накопичує вхідні сигнали та керує затримкою і тривалістю їх передавання.
 * Лічильник активних тактів запускається лише в такті фактичного випуску першого вихідного сигналу.
 */
public final class SimulationSession {

    private final NeuronMapModel model;
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
     * @param model модель карти нейронів.
     */
    private SimulationSession(NeuronMapModel model) {
        this.model = model;
    }

    /**
     * Створює сеанс симуляції та ставить початковий нейрон у чергу ручного запуску.
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
     * Додає наявний нейрон до черги запуску на наступному такті; повторне додавання не створює дубліката.
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     * @return {@code true}, якщо нейрон прийнято до черги; інакше {@code false}.
     */
    public boolean queueManualStart(String sourceNeuronId) {
        if (sourceNeuronId == null
                || sourceNeuronId.isBlank()
                || model.neuron(sourceNeuronId) == null) {
            return false;
        }
        pendingManualStarts.add(sourceNeuronId);
        finished = false;
        return true;
    }

    /**
     * Активує нейрон у поточному такті без просування глобального лічильника.
     * Якщо затримка перевищує один такт, перший вихідний сигнал буде передано після завершення затримки.
     *
     * @param sourceNeuronId ідентифікатор нейрона для негайної активації.
     * @return знімок поточного стану або {@code null}, якщо нейрон відсутній чи вже передавав сигнал цього такту.
     */
    public SimulationStep activateManualStartNow(String sourceNeuronId) {
        if (sourceNeuronId == null || sourceNeuronId.isBlank()) {
            return null;
        }
        Neuron sourceNeuron = model.neuron(sourceNeuronId);
        if (sourceNeuron == null) {
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

    /** Планує затримку для щойно активованих нейронів, не витрачаючи їхніх активних тактів. */
    private void scheduleActivationDelay(Set<String> activatedNeuronIds) {
        for (String neuronId : activatedNeuronIds) {
            Neuron neuron = model.neuron(neuronId);
            if (neuron == null) {
                continue;
            }
            remainingActiveTicks.remove(neuronId);
            remainingEmissionDelayTicks.put(neuronId, neuron.delayTicks() - 1);
        }
    }

    /** Передає перший сигнал після затримки та запускає лічильник активних тактів. */
    private void emitFirstSignalImmediately(Neuron neuron) {
        remainingEmissionDelayTicks.remove(neuron.id());
        remainingActiveTicks.put(neuron.id(), neuron.activeTicks());
        collectOutgoingSignals(neuron, pendingSignals);
        consumeActivationTick(neuron.id());
        emittingNeuronIdsThisTick.add(neuron.id());
    }

    /** Оновлює стан завершення негайного ручного запуску з урахуванням сигналів і незавершених затримок. */
    private void updateFinishedStateAfterImmediateActivation() {
        finished = !hasPendingWork();
        if (finished) {
            remainingActiveTicks.clear();
            remainingEmissionDelayTicks.clear();
        }
    }

    /** Формує знімок поточного такту, не позначаючи нейрон випромінювачем до фактичної передачі сигналу. */
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
        return new SimulationStep(
                currentTick,
                currentInputs,
                currentActivations,
                currentEmitters,
                pendingSignals,
                remainingActiveTicks,
                nextActivatedNeuronIds
        );
    }

    /**
     * Перевіряє, чи завершено сеанс через відсутність сигналів, ручних запусків і затриманих нейронів.
     *
     * @return {@code true}, якщо сеанс завершено; інакше {@code false}.
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Повертає номер наступного глобального такту симуляції.
     *
     * @return номер наступного глобального такту.
     */
    public BigInteger tick() {
        return tick.value();
    }

    /**
     * Перевіряє, чи є робота для наступного такту, включно з нейронами, що очікують завершення затримки.
     *
     * @return {@code true}, якщо є очікувана робота; інакше {@code false}.
     */
    public boolean hasPendingWork() {
        return !pendingSignals.isEmpty()
                || !pendingManualStarts.isEmpty()
                || !remainingEmissionDelayTicks.isEmpty();
    }

    /**
     * Обробляє один такт, доставляє сигнали та враховує затримку перед початком активних тактів.
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
        pendingSignals.clear();
        Set<String> manualStarts = new LinkedHashSet<>(pendingManualStarts);
        pendingManualStarts.clear();

        Set<String> activatedNeuronIds = new LinkedHashSet<>();
        Map<String, Integer> nextSignals = new LinkedHashMap<>();
        collectManualActivations(manualStarts, activatedNeuronIds);
        collectThresholdActivations(inputSums, activatedNeuronIds);
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

        SimulationStep step = new SimulationStep(
                tick.value(),
                inputSums,
                activatedNeuronIds,
                emittingNeuronIds,
                nextSignals,
                remainingActiveTicks,
                nextActivatedNeuronIds
        );
        lastStep = step;
        tick = tick.next();
        return step;
    }

    /** Додає наявні нейрони з ручної черги до набору активацій поточного такту. */
    private void collectManualActivations(Set<String> manualStarts, Set<String> activatedNeuronIds) {
        for (String neuronId : manualStarts) {
            Neuron neuron = model.neuron(neuronId);
            if (neuron != null) {
                activatedNeuronIds.add(neuron.id());
            }
        }
    }

    /** Додає нейрони, чия сума вхідних сигналів досягла налаштованого порога активації. */
    private void collectThresholdActivations(
            Map<String, Integer> inputSums,
            Set<String> activatedNeuronIds
    ) {
        for (Map.Entry<String, Integer> entry : inputSums.entrySet()) {
            Neuron neuron = model.neuron(entry.getKey());
            if (neuron != null && entry.getValue() >= neuron.activationThreshold()) {
                activatedNeuronIds.add(neuron.id());
            }
        }
    }

    /** Витрачає один активний такт тільки після того, як нейрон фактично передав вихідний сигнал. */
    private void consumeActivationTick(String neuronId) {
        int remaining = remainingActiveTicks.getOrDefault(neuronId, 0) - 1;
        if (remaining <= 0) {
            remainingActiveTicks.remove(neuronId);
        } else {
            remainingActiveTicks.put(neuronId, remaining);
        }
    }

    /** Передає сигнали нейронів, у яких завершилася затримка або вже триває період активності. */
    private Set<String> emitReadySignals(Set<String> activatedNeuronIds, Map<String, Integer> nextSignals) {
        Set<String> candidates = new LinkedHashSet<>(activatedNeuronIds);
        candidates.addAll(remainingActiveTicks.keySet());
        candidates.addAll(remainingEmissionDelayTicks.keySet());
        Set<String> emittingNeuronIds = new LinkedHashSet<>();

        for (String neuronId : candidates) {
            Neuron neuron = model.neuron(neuronId);
            if (neuron == null) {
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

    /** Додає вагу сигналу до кожного наявного нейрона, з яким з'єднаний випромінювач. */
    private void collectOutgoingSignals(Neuron neuron, Map<String, Integer> nextSignals) {
        for (Connection connection : model.connections()) {
            if (!connection.sourceId().equals(neuron.id())) {
                continue;
            }
            if (model.neuron(connection.targetId()) == null) {
                continue;
            }
            nextSignals.merge(connection.targetId(), neuron.signalWeight(), Integer::sum);
        }
    }
}
