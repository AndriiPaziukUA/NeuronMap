package com.example.neuronmap.simulation;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Зберігає незмінні результати одного такту симуляції та розрізняє активацію і випуск сигналу. */
public record SimulationStep(
        BigInteger tick,
        Map<String, Integer> inputSums,
        Set<String> activatedNeuronIds,
        Set<String> emittingNeuronIds,
        Map<String, Integer> nextInputSums
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
        this(tick, inputSums, activatedNeuronIds, activatedNeuronIds, nextInputSums);
    }

    /**
     * Створює результат такту з незмінними копіями всіх наборів і мап сигналів.
     *
     * @param tick номер обробленого такту.
     * @param inputSums суми вхідних сигналів, отриманих нейронами в цьому такті.
     * @param activatedNeuronIds ідентифікатори нейронів, що перетнули поріг або запущені вручну.
     * @param emittingNeuronIds ідентифікатори нейронів, які випустили сигнал у цьому такті.
     * @param nextInputSums суми сигналів, запланованих для нейронів на наступний такт.
     */
    public SimulationStep {
        inputSums = Collections.unmodifiableMap(new LinkedHashMap<>(inputSums));
        activatedNeuronIds = Collections.unmodifiableSet(new LinkedHashSet<>(activatedNeuronIds));
        emittingNeuronIds = Collections.unmodifiableSet(new LinkedHashSet<>(emittingNeuronIds));
        nextInputSums = Collections.unmodifiableMap(new LinkedHashMap<>(nextInputSums));
    }
}
