package com.example.neuronmap.util;

import java.util.Locale;

/**
 * Форматує числові значення для компактного відображення без залежності від локалі системи.
 * Цілі значення показуються без десяткових знаків, інші — з одним десятковим знаком.
 */
public final class NumberDisplayFormatter {

    private static final double INTEGER_TOLERANCE = 0.0001;

    /**
     * Забороняє створення екземплярів класу, який містить лише спільну операцію форматування.
     */
    private NumberDisplayFormatter() {
    }

    /**
     * Форматує число без зайвих десяткових знаків і з десятковою крапкою незалежно від локалі системи.
     *
     * @param value числове значення для відображення.
     * @return рядок із нулем десяткових знаків для значень, близьких до цілого, або з одним — для інших.
     */
    public static String format(double value) {
        return formatNumber(value);
    }

    /**
     * Форматує числове значення відсотка за тими самими правилами точності, що й звичайне число.
     *
     * @param percentage значення відсотка, наприклад {@code 100.5} для {@code 100.5%}.
     * @return компактне представлення значення з суфіксом відсотка.
     */
    public static String formatPercentage(double percentage) {
        return formatNumber(percentage) + "%";
    }

    /**
     * Застосовує єдине правило округлення й форматування для всіх підтримуваних представлень чисел.
     *
     * @param value числове значення для відображення.
     * @return рядок із нулем або одним десятковим знаком.
     */
    private static String formatNumber(double value) {
        return Math.abs(value - Math.rint(value)) < INTEGER_TOLERANCE
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
