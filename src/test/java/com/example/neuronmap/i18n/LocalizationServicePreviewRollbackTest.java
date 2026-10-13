package com.example.neuronmap.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє узгодженість активної та підтвердженої мови під час попереднього перегляду й скасування. */
final class LocalizationServicePreviewRollbackTest {

    /** Перевіряє, що скасування попереднього перегляду відновлює мову й не змінює збережений вибір. */
    @Test
    void discardingPreviewRestoresConfirmedLanguageWithoutPersistingIt() {
        String[] storedLanguage = {"uk"};
        LocalizationService localization = new LocalizationService(preferenceStore(storedLanguage));

        localization.previewLanguage(SupportedLanguage.ENGLISH);

        assertEquals(SupportedLanguage.ENGLISH, localization.language());
        assertEquals(SupportedLanguage.UKRAINIAN, localization.confirmedLanguage());
        assertTrue(localization.hasUnconfirmedLanguagePreview());
        assertEquals("uk", storedLanguage[0]);

        localization.discardLanguagePreview();

        assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
        assertEquals(SupportedLanguage.UKRAINIAN, localization.confirmedLanguage());
        assertFalse(localization.hasUnconfirmedLanguagePreview());
        assertEquals("uk", storedLanguage[0]);
    }

    /** Перевіряє, що збереження підтверджує попередній перегляд і робить його новою точкою відкату. */
    @Test
    void persistingPreviewConfirmsItAsTheNewBaseline() {
        String[] storedLanguage = {"uk"};
        LocalizationService localization = new LocalizationService(preferenceStore(storedLanguage));
        localization.previewLanguage(SupportedLanguage.RUSSIAN);

        localization.persistCurrentLanguage();

        assertEquals(SupportedLanguage.RUSSIAN, localization.language());
        assertEquals(SupportedLanguage.RUSSIAN, localization.confirmedLanguage());
        assertFalse(localization.hasUnconfirmedLanguagePreview());
        assertEquals("ru", storedLanguage[0]);
    }

    /** Перевіряє, що повернення до підтвердженої мови завершує попередній перегляд без запису у сховище. */
    @Test
    void selectingConfirmedLanguageEndsPreviewWithoutSaving() {
        String[] storedLanguage = {"en"};
        LocalizationService localization = new LocalizationService(preferenceStore(storedLanguage));
        localization.previewLanguage(SupportedLanguage.UKRAINIAN);
        localization.previewLanguage(SupportedLanguage.ENGLISH);

        assertEquals(SupportedLanguage.ENGLISH, localization.language());
        assertFalse(localization.hasUnconfirmedLanguagePreview());
        assertEquals("en", storedLanguage[0]);
    }

    /** Створює незалежне тестове сховище, яке зберігає мовний тег у переданому масиві. */
    private static LanguagePreferenceStore preferenceStore(String[] storedLanguage) {
        return new LanguagePreferenceStore() {
            /** Повертає мовний тег, зафіксований тестовим сценарієм. */
            @Override
            public String loadLanguageTag() {
                return storedLanguage[0];
            }

            /** Оновлює значення в пам'яті, не торкаючись файлової системи. */
            @Override
            public void saveLanguageTag(String languageTag) {
                storedLanguage[0] = languageTag;
            }
        };
    }
}
