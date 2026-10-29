package com.example.neuronmap.simulation;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Зберігає незмінні результати такту, стан сигналів і похідні індикатори структур. */
public record SimulationStep(
        BigInteger tick,
        Map<String, Integer> inputSums,
        Set<String> activatedNeuronIds,
        Set<String> emittingNeuronIds,
        Map<String, Integer> nextInputSums,
        Map<String, Integer> remainingActiveTicks,
        Set<String> nextActivatedNeuronIds,
        Map<String, Integer> structureInputActivationCounts,
        Map<String, Integer> structureEmittingNeuronCounts,
        Set<String> structureConnectionIdsToAnimate
) {

    /**
     * Зберігає сумісність із попереднім контрактом результату такту без індикаторів структур.
     *
     * @param tick номер обробленого такту.
     * @param inputSums суми вхідних сигналів за поточний такт.
     * @param activatedNeuronIds ідентифікатори нейронів, які активувалися в поточному такті.
     * @param emittingNeuronIds ідентифікатори нейронів, які випустили сигнал у цьому такті.
     * @param nextInputSums суми сигналів, запланованих на наступний такт.
     * @param remainingActiveTicks залишки активних тактів після обробки поточного такту.
     * @param nextActivatedNeuronIds ідентифікатори нейронів, які активуються наступного такту.
     */
    public SimulationStep(
            BigInteger tick,
            Map<String, Integer> inputSums,
            Set<String> activatedNeuronIds,
            Set<String> emittingNeuronIds,
            Map<String, Integer> nextInputSums,
            Map<String, Integer> remainingActiveTicks,
            Set<String> nextActivatedNeuronIds
    ) {
        this(tick, inputSums, activatedNeuronIds, emittingNeuronIds, nextInputSums,
                remainingActiveTicks, nextActivatedNeuronIds, Map.of(), Map.of(), Set.of());
    }

    /**
     * Створює результат такту, у якому активовані нейрони одночасно вважаються тими, що випускають сигнал.
     * Цей конструктор зберігає сумісність із попереднім контрактом результату такту.
     *
     * @param tick номер обробленого такту.
     * @param inputSums суми вхідних сигналів за поточний такт.
     * @param activatedNeuronIds ідентифікатори нейронів, які активувалися в поточному такті.
     * @param nextInputSums суми сигналів, запланованих на наступний такт.
     */
    public SimulationStep(
            BigInteger tick,
            Map<String, Integer> inputSums,
            Set<String> activatedNeuronIds,
            Map<String, Integer> nextInputSums
    ) {
        this(tick, inputSums, activatedNeuronIds, activatedNeuronIds, nextInputSums, Map.of(), Set.of());
    }

    /**
     * Зберігає сумісність із викликами, які явно передають окремі набори активації та випуску сигналів.
     *
     * @param tick номер обробленого такту.
     * @param inputSums суми вхідних сигналів за поточний такт.
     * @param activatedNeuronIds ідентифікатори нейронів, що перетнули поріг або запущені вручну.
     * @param emittingNeuronIds ідентифікатори нейронів, які випустили сигнал у цьому такті.
     * @param nextInputSums суми сигналів, запланованих для нейронів на наступний такт.
     */
    public SimulationStep(
            BigInteger tick,
            Map<String, Integer> inputSums,
            Set<String> activatedNeuronIds,
            Set<String> emittingNeuronIds,
            Map<String, Integer> nextInputSums
    ) {
        this(tick, inputSums, activatedNeuronIds, emittingNeuronIds, nextInputSums, Map.of(), Set.of());
    }

    /**
     * Зберігає сумісність із викликами, які передають знімок залишків активних тактів без прогнозу активацій.
     *
     * @param tick номер обробленого такту.
     * @param inputSums суми вхідних сигналів, отриманих нейронами в цьому такті.
     * @param activatedNeuronIds ідентифікатори нейронів, що перетнули поріг або запущені вручну.
     * @param emittingNeuronIds ідентифікатори нейронів, які випустили сигнал у цьому такті.
     * @param nextInputSums суми сигналів, запланованих для нейронів на наступний такт.
     * @param remainingActiveTicks залишки активних тактів після обробки поточного такту.
     */
    public SimulationStep(
            BigInteger tick,
            Map<String, Integer> inputSums,
            Set<String> activatedNeuronIds,
            Set<String> emittingNeuronIds,
            Map<String, Integer> nextInputSums,
            Map<String, Integer> remainingActiveTicks
    ) {
        this(tick, inputSums, activatedNeuronIds, emittingNeuronIds,
                nextInputSums, remainingActiveTicks, Set.of());
    }

    /**
     * Створює незмінний знімок усіх сигналів, лічильників і структурних індикаторів цього такту.
     *
     * @param tick номер обробленого такту.
     * @param inputSums суми вхідних сигналів за поточний такт.
     * @param activatedNeuronIds нейрони, які активувалися в поточному такті.
     * @param emittingNeuronIds нейрони, які фактично випустили сигнал у поточному такті.
     * @param nextInputSums суми сигналів, запланованих на наступний такт.
     * @param remainingActiveTicks залишки активних тактів після поточного такту.
     * @param nextActivatedNeuronIds нейрони, чий вхідний індикатор засвітиться в наступному стані.
     * @param structureInputActivationCounts кількість внутрішніх нейронів із сигналом, що активує їхній вхід.
     * @param structureEmittingNeuronCounts кількість внутрішніх нейронів, що випускають сигнал у поточному такті.
     * @param structureConnectionIdsToAnimate ідентифікатори структурних зв’язків, підтверджених реальними внутрішніми сигналами.
     */
    public SimulationStep {
        inputSums = Collections.unmodifiableMap(new LinkedHashMap<>(inputSums));
        activatedNeuronIds = Collections.unmodifiableSet(new LinkedHashSet<>(activatedNeuronIds));
        emittingNeuronIds = Collections.unmodifiableSet(new LinkedHashSet<>(emittingNeuronIds));
        nextInputSums = Collections.unmodifiableMap(new LinkedHashMap<>(nextInputSums));
        remainingActiveTicks = Collections.unmodifiableMap(new LinkedHashMap<>(remainingActiveTicks));
        nextActivatedNeuronIds = Collections.unmodifiableSet(new LinkedHashSet<>(nextActivatedNeuronIds));
        structureInputActivationCounts = Collections.unmodifiableMap(new LinkedHashMap<>(structureInputActivationCounts));
        structureEmittingNeuronCounts = Collections.unmodifiableMap(new LinkedHashMap<>(structureEmittingNeuronCounts));
        structureConnectionIdsToAnimate = Collections.unmodifiableSet(new LinkedHashSet<>(structureConnectionIdsToAnimate));
    }
}
