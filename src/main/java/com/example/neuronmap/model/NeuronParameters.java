package com.example.neuronmap.model;

/**
 * Об'єднує числові налаштування сигналу нейрона та спосіб перевірки порога активації.
 * Поріг має різні межі залежно від режиму: додатне ціле число для сили сигналів і відсоток 0–100 для активуючих входів.
 *
 * @param signalStrength сила сигналу, який генерує нейрон.
 * @param activationThreshold поріг сили або відсоток активуючих вхідних сигналів.
 * @param activeTicks кількість тактів, протягом яких нейрон випускає сигнали після початку передачі.
 * @param delayTicks кількість тактів затримки перед першим вихідним сигналом.
 * @param activationMode спосіб перевірки умови активації.
 */
public record NeuronParameters(
        int signalStrength,
        int activationThreshold,
        int activeTicks,
        int delayTicks,
        NeuronActivationMode activationMode
) {

    /**
     * Перевіряє допустимість параметрів під час створення їхнього незмінного значення.
     *
     * @throws IllegalArgumentException якщо числові параметри або режим активації некоректні.
     */
    public NeuronParameters {
        if (signalStrength <= 0) {
            throw new IllegalArgumentException("Signal strength must be greater than zero.");
        }
        if (activeTicks <= 0) {
            throw new IllegalArgumentException("Active ticks must be greater than zero.");
        }
        if (delayTicks <= 0) {
            throw new IllegalArgumentException("Delay ticks must be greater than zero.");
        }
        if (activationMode == null) {
            throw new IllegalArgumentException("Activation mode must not be null.");
        }
        if (activationMode == NeuronActivationMode.SIGNAL_STRENGTH && activationThreshold <= 0) {
            throw new IllegalArgumentException("Activation threshold must be greater than zero in signal-strength mode.");
        }
        if (activationMode == NeuronActivationMode.SIGNAL_COUNT
                && (activationThreshold < 0 || activationThreshold > 100)) {
            throw new IllegalArgumentException("Activation threshold percentage must be between 0 and 100.");
        }
    }

    /**
     * Зберігає сумісність чотирипольового контракту, використовуючи режим сили сигналів.
     *
     * @param signalStrength сила сигналу.
     * @param activationThreshold додатний поріг активації.
     * @param activeTicks кількість активних тактів.
     * @param delayTicks кількість тактів затримки.
     */
    public NeuronParameters(int signalStrength, int activationThreshold, int activeTicks, int delayTicks) {
        this(signalStrength, activationThreshold, activeTicks, delayTicks, NeuronActivationMode.SIGNAL_STRENGTH);
    }

    /**
     * Створює параметри старого трьохпольового контракту, використовуючи затримку в один такт.
     *
     * @param signalStrength сила сигналу.
     * @param activationThreshold додатний поріг активації.
     * @param activeTicks кількість активних тактів.
     */
    public NeuronParameters(int signalStrength, int activationThreshold, int activeTicks) {
        this(signalStrength, activationThreshold, activeTicks, 1);
    }

    /**
     * Створює параметри зі старого контракту, використовуючи один активний такт і затримку в один такт.
     *
     * @param signalStrength сила сигналу.
     * @param activationThreshold додатний поріг активації.
     */
    public NeuronParameters(int signalStrength, int activationThreshold) {
        this(signalStrength, activationThreshold, 1, 1);
    }
}
