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
     * Форматує значення без зайвих десяткових знаків і з десятковою крапкою незалежно від системної локалі.
     *
     * @param value числове значення для відображення.
     * @return рядок із нулем десяткових знаків для значень, близьких до цілого, або з одним — для інших.
     */
    public static String format(double value) {
        return Math.abs(value - Math.rint(value)) < INTEGER_TOLERANCE
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
