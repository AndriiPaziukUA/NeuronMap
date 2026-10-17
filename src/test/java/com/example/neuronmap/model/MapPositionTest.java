package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Перевіряє позицію карти як незмінне значення з координатами, незалежне від JavaFX.
 */
class MapPositionTest {

    /**
     * Перевіряє збереження координат і семантику порівняння record за значенням.
     */
    @Test
    void storesAndComparesCoordinatesByValue() {
        MapPosition position = new MapPosition(120.5, -44.0);

        assertEquals(120.5, position.x());
        assertEquals(-44.0, position.y());
        assertEquals(new MapPosition(120.5, -44.0), position);
    }
}
