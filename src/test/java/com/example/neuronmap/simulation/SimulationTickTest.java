package com.example.neuronmap.simulation;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевіряє збільшення номера такту та перевірку відʼємних значень.
 */
class SimulationTickTest {

    /**
     * Перевіряє очікувану поведінку: long без.
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
     * Перевіряє очікувану поведінку: відхиляє відʼємний такт.
     */
    @Test
    void rejectsNegativeTick() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulationTick(BigInteger.valueOf(-1))
        );
    }
}
