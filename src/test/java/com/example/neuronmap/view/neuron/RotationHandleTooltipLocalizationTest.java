package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Перевіряє локалізацію підказки та доступної назви ручки обертання й звільнення слухача після від'єднання. */
final class RotationHandleTooltipLocalizationTest {

    /** Перевіряє переклад підказки й доступної назви для всіх мов та їх очищення після завершення життєвого циклу. */
    @Test
    void tooltipAndAccessibleTextTrackActiveLanguageAndCanBeUnbound() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            RotationHandleView handle = new RotationHandleView();
            handle.bindLocalizedTooltip(localization);

            assertEquals("Rotate", handle.getTooltip().getText());
            assertEquals("Rotate", handle.getAccessibleText());

            localization.setLanguage(SupportedLanguage.UKRAINIAN);
            assertEquals("Повернути", handle.getTooltip().getText());
            assertEquals("Повернути", handle.getAccessibleText());

            localization.setLanguage(SupportedLanguage.RUSSIAN);
            assertEquals("Повернуть", handle.getTooltip().getText());
            assertEquals("Повернуть", handle.getAccessibleText());

            handle.unbindLocalizedTooltip();
            assertNull(handle.getTooltip());
            assertNull(handle.getAccessibleText());
            localization.setLanguage(SupportedLanguage.ENGLISH);
            assertNull(handle.getTooltip());
            assertNull(handle.getAccessibleText());

            handle.dispose();
        });
    }
}
