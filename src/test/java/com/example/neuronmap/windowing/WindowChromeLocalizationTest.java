package com.example.neuronmap.windowing;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє переклади назв кнопок власного заголовка для мов, підтримуваних застосунком. */
final class WindowChromeLocalizationTest {

    /** Перевіряє англійські та резервні переклади кнопок заголовка. */
    @Test
    void providesEnglishLabelsByDefault() {
        ResourceBundle labels = ResourceBundle.getBundle("i18n.windowChrome", Locale.ENGLISH);

        assertEquals("Minimize", labels.getString("minimize"));
        assertEquals("Maximize", labels.getString("maximize"));
        assertEquals("Restore", labels.getString("restore"));
        assertEquals("Close", labels.getString("close"));
    }

    /** Перевіряє, що підказки заголовка перекладені українською та російською мовами. */
    @Test
    void providesLocalizedLabelsForSupportedLanguages() {
        ResourceBundle ukrainian = ResourceBundle.getBundle("i18n.windowChrome", Locale.forLanguageTag("uk-UA"));
        ResourceBundle russian = ResourceBundle.getBundle("i18n.windowChrome", Locale.forLanguageTag("ru-RU"));

        assertEquals("Згорнути", ukrainian.getString("minimize"));
        assertEquals("Відновити", ukrainian.getString("restore"));
        assertEquals("Свернуть", russian.getString("minimize"));
        assertEquals("Восстановить", russian.getString("restore"));
    }
}
