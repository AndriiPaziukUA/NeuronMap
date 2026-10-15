package com.example.neuronmap.i18n;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє використання базових англійських ресурсів для основного й тематичного перекладів. */
final class EnglishBaseBundleTest {

    private static final String WINDOW_CHROME_BUNDLE = "i18n.windowChrome";

    /** Перевіряє, що після вибору англійської служба використовує базові ресурси без суфікса _en. */
    @Test
    void switchingToEnglishUsesBaseBundles() {
        LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
        assertEquals("Новий проєкт", localization.text("menu.new_project"));

        localization.setLanguage(SupportedLanguage.ENGLISH);

        assertEquals("New Project", localization.text("menu.new_project"));
        assertEquals("Minimize", localization.textFrom(WINDOW_CHROME_BUNDLE, "minimize"));
        assertEquals("Maximize", localization.textFrom(WINDOW_CHROME_BUNDLE, "maximize"));
        assertEquals("Restore", localization.textFrom(WINDOW_CHROME_BUNDLE, "restore"));
        assertEquals("Close", localization.textFrom(WINDOW_CHROME_BUNDLE, "close"));
    }

    /** Перевіряє, що перехід на англійську й назад не порушує українські тематичні переклади. */
    @Test
    void localizedBundlesStillWorkAfterSwitchingThroughEnglish() {
        LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));

        localization.setLanguage(SupportedLanguage.ENGLISH);
        assertEquals("New Project", localization.text("menu.new_project"));
        assertEquals("Minimize", localization.textFrom(WINDOW_CHROME_BUNDLE, "minimize"));

        localization.setLanguage(SupportedLanguage.UKRAINIAN);
        assertEquals("Новий проєкт", localization.text("menu.new_project"));
        assertEquals("Згорнути", localization.textFrom(WINDOW_CHROME_BUNDLE, "minimize"));
    }
}
