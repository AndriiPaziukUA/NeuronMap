package com.example.neuronmap.simulation;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Зберігає номер такту симуляції як BigInteger, щоб лічильник не переповнювався на межі long.
 * @param value невід’ємний номер поточного такту.
 */
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

    /**
     * Повертає новий лічильник із номером такту, збільшеним на одиницю.
     *
     * @return новий лічильник із номером такту, збільшеним на одиницю.
     */
    public SimulationTick next() {
        return new SimulationTick(value.add(BigInteger.ONE));
    }
}
