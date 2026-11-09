package com.example.neuronmap.model;

/**
 * Об'єднує числові налаштування сигналу нейрона, режими його поширення та спосіб перевірки порога активації.
 * Поріг має різні межі залежно від режиму: додатне ціле число для сили сигналів і відсоток 0–100 для активуючих входів.
 *
 * @param signalStrength базова сила сигналу; для адаптивних режимів вона також є початковим значенням персональної сили.
 * @param activationThreshold поріг сили або відсоток активуючих вхідних сигналів.
 * @param activeTicks кількість тактів, протягом яких нейрон випускає сигнали після початку передачі.
 * @param delayTicks кількість тактів затримки перед першим вихідним сигналом.
 * @param activationMode спосіб перевірки умови активації.
 * @param signalType правило вибору сили вихідного сигналу.
 */
public record NeuronParameters(
        int signalStrength,
        int activationThreshold,
        int activeTicks,
        int delayTicks,
        NeuronActivationMode activationMode,
        SignalType signalType
) {

    /** Типовий набір параметрів нового нейрона. */
    public static final NeuronParameters DEFAULT = new NeuronParameters(
            1, 1, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH, SignalType.STATIC);

    /** Перевіряє допустимість параметрів під час створення їхнього незмінного значення. */
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
        if (signalType == null) {
            throw new IllegalArgumentException("Signal type must not be null.");
        }
        if (activationMode == NeuronActivationMode.SIGNAL_STRENGTH && activationThreshold <= 0) {
            throw new IllegalArgumentException("Activation threshold must be greater than zero in signal-strength mode.");
        }
        if (activationMode == NeuronActivationMode.SIGNAL_COUNT
                && (activationThreshold < 0 || activationThreshold > 100)) {
            throw new IllegalArgumentException("Activation threshold percentage must be between 0 and 100.");
        }
    }

    /** Зберігає сумісність п'ятипольового контракту, використовуючи статичну силу сигналу. */
    public NeuronParameters(int signalStrength, int activationThreshold, int activeTicks,
                            int delayTicks, NeuronActivationMode activationMode) {
        this(signalStrength, activationThreshold, activeTicks, delayTicks, activationMode, SignalType.STATIC);
    }

    /** Зберігає сумісність чотирипольового контракту, використовуючи режим сили сигналів. */
    public NeuronParameters(int signalStrength, int activationThreshold, int activeTicks, int delayTicks) {
        this(signalStrength, activationThreshold, activeTicks, delayTicks,
                NeuronActivationMode.SIGNAL_STRENGTH, SignalType.STATIC);
    }

    /** Створює параметри старого трьохпольового контракту із затримкою в один такт. */
    public NeuronParameters(int signalStrength, int activationThreshold, int activeTicks) {
        this(signalStrength, activationThreshold, activeTicks, 1);
    }

    /** Створює параметри старого двопольового контракту з одним активним тактом і затримкою в один такт. */
    public NeuronParameters(int signalStrength, int activationThreshold) {
        this(signalStrength, activationThreshold, 1, 1);
    }

    /** Повертає копію параметрів із заміненою базовою силою сигналу. */
    public NeuronParameters withSignalStrength(int value) {
        return new NeuronParameters(value, activationThreshold, activeTicks, delayTicks, activationMode, signalType);
    }

    /** Повертає копію параметрів з указаним типом сигналу. */
    public NeuronParameters withSignalType(SignalType value) {
        return new NeuronParameters(signalStrength, activationThreshold, activeTicks, delayTicks, activationMode, value);
    }
}
