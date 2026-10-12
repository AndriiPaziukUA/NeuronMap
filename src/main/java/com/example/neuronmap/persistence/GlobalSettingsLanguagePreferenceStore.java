package com.example.neuronmap.persistence;

import com.example.neuronmap.i18n.LanguagePreferenceStore;

/**
 * Адаптує глобальне сховище налаштувань до контракту збереження вибраної мови інтерфейсу.
 */
public final class GlobalSettingsLanguagePreferenceStore implements LanguagePreferenceStore {

    private static final String LANGUAGE_KEY = "language";

    private final GlobalSettingsStore settings;

    /**
     * Створює адаптер для читання та запису мовних налаштувань через глобальне сховище.
     *
     * @param settings сховище глобальних налаштувань застосунку.
     * @throws IllegalArgumentException якщо сховище не задано.
     */
    public GlobalSettingsLanguagePreferenceStore(GlobalSettingsStore settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        this.settings = settings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String loadLanguageTag() {
        return settings.load(LANGUAGE_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveLanguageTag(String languageTag) {
        settings.save(LANGUAGE_KEY, languageTag);
    }
}
