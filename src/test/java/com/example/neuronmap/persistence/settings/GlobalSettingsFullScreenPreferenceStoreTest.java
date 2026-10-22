package com.example.neuronmap.persistence.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє типізоване збереження параметра повноекранного режиму у спільному файлі налаштувань. */
final class GlobalSettingsFullScreenPreferenceStoreTest {

    /** Перевіряє вимкнений режим за замовчуванням і збереження обох станів між екземплярами сховища. */
    @Test
    void defaultsToWindowedModeAndPersistsBothStates(@TempDir Path temporaryDirectory) throws Exception {
        Path globalSettingsFile = temporaryDirectory.resolve("global.properties");
        GlobalSettingsFullScreenPreferenceStore store = new GlobalSettingsFullScreenPreferenceStore(
                new GlobalSettingsStore(globalSettingsFile));

        assertFalse(store.loadFullScreenEnabled());
        store.saveFullScreenEnabled(true);
        assertTrue(new GlobalSettingsFullScreenPreferenceStore(
                new GlobalSettingsStore(globalSettingsFile)).loadFullScreenEnabled());

        store.saveFullScreenEnabled(false);
        assertFalse(new GlobalSettingsFullScreenPreferenceStore(
                new GlobalSettingsStore(globalSettingsFile)).loadFullScreenEnabled());
        assertTrue(Files.readString(globalSettingsFile).contains("fullscreen=false"));
    }

    /** Перевіряє, що пошкоджене значення не вмикає повноекранний режим без явної згоди користувача. */
    @Test
    void invalidStoredValueKeepsWindowedModeAsSafeDefault(@TempDir Path temporaryDirectory) throws Exception {
        Path globalSettingsFile = temporaryDirectory.resolve("global.properties");
        Files.writeString(globalSettingsFile, "fullscreen=unexpected\n");

        GlobalSettingsFullScreenPreferenceStore store = new GlobalSettingsFullScreenPreferenceStore(
                new GlobalSettingsStore(globalSettingsFile));

        assertFalse(store.loadFullScreenEnabled());
    }
}
