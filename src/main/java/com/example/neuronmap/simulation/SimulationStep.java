package com.example.neuronmap.simulation;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Повертає результат операції «крок».
 *
 * @param tick номер такту симуляції.
 *
 * @param inputSums значення, що визначає вхід для цієї операції.
 *
 * @param activatedNeuronIds значення, що визначає нейрон ідентифікатори для цієї операції.
 *
 * @param nextInputSums значення, що визначає наступний вхід для цієї операції.
 *
 * @return значення або обʼєкт, визначений описаною операцією.
 */
/**
 * Містить результат одного такту симуляції, зокрема активовані нейрони й сигнали.
 */
public record SimulationStep(
        BigInteger tick,
        Map<String, Integer> inputSums,
        Set<String> activatedNeuronIds,
        Map<String, Integer> nextInputSums
) {

    /**
     * Повертає результат операції «крок».
     *
     * @param tick номер такту симуляції.
     *
     * @param inputSums значення, що визначає вхід для цієї операції.
     *
     * @param activatedNeuronIds значення, що визначає нейрон ідентифікатори для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public SimulationStep(
            BigInteger tick,
            Map<String, Integer> inputSums,
            Set<String> activatedNeuronIds
    ) {
        this(
                tick,
                inputSums,
                activatedNeuronIds,
                Map.of()
        );
    }

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
