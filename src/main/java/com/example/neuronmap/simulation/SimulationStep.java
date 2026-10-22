package com.example.neuronmap.simulation;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Зберігає незмінні результати такту, залишки активності та майбутні активації за запланованими вхідними сигналами. */
public record SimulationStep(
        BigInteger tick,
        Map<String, Integer> inputSums,
        Set<String> activatedNeuronIds,
        Set<String> emittingNeuronIds,
        Map<String, Integer> nextInputSums,
        Map<String, Integer> remainingActiveTicks,
        Set<String> nextActivatedNeuronIds
) {

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
     * Створює результат такту з незмінними копіями всіх наборів, сум сигналів і залишків активних тактів.
     *
     * @param tick номер обробленого такту.
     * @param inputSums суми вхідних сигналів, отриманих нейронами в цьому такті.
     * @param activatedNeuronIds ідентифікатори нейронів, що перетнули поріг або запущені вручну.
     * @param emittingNeuronIds ідентифікатори нейронів, які випустили сигнал у цьому такті.
     * @param nextInputSums суми сигналів, запланованих для нейронів на наступний такт.
     * @param remainingActiveTicks кількість активних тактів, що залишилася в кожного нейрона після цього такту.
     * @param nextActivatedNeuronIds ідентифікатори нейронів, які активуються від запланованих вхідних сум у наступному такті.
     */
    public SimulationStep {
        inputSums = Collections.unmodifiableMap(new LinkedHashMap<>(inputSums));
        activatedNeuronIds = Collections.unmodifiableSet(new LinkedHashSet<>(activatedNeuronIds));
        emittingNeuronIds = Collections.unmodifiableSet(new LinkedHashSet<>(emittingNeuronIds));
        nextInputSums = Collections.unmodifiableMap(new LinkedHashMap<>(nextInputSums));
        remainingActiveTicks = Collections.unmodifiableMap(new LinkedHashMap<>(remainingActiveTicks));
        nextActivatedNeuronIds = Collections.unmodifiableSet(new LinkedHashSet<>(nextActivatedNeuronIds));
    }
}
