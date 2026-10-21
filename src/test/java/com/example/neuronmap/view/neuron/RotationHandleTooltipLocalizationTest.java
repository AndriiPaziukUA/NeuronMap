package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Перевіряє локалізацію підказки ручки обертання та звільнення слухача після від'єднання. */
final class RotationHandleTooltipLocalizationTest {

    /** Перевіряє переклад для всіх підтримуваних мов і від'єднання підказки після завершення життєвого циклу. */
    @Test
    void tooltipTracksActiveLanguageAndCanBeUnbound() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            RotationHandleView handle = new RotationHandleView();
            handle.bindLocalizedTooltip(localization);

            assertEquals("Rotate", handle.getTooltip().getText());

            localization.setLanguage(SupportedLanguage.UKRAINIAN);
            assertEquals("Повернути", handle.getTooltip().getText());

            localization.setLanguage(SupportedLanguage.RUSSIAN);
            assertEquals("Повернуть", handle.getTooltip().getText());

            handle.unbindLocalizedTooltip();
            assertNull(handle.getTooltip());
            localization.setLanguage(SupportedLanguage.ENGLISH);
            assertNull(handle.getTooltip());

            handle.dispose();
        });
    }
}
