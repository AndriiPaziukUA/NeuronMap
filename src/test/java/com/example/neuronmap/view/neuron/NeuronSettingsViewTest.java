package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє оформлення, локалізацію, введення та компонування внутрішнього вікна налаштувань нейрона. */
final class NeuronSettingsViewTest {

    /** Перевіряє ізольовану таблицю стилів і однакову природну геометрію кнопок. */
    @Test
    void usesDedicatedStylesAndSizesBothButtonsByLargestLabel() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            NeuronSettingsView view = new NeuronSettingsView(localization);
            view.showIn(root, neuron("styles", NeuronType.EXCITATORY, 4, 6), ignored -> { });
            root.applyCss();
            root.layout();

            assertTrue(view.getStylesheets().stream().anyMatch(path -> path.endsWith("/neuron-settings.css")));
            assertFalse(view.getStylesheets().stream().anyMatch(path -> path.endsWith("/menu-enhancements.css")));
            assertTrue(view.saveButtonForTest().getStyleClass().contains("neuron-settings-button"));
            assertTrue(view.cancelButtonForTest().getStyleClass().contains("neuron-settings-button"));
            assertEquals(localization.textFrom("i18n.neuronSettings", "cancel"), view.cancelButtonForTest().getText());
            assertEquals(Region.USE_COMPUTED_SIZE, view.saveButtonForTest().getMaxWidth(), 0.1);
            assertEquals(Region.USE_COMPUTED_SIZE, view.saveButtonForTest().getMaxHeight(), 0.1);
            assertEquals(Region.USE_COMPUTED_SIZE, view.cancelButtonForTest().getMaxWidth(), 0.1);
            assertEquals(Region.USE_COMPUTED_SIZE, view.cancelButtonForTest().getMaxHeight(), 0.1);
            assertEquals(view.saveButtonForTest().getPrefWidth(), view.cancelButtonForTest().getPrefWidth(), 0.1);
            assertEquals(view.saveButtonForTest().getPrefHeight(), view.cancelButtonForTest().getPrefHeight(), 0.1);
            assertEquals(view.saveButtonForTest().getWidth(), view.cancelButtonForTest().getWidth(), 0.5);
            assertEquals(view.saveButtonForTest().getHeight(), view.cancelButtonForTest().getHeight(), 0.5);
            assertEquals(view.saveButtonForTest().getFont(), view.cancelButtonForTest().getFont());
            assertNull(view.saveButtonForTest().getTooltip());
            assertNull(view.cancelButtonForTest().getTooltip());
            assertEquals(Pos.CENTER, view.titleForTest().getAlignment());
            view.hide();
        });
    }

    /** Перевіряє оновлення підписів назви й інших параметрів після зміни мови інтерфейсу. */
    @Test
    void refreshesSettingLabelsImmediatelyWhenLocaleChanges() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            NeuronSettingsView view = new NeuronSettingsView(localization);
            view.showIn(root, neuron("locale", NeuronType.EXCITATORY, 4, 6), ignored -> { });

            String englishSignalLabel = view.settingLabelTextForTest("neuron.settings.signal_strength");
            String englishThresholdLabel = view.settingLabelTextForTest("neuron.settings.activation_threshold");
            String englishActiveTicksLabel = view.settingLabelTextForTest("i18n.neuronSettings.active_ticks");
            String englishNameLabel = view.settingLabelTextForTest("i18n.neuronSettings.name");
            assertEquals("Signal strength:", englishSignalLabel);
            assertEquals("Activation threshold:", englishThresholdLabel);
            assertEquals("Active ticks", englishActiveTicksLabel);
            assertEquals("Name:", englishNameLabel);

            localization.previewLanguage(SupportedLanguage.UKRAINIAN);
            assertEquals(localization.text("neuron.settings.signal_strength"),
                    view.settingLabelTextForTest("neuron.settings.signal_strength"));
            assertEquals(localization.text("neuron.settings.activation_threshold"),
                    view.settingLabelTextForTest("neuron.settings.activation_threshold"));
            assertEquals(localization.textFrom("i18n.neuronSettings", "active_ticks"),
                    view.settingLabelTextForTest("i18n.neuronSettings.active_ticks"));
            assertEquals("Назва:", view.settingLabelTextForTest("i18n.neuronSettings.name"));
            assertNotEquals(englishSignalLabel, view.settingLabelTextForTest("neuron.settings.signal_strength"));
            assertNotEquals(englishThresholdLabel, view.settingLabelTextForTest("neuron.settings.activation_threshold"));
            assertNotEquals(englishActiveTicksLabel, view.settingLabelTextForTest("i18n.neuronSettings.active_ticks"));
            assertNotEquals(englishNameLabel, view.settingLabelTextForTest("i18n.neuronSettings.name"));

            localization.previewLanguage(SupportedLanguage.RUSSIAN);
            assertEquals("Название:", view.settingLabelTextForTest("i18n.neuronSettings.name"));
            view.hide();
        });
    }

    /** Перевіряє, що поле назви приймає будь-які символи, обмежує довжину й зберігає порожнє значення як null. */
    @Test
    void acceptsUnicodeNameWithinLimitAndClearsNameOnSave() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            Neuron neuron = neuron("name", NeuronType.EXCITATORY, 3, 2);
            AtomicReference<String> savedName = new AtomicReference<>("unexpected");
            view.showWithNameIn(root, neuron, (_, name) -> savedName.set(name));

            view.nameFieldForTest().setText("😀 <> Привіт & 'quoted'");
            assertEquals("😀 <> Привіт & 'quoted'", view.nameFieldForTest().getText());
            view.nameFieldForTest().setText("😀".repeat(30));
            assertEquals(30, view.nameFieldForTest().getText().codePointCount(
                    0, view.nameFieldForTest().getText().length()));
            String acceptedName = view.nameFieldForTest().getText();
            view.nameFieldForTest().appendText("x");
            assertEquals(acceptedName, view.nameFieldForTest().getText());

            view.nameFieldForTest().clear();
            view.saveButtonForTest().fire();
            assertNull(savedName.get());
            assertFalse(root.getChildren().contains(view));
        });
    }

    /** Перевіряє, що відкриття накладки не фокусує поля та не залишає виділеного тексту. */
    @Test
    void doesNotFocusOrSelectTextInFieldsWhenOpened() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            view.signalFieldForTest().setText("17");
            view.signalFieldForTest().selectAll();
            view.thresholdFieldForTest().setText("4");
            view.thresholdFieldForTest().selectAll();
            view.activeTicksFieldForTest().setText("4");
            view.activeTicksFieldForTest().selectAll();

            view.showIn(root, neuron("focus", NeuronType.EXCITATORY, 17, 4), ignored -> { });
            assertFalse(view.signalFieldForTest().isFocused());
            assertFalse(view.thresholdFieldForTest().isFocused());
            assertFalse(view.activeTicksFieldForTest().isFocused());
            assertEquals("", view.signalFieldForTest().getSelectedText());
            assertEquals("", view.thresholdFieldForTest().getSelectedText());
            assertEquals("", view.activeTicksFieldForTest().getSelectedText());
            view.hide();
        });
    }

    /** Перевіряє числовий фільтр, додатність значень і блокування переповнення int для кожного поля. */
    @Test
    void acceptsOnlyPositiveIntegersWithinIntegerRange() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            view.showIn(root, neuron("numeric", NeuronType.EXCITATORY, 3, 2), ignored -> { });

            view.signalFieldForTest().setText("12");
            view.signalFieldForTest().replaceText(2, 2, "x");
            assertEquals("12", view.signalFieldForTest().getText());
            view.signalFieldForTest().setText("0");
            assertTrue(view.saveButtonForTest().isDisabled());
            view.signalFieldForTest().setText("2147483648");
            assertTrue(view.saveButtonForTest().isDisabled());
            view.signalFieldForTest().setText("12");
            view.thresholdFieldForTest().setText("2147483648");
            assertTrue(view.saveButtonForTest().isDisabled());
            view.thresholdFieldForTest().setText("2");
            view.activeTicksFieldForTest().setText("0");
            assertTrue(view.saveButtonForTest().isDisabled());
            view.activeTicksFieldForTest().setText("2147483648");
            assertTrue(view.saveButtonForTest().isDisabled());
            view.hide();
        });
    }

    /** Перевіряє зміну всіх параметрів гальмівного нейрона та їх передавання на збереження. */
    @Test
    void savesPositiveParametersForInhibitoryNeuron() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            Neuron neuron = neuron("inhibitory", NeuronType.INHIBITORY, 7, 3);
            AtomicReference<NeuronParameters> saved = new AtomicReference<>();
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            view.showIn(root, neuron, saved::set);

            view.signalFieldForTest().setText("13");
            view.thresholdFieldForTest().setText("5");
            view.activeTicksFieldForTest().setText("4");
            view.saveButtonForTest().fire();

            assertEquals(new NeuronParameters(13, 5, 4), saved.get());
            assertFalse(root.getChildren().contains(view));
        });
    }

    /** Перевіряє, що висота viewport обмежена лімітом і лишається сталою для довгих списків налаштувань. */
    @Test
    void growsSettingsViewportToContentAndCapsLongLists() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            view.showIn(root, neuron("viewport", NeuronType.EXCITATORY, 1, 1), ignored -> { });
            ScrollPane scroll = view.settingsScrollForTest();
            double naturalHeight = scroll.getContent().prefHeight(560.0);
            assertEquals(Math.min(NeuronSettingsLayout.MAX_VIEWPORT_HEIGHT, naturalHeight),
                    scroll.getPrefViewportHeight(), 1.0);
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, scroll.getVbarPolicy());
            assertEquals(ScrollPane.ScrollBarPolicy.NEVER, scroll.getHbarPolicy());
            for (int index = 0; index < 12; index++) {
                Label extraLabel = new Label("Future setting " + index);
                HBox row = new HBox(extraLabel);
                row.setMinHeight(40.0);
                row.setPrefHeight(40.0);
                view.addSetting(row);
            }
            assertEquals(NeuronSettingsLayout.MAX_VIEWPORT_HEIGHT, scroll.getPrefViewportHeight(), 0.5);
            assertTrue(view.panelMaxWidthForTest() <= 920.0);
            assertEquals(430.0, view.panelMaxHeightForTest(), 0.1);
            assertTrue(view.panelForTest().getChildren().contains(scroll));
            view.hide();
        });
    }

    /** Перевіряє, що накладку не можна показати без сцени, потрібної для ізоляції клавіатури. */
    @Test
    void refusesToShowWithoutAttachedScene() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane unattachedRoot = new StackPane();
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            assertThrows(IllegalStateException.class,
                    () -> view.showIn(unattachedRoot, neuron("unattached", NeuronType.EXCITATORY, 1, 1), ignored -> { }));
            view.dispose();
        });
    }

    /** Перевіряє повторне компонування рядків із переносом тексту після зміни доступної ширини. */
    @Test
    void recomputesWrappedRowHeightWhenAvailableWidthChanges() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            root.resize(900.0, 600.0);
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            view.showIn(root, neuron("resize", NeuronType.EXCITATORY, 1, 1), ignored -> { });
            Label wrappedLabel = new Label("A future setting with a deliberately long localized description "
                    + "that must wrap onto several lines when the available panel width becomes narrow.");
            wrappedLabel.setWrapText(true);
            wrappedLabel.setMinWidth(0.0);
            wrappedLabel.setMaxWidth(Double.MAX_VALUE);
            view.addSetting(wrappedLabel);
            root.applyCss();
            root.layout();
            double wideHeight = wrappedLabel.getHeight();
            double wideViewportHeight = view.settingsScrollForTest().getPrefViewportHeight();
            root.resize(420.0, 600.0);
            root.applyCss();
            root.layout();
            view.applyCss();
            view.layout();
            root.layout();
            assertTrue(wrappedLabel.getHeight() > wideHeight + 1.0,
                    "Підпис має займати більше рядків після звуження контейнера");
            assertTrue(view.settingsScrollForTest().getPrefViewportHeight() >= wideViewportHeight,
                    "Висота viewport має враховувати додаткові рядки перенесеного тексту");
            view.hide();
        });
    }

    /** Створює нейрон для тестового сценарію без залежності від стану редактора. */
    private static Neuron neuron(String id, NeuronType type, int signalStrength, int threshold) {
        return new Neuron(id, type, 0, signalStrength, threshold);
    }
}
