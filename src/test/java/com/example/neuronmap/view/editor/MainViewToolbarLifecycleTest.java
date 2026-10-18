package com.example.neuronmap.view.editor;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє, що закриття головного подання від'єднує його панель інструментів від зовнішніх слухачів і подій. */
final class MainViewToolbarLifecycleTest {

    /** Перевіряє, що dispose головного подання від'єднує toolbar і не допускає подальших callback-ів. */
    @Test
    void disposingMainViewDetachesToolbarCallbacksAndLocalizationListener() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            AtomicInteger addNeuronCalls = new AtomicInteger();
            MainView view = new MainView(
                    (NeuronType ignored) -> addNeuronCalls.incrementAndGet(),
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    ignored -> { },
                    650.0,
                    localization
            );
            String initialButtonText = view.toolbar().addExcitatoryButton().getText();

            view.toolbar().addExcitatoryButton().fire();
            assertEquals(1, addNeuronCalls.get());

            view.dispose();
            view.dispose();
            view.toolbar().addExcitatoryButton().fire();
            localization.previewLanguage(SupportedLanguage.UKRAINIAN);

            assertEquals(1, addNeuronCalls.get());
            assertEquals(initialButtonText, view.toolbar().addExcitatoryButton().getText());
        });
    }
}
