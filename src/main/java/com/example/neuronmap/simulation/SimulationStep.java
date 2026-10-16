package com.example.neuronmap.simulation;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Зберігає результати одного такту симуляції.
 * @param tick номер такту, який було оброблено.
 * @param inputSums суми вхідних сигналів, отриманих кожним нейроном у цьому такті.
 * @param activatedNeuronIds ідентифікатори нейронів, які активувалися в цьому такті.
 * @param nextInputSums суми сигналів, запланованих для нейронів на наступний такт.
 */
public record SimulationStep(
        BigInteger tick,
        Map<String, Integer> inputSums,
        Set<String> activatedNeuronIds,
        Map<String, Integer> nextInputSums
) {

    /**
     * Створює результат такту з незмінними знімками отриманих і запланованих сигналів та активованих нейронів.
     */
    public SimulationStep {
        inputSums = Collections.unmodifiableMap(
                new LinkedHashMap<>(inputSums)
        );
        activatedNeuronIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(activatedNeuronIds)
        );
        nextInputSums = Collections.unmodifiableMap(
                new LinkedHashMap<>(nextInputSums)
        );
    }
}
