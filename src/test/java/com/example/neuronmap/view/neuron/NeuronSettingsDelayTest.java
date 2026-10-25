package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє введення, валідацію та збереження параметра затримки у вікні налаштувань нейрона. */
final class NeuronSettingsDelayTest {

    /** Перевіряє локалізований підпис і завантаження значення затримки з моделі нейрона. */
    @Test
    void displaysLocalizedDelayAndLoadsCurrentValue() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            Neuron neuron = new Neuron("delay-setting", NeuronType.EXCITATORY, 0);
            neuron.setDelayTicks(4);
            NeuronSettingsView view = new NeuronSettingsView(localization);
            view.showIn(root, neuron, ignored -> { });

            assertEquals("Затримка:", view.settingLabelTextForTest("i18n.neuronSettings.delay"));
            assertEquals("4", view.delayTicksFieldForTest().getText());
            assertFalse(view.saveButtonForTest().isDisabled());
            view.hide();
        });
    }

    /** Перевіряє заборону нуля та переповнення й застосування додатної затримки під час збереження. */
    @Test
    void validatesAndSavesPositiveDelay() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            Neuron neuron = new Neuron("delay-save", NeuronType.EXCITATORY, 0);
            AtomicReference<NeuronParameters> saved = new AtomicReference<>();
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            view.showIn(root, neuron, saved::set);

            view.delayTicksFieldForTest().setText("0");
            assertTrue(view.saveButtonForTest().isDisabled());
            view.delayTicksFieldForTest().setText("2147483648");
            assertTrue(view.saveButtonForTest().isDisabled());
            view.delayTicksFieldForTest().setText("3");
            assertFalse(view.saveButtonForTest().isDisabled());
            view.saveButtonForTest().fire();

            assertEquals(new NeuronParameters(1, 1, 1, 3), saved.get());
            assertFalse(root.getChildren().contains(view));
        });
    }
}
