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
 * Обробляє симуляцію по глобальних тактах: накопичує вхідні сигнали, активує нейрони та планує сигнали для наступного такту.
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
     * Створює екземпляр SimulationSession та зберігає передані залежності, потрібні для його роботи.
     *
     * @param model модель карти нейронів.
     */
    private SimulationSession(NeuronMapModel model) {
        this.model = model;
    }

    /**
     * Створює сеанс симуляції та додає початковий нейрон до черги ручного запуску.
     *
     * @param model модель карти нейронів.
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     */
    public static SimulationSession manual(
            NeuronMapModel model,
            String sourceNeuronId
    ) {
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
     * Повертає true, якщо сеанс не має подальшої роботи.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Повертає номер наступного глобального такту симуляції.
     *
     * @return номер наступного глобального такту симуляції.
     */
    public BigInteger tick() {
        return tick;
    }

/**
 * Перевіряє, чи залишилися вхідні сигнали або ручні запуски для наступного такту.
 *
 * @return {@code true}, якщо умову виконано; інакше {@code false}.
 */
public boolean hasPendingWork() {
        return !pendingSignals.isEmpty()
                || !pendingManualStarts.isEmpty();
    }

    /**
     * Обробляє один такт: визначає активовані нейрони й накопичує вихідні сигнали для наступного такту. Повертає null, якщо робота завершилася.
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

            if (neuron == null) {
                continue;
            }

            if (entry.getValue() >= neuron.activationThreshold()) {
                activatedNeuronIds.add(neuron.id());
            }
        }

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
     * Додає вагу сигналу до накопичувача для кожного наявного цільового нейрона, з яким з’єднаний активований нейрон.
     *
     * @param neuron нейрон моделі.
     * @param nextSignals накопичувач сигналів, запланованих для наступного такту.
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
