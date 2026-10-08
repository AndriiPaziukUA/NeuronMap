package com.example.neuronmap.simulation;

import java.math.BigInteger;
import java.util.Objects;

/** Immutable, unbounded simulation tick number. */
public record SimulationTick(BigInteger value) {

    public SimulationTick {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
    }

    public SimulationTick() {
        this(BigInteger.ZERO);
    }

    public SimulationTick next() {
        return new SimulationTick(value.add(BigInteger.ONE));
    }
}
