package com.example.neuronmap.i18n;

import java.util.Locale;

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

    public Locale locale() {
        return locale;
    }

    public String displayKey() {
        return displayKey;
    }

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
