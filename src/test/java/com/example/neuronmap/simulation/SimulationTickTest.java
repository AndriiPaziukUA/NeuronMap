package com.example.neuronmap.simulation;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class SimulationTickTest {

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

    @Test
    void rejectsNegativeTick() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulationTick(BigInteger.valueOf(-1))
        );
    }
}
