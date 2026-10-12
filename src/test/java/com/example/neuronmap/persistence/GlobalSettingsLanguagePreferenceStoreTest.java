package com.example.neuronmap.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Перевіряє збереження мовних налаштувань через адаптер глобального сховища.
 */
final class GlobalSettingsLanguagePreferenceStoreTest {

    /**
     * Перевіряє читання, оновлення та очищення мовного налаштування.
     */
    @Test
    void loadsSavesAndClearsLanguagePreference(@TempDir Path temporaryDirectory) {
        GlobalSettingsStore settings = new GlobalSettingsStore(
                temporaryDirectory.resolve("settings.properties")
        );
        GlobalSettingsLanguagePreferenceStore preferences =
                new GlobalSettingsLanguagePreferenceStore(settings);

        assertNull(preferences.loadLanguageTag());

        preferences.saveLanguageTag("uk");
        assertEquals("uk", preferences.loadLanguageTag());
        assertEquals("uk", settings.load("language"));

        preferences.saveLanguageTag(null);
        assertNull(preferences.loadLanguageTag());
    }
}
