package com.example.neuronmap.simulation;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Перевіряє збереження номера такту, що перевищує максимальне значення long.
 */
class SimulationStepTest {

    /**
     * Перевіряє збереження такту, номер якого перевищує максимальне значення long.
     */
    @Test
    void storesTickBeyondLongMaximum() {
        BigInteger beyondLongMaximum = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

        SimulationStep step = new SimulationStep(
                beyondLongMaximum,
                java.util.Map.of(),
                java.util.Set.of(),
                java.util.Map.of()
        );

        assertEquals(beyondLongMaximum, step.tick());
    }
}
