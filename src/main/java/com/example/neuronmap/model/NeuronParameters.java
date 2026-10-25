package com.example.neuronmap.model;

/**
 * Об'єднує налаштування вихідного сигналу нейрона та умови його активації.
 * Усі числові параметри мають бути додатними, тому їх перевірка зосереджена в одному місці.
 *
 * @param signalStrength сила сигналу, який генерує нейрон.
 * @param activationThreshold мінімальна сума вхідних сигналів, необхідна для активації нейрона.
 * @param activeTicks кількість тактів, протягом яких нейрон випускає сигнали після початку передачі.
 * @param delayTicks кількість тактів затримки перед першим вихідним сигналом.
 */
public record NeuronParameters(
        int signalStrength,
        int activationThreshold,
        int activeTicks,
        int delayTicks
) {

    /**
     * Перевіряє допустимість параметрів під час створення їхнього незмінного значення.
     *
     * @throws IllegalArgumentException якщо сила, поріг, активні такти або затримка не є додатними.
     */
    public NeuronParameters {
        if (signalStrength <= 0) {
            throw new IllegalArgumentException("Signal strength must be greater than zero.");
        }
        if (activationThreshold <= 0) {
            throw new IllegalArgumentException("Activation threshold must be greater than zero.");
        }
        if (activeTicks <= 0) {
            throw new IllegalArgumentException("Active ticks must be greater than zero.");
        }
        if (delayTicks <= 0) {
            throw new IllegalArgumentException("Delay ticks must be greater than zero.");
        }
    }

    /**
     * Створює параметри старого трьохпольового контракту, використовуючи затримку в один такт.
     *
     * @param signalStrength сила сигналу.
     * @param activationThreshold поріг активації.
     * @param activeTicks кількість активних тактів.
     */
    public NeuronParameters(int signalStrength, int activationThreshold, int activeTicks) {
        this(signalStrength, activationThreshold, activeTicks, 1);
    }

    /**
     * Створює параметри зі старого контракту, використовуючи один активний такт і затримку в один такт.
     *
     * @param signalStrength сила сигналу.
     * @param activationThreshold поріг активації.
     */
    public NeuronParameters(int signalStrength, int activationThreshold) {
        this(signalStrength, activationThreshold, 1, 1);
    }
}
