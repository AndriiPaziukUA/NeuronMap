package com.example.neuronmap.i18n;

import java.util.Locale;

/**
 * Перелічує мови, які доступні в застосунку, та їхні властивості.
 */
public enum SupportedLanguage {
    ENGLISH(Locale.ENGLISH, "language.english"),
    RUSSIAN(Locale.forLanguageTag("ru"), "language.russian"),
    UKRAINIAN(Locale.forLanguageTag("uk"), "language.ukrainian");

    private final Locale locale;
    private final String displayKey;

    /**
     * Створює обʼєкт SupportedLanguage та ініціалізує його початковий стан.
     *
     * @param locale значення, що визначає відповідну операцію для цієї операції.
     *
     * @param displayKey значення, що визначає відображати ключ для цієї операції.
     */
    SupportedLanguage(Locale locale, String displayKey) {
        this.locale = locale;
        this.displayKey = displayKey;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Locale locale() {
        return locale;
    }

    /**
     * Відображає «ключ» в інтерфейсі.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    public String displayKey() {
        return displayKey;
    }

    /**
     * Повертає результат операції «із».
     *
     * @param locale значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
