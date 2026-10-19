package com.example.neuronmap.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє розбір тривалості такту симуляції та відхилення значень поза налаштованими межами. */
class SimulationSpeedTest {

    /** Перевіряє прийняття коректного значення швидкості в межах конфігурації. */
    @Test
    void parsesValidSpeedInsideConfiguredRange() {
        assertEquals(750.0, SimulationSpeed.parseMillis("750", 50.0, 5000.0));
    }

    /** Перевіряє, що обидві включені межі швидкості залишаються допустимими. */
    @Test
    void acceptsConfiguredMinimumAndMaximum() {
        assertEquals(50.0, SimulationSpeed.parseMillis("50", 50.0, 5000.0));
        assertEquals(5000.0, SimulationSpeed.parseMillis("5000", 50.0, 5000.0));
    }

    /** Перевіряє відхилення значень нижче мінімуму та вище максимуму. */
    @Test
    void rejectsSpeedOutsideConfiguredRange() {
        assertThrows(IllegalArgumentException.class, () -> SimulationSpeed.parseMillis("20", 50.0, 5000.0));
        assertThrows(IllegalArgumentException.class, () -> SimulationSpeed.parseMillis("5001", 50.0, 5000.0));
    }
}
