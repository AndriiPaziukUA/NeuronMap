package com.example.neuronmap.util;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Перевіряє компактне форматування чисел, зокрема допуск для цілих значень і незалежність від локалі.
 */
final class NumberDisplayFormatterTest {

    /** Перевіряє, що цілі та достатньо близькі до цілого значення не мають дробової частини. */
    @Test
    void formatsWholeAndNearlyWholeValuesWithoutFraction() {
        assertEquals("12", NumberDisplayFormatter.format(12.0));
        assertEquals("12", NumberDisplayFormatter.format(12.00009));
        assertEquals("-3", NumberDisplayFormatter.format(-3.0));
    }

    /** Перевіряє, що значення з ненульовою дробовою частиною відображається з одним десятковим знаком. */
    @Test
    void formatsFractionalValuesWithOneDecimalPlace() {
        assertEquals("12.5", NumberDisplayFormatter.format(12.5));
        assertEquals("12.3", NumberDisplayFormatter.format(12.34));
    }

    /** Перевіряє, що системна локаль не змінює десятковий роздільник форматованого значення. */
    @Test
    void alwaysUsesRootLocaleForDecimalSeparator() {
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("12.5", NumberDisplayFormatter.format(12.5));
        } finally {
            Locale.setDefault(previousLocale);
        }
    }
}
