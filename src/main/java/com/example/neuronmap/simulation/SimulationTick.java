package com.example.neuronmap.simulation;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Повертає результат операції «такт».
 *
 * @param value значення, яке потрібно передати або зберегти.
 *
 * @return значення або обʼєкт, визначений описаною операцією.
 */
/**
 * Представляє номер такту симуляції без обмеження типу long.
 */
public record SimulationTick(BigInteger value) {

    public SimulationTick {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("tick must not be negative");
        }
    }

    /**
     * Повертає результат операції «такт».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public SimulationTick() {
        this(BigInteger.ZERO);
    }

    /**
     * Повертає результат операції «наступний».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public SimulationTick next() {
        return new SimulationTick(value.add(BigInteger.ONE));
    }
}
