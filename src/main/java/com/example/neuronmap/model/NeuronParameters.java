package com.example.neuronmap.model;

/**
 * Об'єднує налаштування вихідного сигналу нейрона та умови його активації.
 * Усі значення мають бути додатними, тому перевірка виконується в одному місці.
 *
 * @param signalStrength сила сигналу, який генерує нейрон.
 * @param activationThreshold мінімальна сума вхідних сигналів, необхідна для активації нейрона.
 * @param activeTicks кількість тактів, протягом яких нейрон випускає по одному сигналу після активації.
 */
public record NeuronParameters(
        int signalStrength,
        int activationThreshold,
        int activeTicks
) {

    /**
     * Перевіряє допустимість усіх параметрів під час створення їхнього незмінного значення.
     *
     * @throws IllegalArgumentException якщо будь-який параметр не є додатним.
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
    }

    /**
     * Створює параметри зі старого контракту, використовуючи один активний такт за замовчуванням.
     *
     * @param signalStrength сила сигналу.
     * @param activationThreshold поріг активації.
     */
    public NeuronParameters(int signalStrength, int activationThreshold) {
        this(signalStrength, activationThreshold, 1);
    }
}
