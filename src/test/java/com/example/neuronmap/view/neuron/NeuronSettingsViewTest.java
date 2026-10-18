package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє оформлення, числову валідацію та компонування внутрішнього вікна налаштувань нейрона. */
final class NeuronSettingsViewTest {

    /** Запускає JavaFX toolkit перед тестуванням графічних вузлів.
     *
     * @throws Exception якщо JavaFX toolkit не запускається вчасно.
     */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX toolkit має запуститися");
    }

    /** Перевіряє ізольовану таблицю стилів і однакову природну геометрію кнопок. */
    @Test
    void usesDedicatedStylesAndSizesBothButtonsByLargestLabel() throws Exception {
        runOnFxThread(() -> {
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
            assertEquals(localization.text("menu.rename.cancel"), view.cancelButtonForTest().getText());
            assertEquals(Region.USE_COMPUTED_SIZE, view.saveButtonForTest().getMaxWidth(), 0.1);
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
            assertFalse(root.getChildren().contains(view));
        });
    }

    /** Перевіряє оновлення підписів параметрів відразу після попереднього перегляду іншої мови. */
    @Test
    void refreshesSettingLabelsImmediatelyWhenLocaleChanges() throws Exception {
        runOnFxThread(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            NeuronSettingsView view = new NeuronSettingsView(localization);
            view.showIn(root, neuron("locale", NeuronType.EXCITATORY, 4, 6), ignored -> { });

            String englishSignalLabel = view.settingLabelTextForTest("neuron.settings.signal_strength");
            String englishThresholdLabel = view.settingLabelTextForTest("neuron.settings.activation_threshold");
            assertEquals("Signal strength:", englishSignalLabel);
            assertEquals("Activation threshold:", englishThresholdLabel);

            localization.previewLanguage(SupportedLanguage.UKRAINIAN);

            assertEquals(localization.text("neuron.settings.signal_strength"),
                    view.settingLabelTextForTest("neuron.settings.signal_strength"));
            assertEquals(localization.text("neuron.settings.activation_threshold"),
                    view.settingLabelTextForTest("neuron.settings.activation_threshold"));
            assertFalse(englishSignalLabel.equals(view.settingLabelTextForTest("neuron.settings.signal_strength")));
            assertFalse(englishThresholdLabel.equals(
                    view.settingLabelTextForTest("neuron.settings.activation_threshold")));
            view.hide();
        });
    }

    /** Перевіряє, що відкриття накладки не фокусує поля та не залишає виділеного тексту. */
    @Test
    void doesNotFocusOrSelectTextInFieldsWhenOpened() throws Exception {
        runOnFxThread(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            view.signalFieldForTest().setText("17");
            view.signalFieldForTest().selectAll();
            view.thresholdFieldForTest().setText("4");
            view.thresholdFieldForTest().selectAll();

            view.showIn(root, neuron("focus", NeuronType.EXCITATORY, 17, 4), ignored -> { });

            assertFalse(view.signalFieldForTest().isFocused());
            assertFalse(view.thresholdFieldForTest().isFocused());
            assertEquals("", view.signalFieldForTest().getSelectedText());
            assertEquals("", view.thresholdFieldForTest().getSelectedText());
            view.hide();
        });
    }

    /** Перевіряє числовий фільтр, додатність значень і блокування переповнення int. */
    @Test
    void acceptsOnlyPositiveIntegersWithinIntegerRange() throws Exception {
        runOnFxThread(() -> {
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

            view.hide();
        });
    }

    /** Перевіряє зміну сили сигналу гальмівного нейрона та передавання позитивних параметрів на збереження. */
    @Test
    void savesPositiveSignalStrengthForInhibitoryNeuron() throws Exception {
        runOnFxThread(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            Neuron neuron = neuron("inhibitory", NeuronType.INHIBITORY, 7, 3);
            AtomicReference<NeuronParameters> saved = new AtomicReference<>();
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            view.showIn(root, neuron, saved::set);

            view.signalFieldForTest().setText("13");
            view.thresholdFieldForTest().setText("5");
            view.saveButtonForTest().fire();

            assertEquals(new NeuronParameters(13, 5), saved.get());
            assertFalse(root.getChildren().contains(view));
        });
    }

    /** Перевіряє природну висоту списку та появу прокручування лише після перевищення ліміту. */
    @Test
    void growsSettingsViewportToContentAndCapsLongLists() throws Exception {
        runOnFxThread(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            view.showIn(root, neuron("viewport", NeuronType.EXCITATORY, 1, 1), ignored -> { });
            ScrollPane scroll = view.settingsScrollForTest();
            double naturalHeight = scroll.getContent().prefHeight(560.0);
            assertEquals(naturalHeight, scroll.getPrefViewportHeight(), 1.0);
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, scroll.getVbarPolicy());
            assertEquals(ScrollPane.ScrollBarPolicy.NEVER, scroll.getHbarPolicy());

            for (int index = 0; index < 12; index++) {
                Label extraLabel = new Label("Future setting " + index);
                HBox row = new HBox(extraLabel);
                row.setMinHeight(40.0);
                row.setPrefHeight(40.0);
                view.addSetting(row);
            }
            assertEquals(260.0, scroll.getPrefViewportHeight(), 0.5);
            assertTrue(view.panelMaxWidthForTest() <= 920.0);
            assertEquals(430.0, view.panelMaxHeightForTest(), 0.1);
            assertTrue(view.panelForTest().getChildren().contains(scroll));
            view.hide();
        });
    }

    /** Створює нейрон для тестового сценарію без залежності від стану редактора. */
    private static Neuron neuron(String id, NeuronType type, int signalStrength, int threshold) {
        return new Neuron(id, type, 0, signalStrength, threshold);
    }

    /** Виконує UI-перевірку в JavaFX Application Thread і передає помилку до JUnit. */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX action має завершитися");
        if (failure[0] != null) {
            throw new AssertionError("Перевірка налаштувань нейрона завершилася помилкою", failure[0]);
        }
    }
}
