package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що оформлення popup ізольоване від основної сцени й не змінюється після повторного відкриття. */
final class NeuronSettingsCssIsolationTest {

    private static final String SELECTED_ITEM_TEXT = "Selected";

    /**
     * Перевіряє, що popup-таблиця стилів зберігається на кореневому вузлі popup, діє на його комірки
     * та не дозволяє стандартному синьому selected-оформленню перекривати потрібний hover.
     *
     * @throws Exception якщо JavaFX не виконає перевірку в графічному потоці.
     */
    @Test
    void popupStylesStayOnPopupRootAndHoverOverridesSelection() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Pane popupRoot = new Pane();
            ListView<String> list = new ListView<>(FXCollections.observableArrayList(SELECTED_ITEM_TEXT, "Other"));
            list.setPrefSize(220.0, 100.0);
            list.setCellFactory(_ -> {
                ListCell<String> cell = new ListCell<>() {
                    /** Відображає текст пункту, щоб тест перевіряв справжню стилізовану комірку. */
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item);
                    }
                };
                cell.getStyleClass().add("neuron-settings-activation-option");
                return cell;
            });
            popupRoot.getChildren().add(list);
            Scene popupScene = new Scene(popupRoot, 220.0, 100.0);
            URL stylesheetResource = Objects.requireNonNull(
                    NeuronSettingsForm.class.getResource("/neuron-settings-popup.css"),
                    "Missing classpath resource /neuron-settings-popup.css"
            );
            String stylesheet = stylesheetResource.toExternalForm();
            list.resizeRelocate(0.0, 0.0, 220.0, 100.0);
            list.getSelectionModel().select(0);
            popupRoot.applyCss();
            popupRoot.layout();
            list.applyCss();
            list.layout();

            ListCell<?> selectedCell = findSelectedCell(list);
            assertNotNull(selectedCell, "Вибраний пункт має бути створений у видимому ListView");
            NeuronSettingsForm.attachPopupStylesheetToListView(selectedCell);
            NeuronSettingsForm.attachPopupStylesheetToListView(selectedCell);
            popupRoot.applyCss();
            popupRoot.layout();
            list.applyCss();
            list.layout();

            assertEquals(1L, list.getStylesheets().stream().filter(stylesheet::equals).count(),
                    "Popup-таблиця має додаватися до ListView лише один раз");
            assertFalse(popupRoot.getStylesheets().contains(stylesheet),
                    "Таблиця стилів має бути на ListView, а не покладатися на popup-корінь");
            assertFalse(popupScene.getStylesheets().contains(stylesheet),
                    "Не слід змінювати Scene.getStylesheets(): JavaFX може перебудувати цей список для popup");
            assertEquals(Color.web("#282d35"), firstBackgroundColor(selectedCell),
                    "Вибраний пункт без наведення має залишатися темним, а не синім чи білим");
            assertEquals(Color.web("#edf0f3"), selectedCell.getTextFill(),
                    "Текст вибраного пункту має залишатися світлим");

            selectedCell.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
            popupRoot.applyCss();
            popupRoot.layout();
            assertEquals(Color.web("#343a43"), firstBackgroundColor(selectedCell),
                    "Наведення має підсвічувати пункт навіть коли він вибраний");
            assertEquals(Color.WHITE, selectedCell.getTextFill(),
                    "Під час наведення текст пункту має залишатися контрастним");
        });
    }

    /**
     * Перевіряє, що фокус і відкриття селектора не змінюють стиль інших полів, а повторний показ форми
     * не отримує новий розмір через вимірювання scrollbar до застосування CSS.
     *
     * @throws Exception якщо JavaFX не виконає перевірку в графічному потоці.
     */
    @Test
    void activationSelectorKeepsGeometryAndLabelStyleAfterReopening() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            Neuron neuron = new Neuron("style-isolation", NeuronType.EXCITATORY, 0);
            view.showIn(root, neuron, ignored -> { });
            root.applyCss();
            root.layout();

            ComboBox<NeuronActivationMode> selector = view.activationModeSelectorForTest();
            assertTrue(selector.getStyleClass().contains("neuron-settings-activation-selector"),
                    "Селектор має використовувати власний CSS-клас");
            assertTrue(selector.getButtonCell().getStyleClass().contains("neuron-settings-activation-value"),
                    "Комірка значення має окремий стиль від пунктів popup");
            assertFalse(selector.getButtonCell().getStyleClass().contains("neuron-settings-activation-option"),
                    "Комірка значення не повинна отримувати hover-стиль пунктів popup");

            Insets originalSelectorInsets = selector.getInsets();
            Background originalSelectorBackground = selector.getBackground();
            Insets originalValueInsets = selector.getButtonCell().getInsets();
            Background originalValueBackground = selector.getButtonCell().getBackground();
            Insets originalSignalInsets = view.signalFieldForTest().getInsets();
            Background originalSignalBackground = view.signalFieldForTest().getBackground();
            double originalPanelWidth = view.panelForTest().getWidth();
            double originalPanelHeight = view.panelForTest().getHeight();
            Labeled valueCell = selector.getButtonCell();
            assertNotNull(valueCell, "Селектор має містити власну комірку вибраного значення");
            assertEquals(Color.web("#edf0f3"), valueCell.getTextFill(),
                    "Тест має перевіряти саме button cell, а не довільний вузол зі skin");

            selector.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
            selector.pseudoClassStateChanged(PseudoClass.getPseudoClass("showing"), true);
            root.applyCss();
            root.layout();

            assertEquals(originalSelectorInsets, selector.getInsets(),
                    "Відкриття списку не повинно змінювати відступи селектора");
            assertEquals(originalSelectorBackground, selector.getBackground(),
                    "Відкриття списку не повинно змінювати фон селектора");
            assertEquals(originalValueInsets, selector.getButtonCell().getInsets(),
                    "Відкриття списку не повинно змінювати відступи комірки значення");
            assertEquals(originalValueBackground, selector.getButtonCell().getBackground(),
                    "Відкриття списку не повинно змінювати фон комірки значення");
            assertEquals(originalSignalInsets, view.signalFieldForTest().getInsets(),
                    "Фокус селектора не повинен змінювати геометрію інших полів");
            assertEquals(originalSignalBackground, view.signalFieldForTest().getBackground(),
                    "Фокус селектора не повинен змінювати оформлення інших полів");
            assertEquals(originalPanelWidth, view.panelForTest().getWidth(), 0.5,
                    "Ширина картки не повинна змінюватися під час відкриття списку");
            assertEquals(originalPanelHeight, view.panelForTest().getHeight(), 0.5,
                    "Висота картки не повинна змінюватися під час відкриття списку");
            assertEquals(Color.web("#edf0f3"), valueCell.getTextFill(),
                    "Текст вибраного значення має зберігати світлий колір");

            selector.pseudoClassStateChanged(PseudoClass.getPseudoClass("showing"), false);
            selector.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), false);
            root.applyCss();
            root.layout();
            view.hide();
            view.showIn(root, neuron, ignored -> { });
            root.applyCss();
            root.layout();

            assertEquals(originalPanelWidth, view.panelForTest().getWidth(), 0.5,
                    "Повторне відкриття не повинно звужувати картку через нестабільне CSS-вимірювання scrollbar");
            assertEquals(originalPanelHeight, view.panelForTest().getHeight(), 0.5,
                    "Повторне відкриття не повинно змінювати висоту картки");
            view.hide();
        });
    }

    /** Знаходить створену комірку за її значенням, не покладаючись на порядок вузлів у skin. */
    private static ListCell<?> findSelectedCell(ListView<?> list) {
        Set<Node> cells = list.lookupAll(".neuron-settings-activation-option");
        for (Node node : cells) {
            if (node instanceof ListCell<?> cell && SELECTED_ITEM_TEXT.equals(cell.getItem())) {
                return cell;
            }
        }
        return null;
    }

    /** Повертає суцільний колір першої заливки фону комірки для перевірки CSS-каскаду. */
    private static Color firstBackgroundColor(Region region) {
        Background background = region.getBackground();
        assertNotNull(background, "Для комірки має бути явно визначений фон");
        assertFalse(background.getFills().isEmpty(), "Фон комірки має містити заливку");
        Object fill = background.getFills().getFirst().getFill();
        if (!(fill instanceof Color color)) {
            throw new AssertionError("Очікувався суцільний колір фону, отримано: " + fill);
        }
        return color;
    }
}
