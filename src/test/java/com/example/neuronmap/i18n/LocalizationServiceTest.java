package com.example.neuronmap.i18n;

import com.example.neuronmap.persistence.GlobalSettingsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression tests for localization defaults, localized sorting, listener updates, and resource consistency. */
final class LocalizationServiceTest {

    @TempDir
    Path temporaryDirectory;

    /** Verifies that a missing language preference defaults to English. */
    @Test
    void missingLanguagePreferenceDefaultsToEnglish() {
        LocalizationService localization = new LocalizationService(
                new GlobalSettingsStore(temporaryDirectory.resolve("missing-settings.properties")));

        assertEquals(SupportedLanguage.ENGLISH, localization.language());
        assertEquals("New Project", localization.text("menu.new_project"));
    }

    /** Verifies sorting of supported-language names using the currently active locale. */
    @Test
    void languageNamesAreSortedUsingCurrentLanguage() {
        LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));

        localization.setLanguage(SupportedLanguage.ENGLISH);
        assertEquals(List.of(
                SupportedLanguage.ENGLISH,
                SupportedLanguage.RUSSIAN,
                SupportedLanguage.UKRAINIAN
        ), localization.supportedLanguagesInDisplayOrder());

        localization.setLanguage(SupportedLanguage.RUSSIAN);
        assertEquals(List.of(
                SupportedLanguage.ENGLISH,
                SupportedLanguage.RUSSIAN,
                SupportedLanguage.UKRAINIAN
        ), localization.supportedLanguagesInDisplayOrder());
        assertEquals("Английский", localization.displayName(SupportedLanguage.ENGLISH));
        assertEquals("Русский", localization.displayName(SupportedLanguage.RUSSIAN));
        assertEquals("Украинский", localization.displayName(SupportedLanguage.UKRAINIAN));
    }

    /** Verifies that language changes notify listeners and load the corresponding text bundle. */
    @Test
    void switchingLanguageNotifiesListeners() {
        LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
        Locale[] observed = new Locale[1];
        localization.addListener(locale -> observed[0] = locale);

        localization.setLanguage(SupportedLanguage.ENGLISH);

        assertEquals(Locale.ENGLISH, observed[0]);
        assertEquals("New Project", localization.text("menu.new_project"));
    }

    /** Verifies that previewing a language does not persist it and can be reverted. */
    @Test
    void previewLanguageDoesNotPersistAndCanBeReverted() {
        LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
        assertEquals(SupportedLanguage.UKRAINIAN, localization.language());

        localization.previewLanguage(SupportedLanguage.RUSSIAN);
        assertEquals(SupportedLanguage.RUSSIAN, localization.language());

        localization.previewLanguage(SupportedLanguage.UKRAINIAN);
        assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
    }

    /** Ensures that the new menu labels exist in every supported bundle and the removed hint is absent. */
    @Test
    void menuTranslationsAreConsistentAcrossSupportedLocales() {
        for (Locale locale : List.of(
                Locale.ROOT,
                Locale.ENGLISH,
                Locale.forLanguageTag("ru"),
                Locale.forLanguageTag("uk"))) {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);
            assertTrue(bundle.containsKey("menu.continue"));
            assertTrue(bundle.containsKey("common.save"));
            assertFalse(bundle.containsKey("menu.open_hint"));
        }
        LocalizationService ukrainian = new LocalizationService(Locale.forLanguageTag("uk"));
        assertEquals("Продовжити", ukrainian.text("menu.continue"));
    }

    /** Verifies that unsupported or null locales resolve to English rather than another fallback language. */
    @Test
    void unsupportedLocalesFallBackToEnglish() {
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromLocale(null));
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromLocale(Locale.forLanguageTag("fr")));
    }
}
