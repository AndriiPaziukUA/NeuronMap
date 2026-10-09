package com.example.neuronmap.i18n;

import java.util.Locale;

/** Defines supported UI languages and their localized display-name keys. */
public enum SupportedLanguage {
    ENGLISH(Locale.ENGLISH, "language.english"),
    RUSSIAN(Locale.forLanguageTag("ru"), "language.russian"),
    UKRAINIAN(Locale.forLanguageTag("uk"), "language.ukrainian");

    private final Locale locale;
    private final String displayKey;

    /**
     * Associates a supported language with its locale and translated display-name key.
     *
     * @param locale locale used when rendering the language.
     * @param displayKey resource-bundle key for the language's display name.
     */
    SupportedLanguage(Locale locale, String displayKey) {
        this.locale = locale;
        this.displayKey = displayKey;
    }

    /**
     * Returns the locale represented by this language.
     *
     * @return normalized language locale.
     */
    public Locale locale() {
        return locale;
    }

    /**
     * Returns the localization key for this language's display name.
     *
     * @return resource-bundle key for the display name.
     */
    public String displayKey() {
        return displayKey;
    }

    /**
     * Resolves a locale to a supported language, falling back to English for null or unknown locales.
     *
     * @param locale locale to resolve.
     * @return matching supported language, or English when no language matches.
     */
    public static SupportedLanguage fromLocale(Locale locale) {
        if (locale != null) {
            for (SupportedLanguage language : values()) {
                if (language.locale.getLanguage().equalsIgnoreCase(locale.getLanguage())) {
                    return language;
                }
            }
        }
        return ENGLISH;
    }
}
