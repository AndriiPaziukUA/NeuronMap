package com.example.neuronmap.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє допустимі значення швидкості симуляції.
 */
class SimulationSpeedTest {

    /**
     * Перевіряє очікувану поведінку: коректний швидкість.
     */
    @Test
    void parsesValidSpeedInsideConfiguredRange() {
        assertEquals(
                750.0,
                SimulationSpeed.parseMillis("750", 50.0, 5000.0)
        );
    }

    /**
     * Перевіряє очікувану поведінку: відхиляє швидкість.
     */
    @Test
    void rejectsSpeedOutsideConfiguredRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulationSpeed.parseMillis("20", 50.0, 5000.0)
        );
    }
}
