package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє вибір режиму активації в налаштуваннях, локалізацію та перевірку відсоткового порога. */
final class NeuronSettingsActivationModeTest {

    /** Переконується, що список показує українські підписи та завантажує з моделі поточний режим. */
    @Test
    void selectorUsesLocalizedLabelsAndLoadsExistingActivationMode() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            NeuronSettingsView view = new NeuronSettingsView(localization);
            Neuron neuron = new Neuron("count", NeuronType.EXCITATORY, 0,
                    new NeuronParameters(2, 33, 2, 1, NeuronActivationMode.SIGNAL_COUNT));

            view.showIn(root, neuron, ignored -> { });

            ComboBox<NeuronActivationMode> selector = view.activationModeSelectorForTest();
            assertEquals(NeuronActivationMode.SIGNAL_COUNT, selector.getValue());
            assertEquals("Силою сигналів", selector.getConverter().toString(NeuronActivationMode.SIGNAL_STRENGTH));
            assertEquals("Кількістю сигналів", selector.getConverter().toString(NeuronActivationMode.SIGNAL_COUNT));
            assertEquals("Активація за:", view.settingLabelTextForTest("i18n.neuronSettings.activation_by"));
            assertEquals("Ціле число від 0 до 100", view.thresholdFieldForTest().getPromptText());
            String ukrainianCountLabel = selector.getConverter().toString(NeuronActivationMode.SIGNAL_COUNT);

            localization.previewLanguage(SupportedLanguage.RUSSIAN);
            assertEquals(localization.textFrom("i18n.neuronSettings", "activation_mode_count"),
                    selector.getConverter().toString(NeuronActivationMode.SIGNAL_COUNT));
            assertNotEquals(ukrainianCountLabel,
                    selector.getConverter().toString(NeuronActivationMode.SIGNAL_COUNT));
            localization.previewLanguage(SupportedLanguage.UKRAINIAN);
            view.hide();
        });
    }

    /** Перевіряє, що popup-hover явно перекриває selected-стан у власній таблиці стилів. */
    @Test
    void popupItemsUseTheSameHoverHighlightAsLanguageMenu() throws IOException {
        String stylesheet;
        try (InputStream stream = getClass().getResourceAsStream("/neuron-settings-popup.css")) {
            assertNotNull(stream, "Таблиця стилів popup налаштувань має бути доступною в classpath");
            stylesheet = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(stylesheet.contains(
                ".neuron-settings-activation-popup.list-view .list-cell.neuron-settings-activation-option:filled:selected:hover"),
                "Правило наведення на вибрану комірку має явно перекривати selected-стан");
        assertTrue(stylesheet.contains("-fx-background-color: #343a43;"),
                "Колір наведення має збігатися з підсвічуванням пунктів у меню мови");
    }

    /** Перевіряє незмінну висоту рядка під час переходу між режимами після закриття списку. */
    @Test
    void keepsActivationRowHeightStableWhenSelectedModeChanges() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            NeuronSettingsView view = new NeuronSettingsView(
                    new LocalizationService(Locale.forLanguageTag("uk")));
            view.showIn(root, new Neuron("layout", NeuronType.EXCITATORY, 0), ignored -> { });
            root.applyCss();
            root.layout();

            ComboBox<NeuronActivationMode> selector = view.activationModeSelectorForTest();
            HBox activationRow = (HBox) selector.getParent();
            double initialRowHeight = activationRow.getHeight();
            double initialSelectorHeight = selector.getHeight();
            assertTrue(initialRowHeight > 0.0, "Рядок має бути виміряний після початкового layout");
            assertTrue(initialSelectorHeight > 0.0, "Селектор має бути виміряний після початкового layout");

            selector.pseudoClassStateChanged(PseudoClass.getPseudoClass("showing"), true);
            root.applyCss();
            root.layout();
            for (NeuronActivationMode mode : NeuronActivationMode.values()) {
                selector.setValue(mode);
                selector.fireEvent(new ActionEvent());
                selector.pseudoClassStateChanged(PseudoClass.getPseudoClass("showing"), false);
                selector.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
                root.applyCss();
                root.layout();

                assertEquals(initialSelectorHeight, selector.getHeight(), 0.1,
                        "Висота селектора не повинна залежати від вибраного режиму");
                assertEquals(initialRowHeight, activationRow.getHeight(), 0.1,
                        "Рядок параметра не повинен змінювати висоту після вибору режиму");
            }
            view.hide();
        });
    }

    /** Перевіряє, що нульовий відсоток можна зберегти лише в режимі кількості сигналів. */
    @Test
    void savesZeroPercentAndRejectsValuesAboveOneHundred() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            NeuronSettingsView view = new NeuronSettingsView(
                    new LocalizationService(Locale.forLanguageTag("uk")));
            Neuron neuron = new Neuron("threshold", NeuronType.EXCITATORY, 0);
            AtomicReference<NeuronParameters> saved = new AtomicReference<>();
            view.showIn(root, neuron, saved::set);

            ComboBox<NeuronActivationMode> selector = view.activationModeSelectorForTest();
            selector.setValue(NeuronActivationMode.SIGNAL_COUNT);
            selector.fireEvent(new ActionEvent());
            view.thresholdFieldForTest().setText("101");
            assertTrue(view.saveButtonForTest().isDisabled());

            view.thresholdFieldForTest().setText("0");
            assertFalse(view.saveButtonForTest().isDisabled());
            view.saveButtonForTest().fire();

            assertNotNull(saved.get());
            assertEquals(NeuronActivationMode.SIGNAL_COUNT, saved.get().activationMode());
            assertEquals(0, saved.get().activationThreshold());
        });
    }

    /** Переконується, що перехід між режимами перемикає підказку відсотків та відновлює стандартне поле числа. */
    @Test
    void thresholdPromptChangesWithActivationMode() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            view.showIn(root, new Neuron("prompt", NeuronType.EXCITATORY, 0), ignored -> { });
            ComboBox<NeuronActivationMode> selector = view.activationModeSelectorForTest();

            assertEquals("Integer > 0", view.thresholdFieldForTest().getPromptText());
            selector.setValue(NeuronActivationMode.SIGNAL_COUNT);
            selector.fireEvent(new ActionEvent());
            assertEquals("Whole number from 0 to 100", view.thresholdFieldForTest().getPromptText());
            selector.setValue(NeuronActivationMode.SIGNAL_STRENGTH);
            selector.fireEvent(new ActionEvent());
            assertEquals("Integer > 0", view.thresholdFieldForTest().getPromptText());
            view.hide();
        });
    }
}
