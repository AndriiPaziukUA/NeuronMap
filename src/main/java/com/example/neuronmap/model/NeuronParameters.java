package com.example.neuronmap.model;

/**
 * Об'єднує налаштування нейрона, які визначають силу вихідного сигналу та поріг його активації.
 * Обидва значення мають бути додатними, тому їхня перевірка виконується в одному місці.
 *
 * @param signalStrength сила сигналу, який генерує нейрон.
 * @param activationThreshold мінімальна сума вхідних сигналів, необхідна для активації нейрона.
 */
public record NeuronParameters(
        int signalStrength,
        int activationThreshold
) {

    /**
     * Перевіряє допустимість обох параметрів під час створення їхнього незмінного значення.
     *
     * @throws IllegalArgumentException якщо сила сигналу або поріг активації не є додатними.
     */
    public NeuronParameters {
        if (signalStrength <= 0) {
            throw new IllegalArgumentException("Signal strength must be greater than zero.");
        }
        if (activationThreshold <= 0) {
            throw new IllegalArgumentException("Activation threshold must be greater than zero.");
        }
    }
}
