package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє ізоляцію CSS та стабільність оформлення селектора режиму активації нейрона. */
final class NeuronSettingsCssIsolationTest {

    /** Переконується, що спільний стиль полів застосовується лише до текстових полів, а не до ComboBox.
     *
     * @throws IOException якщо таблицю стилів неможливо прочитати.
     */
    @Test
    void textFieldStylesDoNotBleedIntoActivationModeSelector() throws IOException {
        String stylesheet;
        try (InputStream stream = getClass().getResourceAsStream("/neuron-settings.css")) {
            assertNotNull(stream, "Таблиця стилів налаштувань нейрона має бути доступною в classpath");
            stylesheet = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(stylesheet.contains(
                ".neuron-settings-overlay .neuron-settings-row .text-field.neuron-settings-field {"),
                "Спільні стилі введення мають застосовуватися тільки до текстових полів");
        assertFalse(stylesheet.contains(
                ".neuron-settings-overlay .neuron-settings-row .neuron-settings-field {"),
                "Не можна застосовувати стиль текстового поля до ComboBox через спільний клас");
        assertTrue(stylesheet.contains(
                ".neuron-settings-overlay .neuron-settings-panel .neuron-settings-activation-selector {"),
                "Для ComboBox має існувати єдиний базовий стиль");
        String selectorRule = ruleBody(stylesheet,
                ".neuron-settings-overlay .neuron-settings-panel .neuron-settings-activation-selector");
        assertTrue(selectorRule.contains("-fx-color: #282d35;"),
                "Lookup-колір не повинен перемикатися на pressed-колір JavaFX під час відкриття popup");
        assertFalse(stylesheet.contains(".neuron-settings-activation-selector:focused"),
                "Фокус не повинен вмикати альтернативний стиль ComboBox");
        assertFalse(stylesheet.contains(".neuron-settings-activation-selector:showing"),
                "Відкриття списку не повинно вмикати альтернативний стиль ComboBox");
        assertTrue(stylesheet.contains(
                ".neuron-settings-overlay .neuron-settings-panel .neuron-settings-activation-selector > .arrow-button {"),
                "Кнопка стрілки має зберігати постійний фон та геометрію");
        assertTrue(stylesheet.contains(".neuron-settings-activation-value {"),
                "Комірка поточного значення має окремий стиль від пунктів popup");
        assertFalse(stylesheet.contains(".neuron-settings-activation-selector .list-cell {"),
                "Загальне правило ComboBox не повинно перекривати hover пунктів popup");
        assertTrue(stylesheet.contains(
                ".neuron-settings-overlay .neuron-settings-panel .combo-box-popup .list-view {"),
                "Фон списку має стилізуватися через селектор, який відкрив popup");
        assertTrue(stylesheet.contains(
                ".neuron-settings-overlay .neuron-settings-panel .neuron-settings-activation-selector .neuron-settings-activation-option:filled:selected {"),
                "Вибраний пункт повинен мати явний звичайний стиль без постійного виділення");
        assertTrue(stylesheet.contains(
                ".neuron-settings-overlay .neuron-settings-panel .neuron-settings-activation-selector .neuron-settings-activation-option:filled:hover {"),
                "Підсвічування наведенням має залишатися окремим станом");
        assertFalse(stylesheet.contains("-fx-background-color: #52647c;"),
                "Вибір пункту не повинен залишати синє/сіре виділення після закриття popup");
        assertTrue(stylesheet.contains("-fx-padding: 5 8 5 8;"),
                "Відступи тексту мають визначатися стилем самого селектора");
        String selectedRule = ruleBody(stylesheet,
                ".neuron-settings-overlay .neuron-settings-panel .neuron-settings-activation-selector .neuron-settings-activation-option:filled:selected");
        assertTrue(selectedRule.contains("-fx-background-color: #282d35;"),
                "Вибраний пункт повинен зберігати звичайний фон");
        assertTrue(selectedRule.contains("-fx-text-fill: #edf0f3;"),
                "Текст вибраного пункту має залишатися світлим");
        String hoverRule = ruleBody(stylesheet,
                ".neuron-settings-overlay .neuron-settings-panel .neuron-settings-activation-selector .neuron-settings-activation-option:filled:hover");
        assertTrue(hoverRule.contains("-fx-background-color: #343a43;"),
                "Наведення має й надалі підсвічувати пункт списку");
        assertTrue(stylesheet.indexOf(
                        ".neuron-settings-overlay .neuron-settings-panel .neuron-settings-activation-selector .neuron-settings-activation-option:filled:hover {")
                        > stylesheet.indexOf(
                        ".neuron-settings-overlay .neuron-settings-panel .neuron-settings-activation-selector .neuron-settings-activation-option:filled:selected {"),
                "Hover-правило має йти після selected-правила, щоб наведення працювало й на вибраному пункті");
    }

    /** Перевіряє, що активація станів фокуса й відкриття не змінює ширину картки або вигляд значення.
     *
     * @throws Exception якщо JavaFX не виконає перевірку в графічному потоці.
     */
    @Test
    void activationSelectorKeepsItsGeometryAndTextColorWhenFocusedAndShowing() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            view.showIn(root, new Neuron("style-isolation", NeuronType.EXCITATORY, 0), ignored -> { });
            root.applyCss();
            root.layout();

            ComboBox<NeuronActivationMode> selector = view.activationModeSelectorForTest();
            assertTrue(selector.getStyleClass().contains("neuron-settings-activation-selector"),
                    "Селектор має використовувати власний клас стилів");
            ListCell<NeuronActivationMode> popupCell = selector.getCellFactory().call(new ListView<>());
            assertTrue(popupCell.getStyleClass().contains("neuron-settings-activation-option"),
                    "Пункт popup має отримувати власний клас CSS незалежно від дерева основної сцени");
            assertTrue(selector.getButtonCell().getStyleClass().contains("neuron-settings-activation-value"),
                    "Комірка значення має використовувати окремий постійний стиль");
            assertFalse(selector.getButtonCell().getStyleClass().contains("neuron-settings-activation-option"),
                    "Комірка значення не повинна успадковувати hover-оформлення пунктів popup");
            assertNull(selector.getOnShowing(),
                    "Відкриття popup не повинно синхронно перекомпонувати всю сцену");
            Insets originalInsets = selector.getInsets();
            Background originalBackground = selector.getBackground();
            Node arrowButton = selector.lookup(".arrow-button");
            assertNotNull(arrowButton, "ComboBox має містити кнопку стрілки");
            Region arrowRegion = assertInstanceOf(
                    Region.class,
                    arrowButton,
                    "Кнопка стрілки має бути JavaFX Region"
            );
            Insets originalArrowInsets = arrowRegion.getInsets();
            Background originalArrowBackground = arrowRegion.getBackground();
            Insets originalValueInsets = selector.getButtonCell().getInsets();
            Background originalValueBackground = selector.getButtonCell().getBackground();
            Insets originalSignalInsets = view.signalFieldForTest().getInsets();
            Background originalSignalBackground = view.signalFieldForTest().getBackground();
            double originalPanelWidth = view.panelForTest().getWidth();
            double originalPanelHeight = view.panelForTest().getHeight();
            Node buttonCell = selector.lookup(".list-cell");
            assertNotNull(buttonCell, "Селектор має містити комірку відображення вибраного режиму");
            assertEquals(Color.web("#edf0f3"), ((Labeled) buttonCell).getTextFill());

            selector.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
            selector.pseudoClassStateChanged(PseudoClass.getPseudoClass("showing"), true);
            root.applyCss();
            root.layout();

            assertEquals(originalInsets, selector.getInsets(),
                    "Фокус не повинен змінювати внутрішні відступи селектора");
            assertEquals(originalBackground, selector.getBackground(),
                    "Відкриття списку не повинно змінювати фон селектора");
            assertEquals(originalArrowInsets, arrowRegion.getInsets(),
                    "Фокус та відкриття списку не повинні змінювати геометрію кнопки стрілки");
            assertEquals(originalArrowBackground, arrowRegion.getBackground(),
                    "Фокус та відкриття списку не повинні змінювати фон кнопки стрілки");
            assertEquals(originalValueInsets, selector.getButtonCell().getInsets(),
                    "Фокус та відкриття списку не повинні змінювати геометрію комірки значення");
            assertEquals(originalValueBackground, selector.getButtonCell().getBackground(),
                    "Фокус та відкриття списку не повинні змінювати фон комірки значення");
            assertEquals(originalSignalInsets, view.signalFieldForTest().getInsets(),
                    "Перефокусування селектора не повинно змінювати геометрію інших полів");
            assertEquals(originalSignalBackground, view.signalFieldForTest().getBackground(),
                    "Перефокусування селектора не повинно змінювати оформлення інших полів");
            assertEquals(originalPanelWidth, view.panelForTest().getWidth(), 0.5,
                    "Ширина картки не повинна змінюватися під час відкриття селектора");
            assertEquals(originalPanelHeight, view.panelForTest().getHeight(), 0.5,
                    "Висота картки не повинна змінюватися під час відкриття селектора");
            assertEquals(Color.web("#edf0f3"), ((Labeled) buttonCell).getTextFill(),
                    "Текст вибраного режиму має залишатися світлим у сфокусованому стані");

            selector.pseudoClassStateChanged(PseudoClass.getPseudoClass("showing"), false);
            selector.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), false);
            root.applyCss();
            root.layout();
            assertEquals(originalPanelWidth, view.panelForTest().getWidth(), 0.5,
                    "Втрата фокуса не повинна повторно змінювати ширину картки");
            assertEquals(originalPanelHeight, view.panelForTest().getHeight(), 0.5,
                    "Втрата фокуса не повинна повторно змінювати висоту картки");
            view.hide();
        });
    }

    /** Повертає тіло CSS-правила, щоб перевіряти властивості конкретного стану окремо.
     *
     * @param stylesheet повний вміст таблиці стилів.
     * @param selector CSS-селектор правила.
     * @return декларації всередині фігурних дужок.
     */
    private static String ruleBody(String stylesheet, String selector) {
        int selectorStart = stylesheet.indexOf(selector);
        assertTrue(selectorStart >= 0, "Не знайдено CSS-селектор " + selector);
        int openingBrace = stylesheet.indexOf('{', selectorStart);
        int closingBrace = stylesheet.indexOf('}', openingBrace);
        assertTrue(openingBrace >= 0 && closingBrace > openingBrace,
                "Некоректний блок CSS для селектора " + selector);
        return stylesheet.substring(openingBrace + 1, closingBrace);
    }
}
