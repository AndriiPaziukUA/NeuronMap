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
 * Веде спільний відлік тактів і накопичує сигнали, які потрібно обробити на наступних тактах.
 */
public final class SimulationSession {

    private final NeuronMapModel model;
    private final Map<String, Integer> pendingSignals =
            new LinkedHashMap<>();
    private final Set<String> pendingManualStarts =
            new LinkedHashSet<>();

    private BigInteger tick = BigInteger.ZERO;
    private boolean finished;

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param model модель карти нейронів.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private SimulationSession(NeuronMapModel model) {
        this.model = model;
    }

    /**
     * Створює сеанс симуляції та ставить ручний запуск указаного нейрона в чергу.
     *
     * @param model модель карти нейронів.
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public static SimulationSession manual(
            NeuronMapModel model,
            String sourceNeuronId
    ) {
        if (model == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("model must not be null");
        }

        SimulationSession session = new SimulationSession(model);
        session.queueManualStart(sourceNeuronId);
        return session;
    }

/**
 * Додає нейрон до списку ручних запусків наступного такту; повторне додавання не дублює запуск.
 *
 * @param sourceNeuronId ідентифікатор початкового нейрона.
 *
 * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
 */
public boolean queueManualStart(String sourceNeuronId) {
        if (sourceNeuronId == null || sourceNeuronId.isBlank()) {
            return false;
        }

        if (model.neuron(sourceNeuronId) == null) {
            return false;
        }

        pendingManualStarts.add(sourceNeuronId);
        finished = false;
        return true;
    }

    /**
     * Повертає ознаку того, що сеанс симуляції завершився.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Повертає номер поточного такту симуляції.
     *
     * @return номер такту симуляції.
     */
    public BigInteger tick() {
        return tick;
    }

/**
 * Перевіряє, чи залишилися сигнали або ручні запуски для наступного такту.
 *
 * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
 */
public boolean hasPendingWork() {
        return !pendingSignals.isEmpty()
                || !pendingManualStarts.isEmpty();
    }

    /**
     * Обчислює наступний такт симуляції; повертає null, коли роботи більше немає.
     *
     * @return наступний такт симуляції або null, якщо подальшої роботи немає.
     */
    public SimulationStep nextStep() {
        if (finished || !hasPendingWork()) {
            finished = true;
            return null;
        }

        Map<String, Integer> inputSums =
                new LinkedHashMap<>(pendingSignals);
        pendingSignals.clear();

        Set<String> manualStarts =
                new LinkedHashSet<>(pendingManualStarts);
        pendingManualStarts.clear();

        Set<String> activatedNeuronIds =
                new LinkedHashSet<>();
        Map<String, Integer> nextSignals =
                new LinkedHashMap<>();

        for (String neuronId : manualStarts) {
            Neuron neuron = model.neuron(neuronId);
            if (neuron != null) {
                activatedNeuronIds.add(neuron.id());
            }
        }

        for (Map.Entry<String, Integer> entry : inputSums.entrySet()) {
            Neuron neuron = model.neuron(entry.getKey());

            // A neuron deleted after a pulse was scheduled may remain in the
            // pending map for this tick, but it must not participate any more.
            if (neuron == null) {
                continue;
            }

            if (entry.getValue() >= neuron.activationThreshold()) {
                activatedNeuronIds.add(neuron.id());
            }
        }

        // Each activated neuron emits once per global tick, even when it was
        // both manually started and activated by an incoming signal.
        for (String neuronId : activatedNeuronIds) {
            Neuron neuron = model.neuron(neuronId);
            if (neuron != null) {
                collectOutgoingSignals(neuron, nextSignals);
            }
        }

        SimulationStep step = new SimulationStep(
                tick,
                inputSums,
                activatedNeuronIds,
                nextSignals
        );

        tick = tick.add(BigInteger.ONE);

        if (nextSignals.isEmpty()) {
            finished = true;
        } else {
            pendingSignals.putAll(nextSignals);
            finished = false;
        }

        return step;
    }

    /**
     * Виконує операцію «сигнали».
     *
     * @param neuron нейрон, над яким виконується операція.
     *
     * @param nextSignals значення, що визначає наступний сигнали для цієї операції.
     */
    private void collectOutgoingSignals(
            Neuron neuron,
            Map<String, Integer> nextSignals
    ) {
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
