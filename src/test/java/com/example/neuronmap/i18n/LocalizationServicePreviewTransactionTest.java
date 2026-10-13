package com.example.neuronmap.i18n;

import com.example.neuronmap.persistence.GlobalSettingsLanguagePreferenceStore;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє транзакційний контракт попереднього перегляду, підтвердження та скасування мови. */
final class LocalizationServicePreviewTransactionTest {

    @TempDir
    Path temporaryDirectory;

    /** Попередній перегляд змінює активну мову, але не підтверджене значення чи файл налаштувань. */
    @Test
    void previewIsTransientUntilExplicitlyPersisted() {
        GlobalSettingsStore store = new GlobalSettingsStore(temporaryDirectory.resolve("preview.properties"));
        store.save("language", "uk");
        LocalizationService localization = new LocalizationService(new GlobalSettingsLanguagePreferenceStore(store));

        localization.previewLanguage(SupportedLanguage.ENGLISH);

        assertEquals(SupportedLanguage.ENGLISH, localization.language());
        assertEquals(SupportedLanguage.UKRAINIAN, localization.confirmedLanguage());
        assertTrue(localization.hasUnconfirmedLanguagePreview());
        assertEquals("uk", store.load("language"));
    }

    /** Скасування відновлює підтверджену мову та негайно робить її джерелом активних перекладів. */
    @Test
    void discardLanguagePreviewRestoresConfirmedLanguageWithoutSavingPreview() {
        GlobalSettingsStore store = new GlobalSettingsStore(temporaryDirectory.resolve("discard.properties"));
        store.save("language", "uk");
        LocalizationService localization = new LocalizationService(new GlobalSettingsLanguagePreferenceStore(store));

        localization.previewLanguage(SupportedLanguage.ENGLISH);
        localization.discardLanguagePreview();

        assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
        assertEquals(SupportedLanguage.UKRAINIAN, localization.confirmedLanguage());
        assertFalse(localization.hasUnconfirmedLanguagePreview());
        assertEquals("uk", store.load("language"));
        assertEquals("Новий проєкт", localization.text("menu.new_project"));
    }

    /** Збереження підтверджує поточний preview, а наступний preview скасовується до нового базового значення. */
    @Test
    void saveMovesRollbackBaselineForward() {
        GlobalSettingsStore store = new GlobalSettingsStore(temporaryDirectory.resolve("save.properties"));
        store.save("language", "uk");
        LocalizationService localization = new LocalizationService(new GlobalSettingsLanguagePreferenceStore(store));

        localization.previewLanguage(SupportedLanguage.ENGLISH);
        localization.persistCurrentLanguage();
        assertEquals("en", store.load("language"));
        assertEquals(SupportedLanguage.ENGLISH, localization.confirmedLanguage());
        assertFalse(localization.hasUnconfirmedLanguagePreview());

        localization.previewLanguage(SupportedLanguage.RUSSIAN);
        localization.discardLanguagePreview();

        assertEquals(SupportedLanguage.ENGLISH, localization.language());
        assertEquals("en", store.load("language"));
    }
}
