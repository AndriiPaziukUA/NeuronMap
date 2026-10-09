package com.example.neuronmap.i18n;

import java.util.Locale;

/**
 * Перелічує мови інтерфейсу, підтримувані застосунком, та пов’язує кожну мову з локаллю й ключем назви.
 */
public enum SupportedLanguage {
    ENGLISH(Locale.ENGLISH, "language.english"),
    RUSSIAN(Locale.forLanguageTag("ru"), "language.russian"),
    UKRAINIAN(Locale.forLanguageTag("uk"), "language.ukrainian");

    private final Locale locale;
    private final String displayKey;

    SupportedLanguage(Locale locale, String displayKey) {
        this.locale = locale;
        this.displayKey = displayKey;
    }

    /**
     * Повертає поточну локаль інтерфейсу.
     *
     * @return поточну локаль інтерфейсу.
     */
    public Locale locale() {
        return locale;
    }

    /**
     * Повертає ключ локалізованої назви мови.
     *
     * @return ключ локалізованої назви мови.
     */
    public String displayKey() {
        return displayKey;
    }

    /**
     * Визначає підтримувану мову за локаллю; якщо точного збігу немає, повертає мову за замовчуванням.
     *
     * @param locale локаль, для якої потрібно завантажити або показати текст.
     */
    public static SupportedLanguage fromLocale(Locale locale) {
        if (locale == null) {
            return UKRAINIAN;
        }
        for (SupportedLanguage language : values()) {
            if (language.locale.getLanguage().equalsIgnoreCase(locale.getLanguage())) {
                return language;
            }
        }
        return UKRAINIAN;
    }
}
