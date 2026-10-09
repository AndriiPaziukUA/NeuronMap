package com.example.neuronmap.simulation;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевіряє збільшення лічильника тактів за межами long без переповнення та відхилення від’ємного номера.
 */
class SimulationTickTest {

    /**
     * Перевіряє збільшення номера такту понад межу long без переповнення.
     */
    @Test
    void advancesBeyondLongMaximumWithoutOverflow() {
        BigInteger nearLongLimit = BigInteger.valueOf(Long.MAX_VALUE);
        SimulationTick tick = new SimulationTick(nearLongLimit);

        SimulationTick next = tick.next();

        assertEquals(
                nearLongLimit.add(BigInteger.ONE),
                next.value()
        );
    }

    /**
     * Перевіряє відхилення від’ємного номера такту.
     */
    @Test
    void rejectsNegativeTick() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulationTick(BigInteger.valueOf(-1))
        );
    }
}
