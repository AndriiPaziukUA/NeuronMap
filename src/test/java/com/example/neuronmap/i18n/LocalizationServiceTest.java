package com.example.neuronmap.i18n;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Перевіряє порядок назв мов, сповіщення слухачів і тимчасовий перегляд мови без збереження налаштування.
 */
final class LocalizationServiceTest {

    /**
     * Перевіряє сортування назв мов відповідно до поточної локалі.
     */
    @Test
    void languageNamesAreSortedUsingCurrentLanguage() {
        LocalizationService localization =
                new LocalizationService(Locale.forLanguageTag("uk"));

        localization.setLanguage(SupportedLanguage.ENGLISH);
        assertEquals(
                List.of(
                        SupportedLanguage.ENGLISH,
                        SupportedLanguage.RUSSIAN,
                        SupportedLanguage.UKRAINIAN
                ),
                localization.supportedLanguagesInDisplayOrder()
        );

        localization.setLanguage(SupportedLanguage.RUSSIAN);
        assertEquals(
                List.of(
                        SupportedLanguage.ENGLISH,
                        SupportedLanguage.RUSSIAN,
                        SupportedLanguage.UKRAINIAN
                ),
                localization.supportedLanguagesInDisplayOrder()
        );

        assertEquals("Английский", localization.displayName(SupportedLanguage.ENGLISH));
        assertEquals("Русский", localization.displayName(SupportedLanguage.RUSSIAN));
        assertEquals("Украинский", localization.displayName(SupportedLanguage.UKRAINIAN));
    }

    /**
     * Перевіряє, що зміна мови сповіщає зареєстрованих слухачів.
     */
    @Test
    void switchingLanguageNotifiesListeners() {
        LocalizationService localization =
                new LocalizationService(Locale.forLanguageTag("uk"));
        Locale[] observed = new Locale[1];
        localization.addListener(locale -> observed[0] = locale);

        localization.setLanguage(SupportedLanguage.ENGLISH);

        assertEquals(Locale.ENGLISH, observed[0]);
        assertEquals("New Project", localization.text("menu.new_project"));
    }

    /**
     * Перевіряє, що попередній перегляд мови не записує її в налаштування й може бути скасований.
     */
    @Test
    void previewLanguageDoesNotPersistAndCanBeReverted() {
        LocalizationService localization =
                new LocalizationService(Locale.forLanguageTag("uk"));
        assertEquals(SupportedLanguage.UKRAINIAN, localization.language());

        localization.previewLanguage(SupportedLanguage.RUSSIAN);
        assertEquals(SupportedLanguage.RUSSIAN, localization.language());

        localization.previewLanguage(SupportedLanguage.UKRAINIAN);
        assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
    }
}
