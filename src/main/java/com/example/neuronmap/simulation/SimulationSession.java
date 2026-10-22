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
 * Обробляє глобальні такти симуляції, накопичує вхідні сигнали та керує залишковою тривалістю активності нейронів.
 * Повторна активація перезапускає лічильник, якщо нейрон ще не передає сигнал у поточному такті.
 */
public final class SimulationSession {

    private final NeuronMapModel model;
    private final Map<String, Integer> pendingSignals = new LinkedHashMap<>();
    private final Set<String> pendingManualStarts = new LinkedHashSet<>();
    private final Map<String, Integer> remainingActiveTicks = new LinkedHashMap<>();
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
     * Активує нейрон у межах поточного такту й одразу додає його вихідні сигнали до черги доставки.
     * На відміну від {@link #queueManualStart(String)}, цей метод не очікує наступної межі такту
     * та не просуває глобальний лічильник. Раніше заплановані сигнали й активності зберігаються.
     * Повторне звернення ігнорується, якщо нейрон уже випустив сигнал у поточному такті.
     *
     * @param sourceNeuronId ідентифікатор нейрона для негайної активації.
     * @return актуальний знімок поточного такту або {@code null}, якщо нейрон відсутній чи вже передає сигнал.
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
        Set<String> activatedNeuronIds = Set.of(sourceNeuron.id());
        restartActivationCounters(activatedNeuronIds);
        collectOutgoingSignals(sourceNeuron, pendingSignals);
        consumeActivationTick(sourceNeuron.id());
        emittingNeuronIdsThisTick.add(sourceNeuron.id());
        updateFinishedStateAfterImmediateActivation();
        lastStep = createImmediateActivationStep(sourceNeuron.id());
        return lastStep;
    }

    /** Позначає сеанс активним або завершує лічильники, якщо після негайного випуску роботи не залишилося. */
    private void updateFinishedStateAfterImmediateActivation() {
        finished = pendingSignals.isEmpty() && pendingManualStarts.isEmpty();
        if (finished) {
            remainingActiveTicks.clear();
        }
    }

    /** Формує актуальний знімок такту, зберігаючи раніше відображені активації та випромінювачі. */
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
        currentEmitters.add(sourceNeuronId);

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
     * Перевіряє, чи завершено сеанс через відсутність сигналів для подальшої обробки.
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
     * Перевіряє, чи є вхідні сигнали або ручні запуски для наступного такту.
     * Активний лічильник сам по собі не продовжує сеанс, якщо нейрон більше не може надіслати сигнал.
     *
     * @return {@code true}, якщо є очікувана робота; інакше {@code false}.
     */
    public boolean hasPendingWork() {
        return !pendingSignals.isEmpty() || !pendingManualStarts.isEmpty();
    }

    /**
     * Обробляє один такт, оновлює залишки активності та визначає активації, які вже прогнозуються наступними сигналами.
     *
     * @return результат такту з незмінним знімком залишків активних тактів або {@code null}, якщо роботи немає.
     */
    public SimulationStep nextStep() {
        if (finished || !hasPendingWork()) {
            finished = true;
            remainingActiveTicks.clear();
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
        restartActivationCounters(activatedNeuronIds);
        Set<String> emittingNeuronIds = emitActiveSignals(activatedNeuronIds, nextSignals);
        emittingNeuronIdsThisTick.addAll(emittingNeuronIds);
        Set<String> nextActivatedNeuronIds = new LinkedHashSet<>();
        collectThresholdActivations(nextSignals, nextActivatedNeuronIds);

        if (nextSignals.isEmpty()) {
            finished = true;
            remainingActiveTicks.clear();
        } else {
            pendingSignals.putAll(nextSignals);
            finished = false;
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

    /**
     * Додає наявні нейрони з ручної черги до набору активацій поточного такту.
     *
     * @param manualStarts ідентифікатори нейронів, поставлених у чергу ручного запуску.
     * @param activatedNeuronIds набір нейронів, активованих у поточному такті.
     */
    private void collectManualActivations(Set<String> manualStarts, Set<String> activatedNeuronIds) {
        for (String neuronId : manualStarts) {
            Neuron neuron = model.neuron(neuronId);
            if (neuron != null) {
                activatedNeuronIds.add(neuron.id());
            }
        }
    }

    /**
     * Додає нейрони, чия сума вхідних сигналів досягла порога активації.
     *
     * @param inputSums суми сигналів, отриманих у поточному такті.
     * @param activatedNeuronIds набір нейронів, активованих у поточному такті.
     */
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

    /**
     * Перезапускає відлік для кожного нейрона, який перетнув поріг або був активований вручну.
     *
     * @param activatedNeuronIds нейрони, для яких потрібно встановити повну тривалість активності.
     */
    private void restartActivationCounters(Set<String> activatedNeuronIds) {
        for (String neuronId : activatedNeuronIds) {
            Neuron neuron = model.neuron(neuronId);
            if (neuron != null) {
                remainingActiveTicks.put(neuronId, neuron.activeTicks());
            }
        }
    }

    /**
     * Витрачає перший активний такт для нейрона, який випустив імпульс посеред поточного такту.
     *
     * @param neuronId ідентифікатор нейрона, що випустив сигнал.
     */
    private void consumeActivationTick(String neuronId) {
        int remaining = remainingActiveTicks.getOrDefault(neuronId, 0) - 1;
        if (remaining <= 0) {
            remainingActiveTicks.remove(neuronId);
        } else {
            remainingActiveTicks.put(neuronId, remaining);
        }
    }

    /**
     * Випускає по одному сигналу від кожного активного нейрона та віднімає витрачений такт від лічильника.
     * Повертає окремий набір випромінювачів, щоб подання могло анімувати повторні сигнали без повторної активації.
     *
     * @param activatedNeuronIds нейрони, активовані у поточному такті.
     * @param nextSignals накопичувач сигналів, запланованих на наступний такт.
     * @return ідентифікатори нейронів, які випустили сигнал у поточному такті.
     */
    private Set<String> emitActiveSignals(Set<String> activatedNeuronIds, Map<String, Integer> nextSignals) {
        Set<String> candidates = new LinkedHashSet<>(activatedNeuronIds);
        remainingActiveTicks.forEach((neuronId, ticks) -> {
            if (ticks > 0) {
                candidates.add(neuronId);
            }
        });
        Set<String> emittingNeuronIds = new LinkedHashSet<>();
        for (String neuronId : candidates) {
            Neuron neuron = model.neuron(neuronId);
            if (neuron == null) {
                remainingActiveTicks.remove(neuronId);
                continue;
            }
            collectOutgoingSignals(neuron, nextSignals);
            emittingNeuronIds.add(neuronId);
            consumeActivationTick(neuronId);
        }
        return emittingNeuronIds;
    }

    /**
     * Додає вагу сигналу до кожного наявного цільового нейрона, з яким з'єднаний випромінювач.
     *
     * @param neuron нейрон, який випускає сигнал.
     * @param nextSignals накопичувач сум сигналів для наступного такту.
     */
    private void collectOutgoingSignals(Neuron neuron, Map<String, Integer> nextSignals) {
        for (Connection connection : model.connections()) {
            if (!connection.sourceId().equals(neuron.id())) {
                continue;
            }
            if (model.neuron(connection.targetId()) == null) {
                continue;
            }
            nextSignals.merge(
                    connection.targetId(),
                    neuron.signalWeight(),
                    Integer::sum
            );
        }
    }
}
