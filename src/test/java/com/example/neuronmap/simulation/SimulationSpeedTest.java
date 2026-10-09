package com.example.neuronmap.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє розбір тривалості такту симуляції та відхилення значень поза налаштованими межами.
 */
class SimulationSpeedTest {

    /**
     * Перевіряє прийняття коректного значення швидкості в межах конфігурації.
     */
    @Test
    void parsesValidSpeedInsideConfiguredRange() {
        assertEquals(
                750.0,
                SimulationSpeed.parseMillis("750", 50.0, 5000.0)
        );
    }

    /**
     * Перевіряє відхилення швидкості за межами допустимого діапазону.
     */
    @Test
    void rejectsSpeedOutsideConfiguredRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulationSpeed.parseMillis("20", 50.0, 5000.0)
        );
    }
}
