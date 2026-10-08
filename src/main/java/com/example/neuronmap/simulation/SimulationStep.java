package com.example.neuronmap.simulation;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record SimulationStep(
        BigInteger tick,
        Map<String, Integer> inputSums,
        Set<String> activatedNeuronIds,
        Map<String, Integer> nextInputSums
) {

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
