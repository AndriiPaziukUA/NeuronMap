package com.example.neuronmap.view.window;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє локалізацію власного заголовка через централізовану службу застосунку. */
final class WindowChromeLocalizationTest {

    private static final String BUNDLE_BASE_NAME = "i18n.windowChrome";

    /** Перевіряє англійські назви кнопок через спільний механізм отримання перекладів. */
    @Test
    void providesEnglishLabelsByDefault() {
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);

        assertEquals("Minimize", localization.textFrom(BUNDLE_BASE_NAME, "minimize"));
        assertEquals("Maximize", localization.textFrom(BUNDLE_BASE_NAME, "maximize"));
        assertEquals("Restore", localization.textFrom(BUNDLE_BASE_NAME, "restore"));
        assertEquals("Close", localization.textFrom(BUNDLE_BASE_NAME, "close"));
    }

    /** Перевіряє, що заголовок отримує переклади активної локалі та попереднього перегляду мови. */
    @Test
    void followsActiveLocaleWhenLanguageChanges() {
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);

        localization.setLanguage(SupportedLanguage.UKRAINIAN);
        assertEquals("Згорнути", localization.textFrom(BUNDLE_BASE_NAME, "minimize"));
        assertEquals("Відновити", localization.textFrom(BUNDLE_BASE_NAME, "restore"));

        localization.previewLanguage(SupportedLanguage.RUSSIAN);
        assertEquals("Свернуть", localization.textFrom(BUNDLE_BASE_NAME, "minimize"));
        assertEquals("Восстановить", localization.textFrom(BUNDLE_BASE_NAME, "restore"));
    }

    /** Перевіряє поведінку для відсутнього ключа та валідацію імені ресурсного набору. */
    @Test
    void handlesMissingKeysAndInvalidBundleNames() {
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);

        assertEquals("unknown.label", localization.textFrom(BUNDLE_BASE_NAME, "unknown.label"));
        assertEquals("", localization.textFrom(BUNDLE_BASE_NAME, " "));
        assertThrows(IllegalArgumentException.class, () -> localization.textFrom(" ", "minimize"));
    }
}
