package com.example.neuronmap.simulation;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє незмінний результат такту, зокрема окремі набори активованих нейронів і випущених сигналів. */
class SimulationStepTest {

    /** Перевіряє збереження такту, номер якого перевищує максимальне значення long. */
    @Test
    void storesTickBeyondLongMaximum() {
        BigInteger beyondLongMaximum = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        SimulationStep step = new SimulationStep(
                beyondLongMaximum, Map.of(), Set.of(), Map.of());
        assertEquals(beyondLongMaximum, step.tick());
    }

    /** Перевіряє, що старий конструктор зберігає сумісну семантику випуску сигналу під час активації. */
    @Test
    void legacyConstructorTreatsActivatedNeuronsAsEmitting() {
        Set<String> activated = Set.of("source");
        SimulationStep step = new SimulationStep(BigInteger.ZERO, Map.of(), activated, Map.of());
        assertEquals(activated, step.activatedNeuronIds());
        assertEquals(activated, step.emittingNeuronIds());
    }
}
