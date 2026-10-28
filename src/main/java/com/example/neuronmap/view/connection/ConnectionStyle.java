package com.example.neuronmap.view.connection;

import com.example.neuronmap.model.NeuronType;
import javafx.scene.paint.Color;

import java.util.Objects;

/**
 * Незмінний опис кольорів і геометричних параметрів зв'язків, незалежний від стилів інших компонентів.
 *
 * @param excitatoryColor основний колір зв'язку зі збуджувального нейрона.
 * @param inhibitoryColor основний колір зв'язку з гальмівного нейрона.
 * @param missingSourceColor резервний колір, якщо модель джерела недоступна.
 * @param excitatoryPulseColor колір імпульсу з активуючого нейрона.
 * @param inhibitoryPulseColor колір імпульсу з гальмівного нейрона.
 * @param deleteHighlightColor колір анімованого підсвічування під час видалення.
 * @param strokeWidth товщина видимої лінії зв'язку.
 * @param hitStrokeWidth ширина прозорої області для вибору зв'язку.
 * @param arrowSize довжина наконечника стрілки.
 * @param arrowHalfWidth половина ширини наконечника стрілки.
 */
public record ConnectionStyle(
        Color excitatoryColor,
        Color inhibitoryColor,
        Color missingSourceColor,
        Color excitatoryPulseColor,
        Color inhibitoryPulseColor,
        Color deleteHighlightColor,
        double strokeWidth,
        double hitStrokeWidth,
        double arrowSize,
        double arrowHalfWidth
) {
    private static final Color STRUCTURE_COLOR = Color.web("#f59e0b");
    private static final Color STRUCTURE_ARROW_COLOR = Color.web("#f59e0b");
    private static final Color STRUCTURE_PULSE_COLOR = Color.web("#38bdf8");

    /**
     * Стандартний стиль зв'язків; лінія й наконечник структури помаранчеві, а сигнал — блакитний.
     */
    public static final ConnectionStyle DEFAULT = new ConnectionStyle(
            Color.web("#55bf78"),
            Color.web("#8b929c"),
            Color.web("#8b929c"),
            Color.web("#facc15"),
            Color.web("#ef4444"),
            Color.web("#ffe25a"),
            2.3,
            18.0,
            11.0,
            5.5
    );

    /**
     * Перевіряє кольори та геометричні параметри стилю на коректність.
     *
     * @throws NullPointerException якщо один із кольорів не задано.
     * @throws IllegalArgumentException якщо товщина або розмір стрілки не є додатними скінченними числами.
     */
    public ConnectionStyle {
        Objects.requireNonNull(excitatoryColor, "excitatoryColor");
        Objects.requireNonNull(inhibitoryColor, "inhibitoryColor");
        Objects.requireNonNull(missingSourceColor, "missingSourceColor");
        Objects.requireNonNull(excitatoryPulseColor, "excitatoryPulseColor");
        Objects.requireNonNull(inhibitoryPulseColor, "inhibitoryPulseColor");
        Objects.requireNonNull(deleteHighlightColor, "deleteHighlightColor");
        requirePositiveFinite(strokeWidth, "strokeWidth");
        requirePositiveFinite(hitStrokeWidth, "hitStrokeWidth");
        requirePositiveFinite(arrowSize, "arrowSize");
        requirePositiveFinite(arrowHalfWidth, "arrowHalfWidth");
    }

    /**
     * Повертає базовий колір зв'язку відповідно до типу джерела.
     *
     * @param sourceType тип джерела або {@code null}.
     * @return колір зв'язку.
     */
    public Color sourceColor(NeuronType sourceType) {
        if (sourceType == null) {
            return missingSourceColor;
        }
        return switch (sourceType) {
            case EXCITATORY -> excitatoryColor;
            case INHIBITORY -> inhibitoryColor;
            case STRUCTURE -> STRUCTURE_COLOR;
        };
    }

    /**
     * Повертає колір наконечника стрілки відповідно до типу нейрона-джерела.
     * Структурні нейрони мають помаранчевий наконечник, як і лінію зв'язку.
     *
     * @param sourceType тип джерела або {@code null}.
     * @return колір наконечника стрілки.
     */
    public Color arrowColor(NeuronType sourceType) {
        return sourceType == NeuronType.STRUCTURE ? STRUCTURE_ARROW_COLOR : sourceColor(sourceType);
    }

    /**
     * Повертає колір рухомого імпульсу відповідно до активуючого чи гальмівного типу джерела.
     *
     * @param sourceType тип нейрона-джерела.
     * @return колір імпульсу.
     */
    public Color pulseColor(NeuronType sourceType) {
        if (sourceType == NeuronType.STRUCTURE) {
            return STRUCTURE_PULSE_COLOR;
        }
        return sourceType != null && sourceType.isActivating() ? excitatoryPulseColor : inhibitoryPulseColor;
    }

    /**
     * Перевіряє окремий числовий параметр геометрії стилю.
     *
     * @param value значення для перевірки.
     * @param name назва параметра для повідомлення про помилку.
     * @throws IllegalArgumentException якщо значення не є додатним скінченним числом.
     */
    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
