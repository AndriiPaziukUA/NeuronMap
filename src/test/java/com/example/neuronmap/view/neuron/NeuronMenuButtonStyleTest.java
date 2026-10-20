package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Path;
import javafx.scene.shape.Shape;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє товщину векторних значків, їхні кольори та незмінну геометрію кнопок меню нейрона. */
final class NeuronMenuButtonStyleTest {

    /** Перевіряє тонкі контури значків, силует блискавки, справжню шестерню й незмінні кнопки 32 × 32 px.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void menuIconsUseOnePixelStrokesAndProperGearWithoutResizingButtons() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            NeuronMenuView menuView = new NeuronMenuView(() -> null, ignored -> { }, localization);
            NeuronContextMenuView contextMenuView = new NeuronContextMenuView(
                    localization,
                    menuView::customizeMenuButton,
                    menuView::createDirectionButton
            );
            StackPane root = new StackPane(contextMenuView.createMenu(true, actions()));
            Scene scene = new Scene(root, 420.0, 80.0);
            scene.getStylesheets().add(NeuronMenuButtonStyleTest.class
                    .getResource("/app.css").toExternalForm());
            root.applyCss();
            root.layout();

            VBox menu = (VBox) root.getChildren().getFirst();
            HBox buttons = (HBox) menu.getChildren().getFirst();
            Button activation = (Button) buttons.getChildren().getFirst();
            Button deletion = (Button) buttons.getChildren().getLast();
            assertEquals(Color.web("#c9a52d"), activation.getBackground().getFills().getFirst().getFill(),
                    "Кнопка імпульсу зберігає власний колір");
            assertEquals(Color.web("#b83b3b"), deletion.getBackground().getFills().getFirst().getFill(),
                    "Кнопка видалення зберігає власний колір");
            assertEquals(Color.BLACK, firstIconShape(activation).getStroke(),
                    "Значок імпульсу має відповідати чорному кольору кнопки");
            assertEquals(Color.WHITE, firstIconShape(deletion).getStroke(),
                    "Значок видалення має відповідати білому кольору кнопки");
            assertTrue(firstIconShape(activation).getBoundsInLocal().getWidth() < 8.0,
                    "Заповнений силует блискавки має залишатися вузьким незалежно від товщини CSS-контуру");
            assertSettingsGear((Button) buttons.getChildren().get(5));

            for (Node node : buttons.getChildren()) {
                Button button = (Button) node;
                assertEquals(32.0, button.getWidth(), 0.1, "Ширина кнопок меню має бути однаковою");
                assertEquals(32.0, button.getHeight(), 0.1, "Висота кнопок меню має бути однаковою");
                assertEquals(button.getWidth(), button.getHeight(), 0.1,
                        "Кнопки меню мають бути квадратними");
                assertTrue(button.getText().isBlank(), "Значок не повинен бути текстом кнопки");
                assertNotNull(button.getBorder(), "Кнопка має мати рамку");
                assertEquals(Color.web("#454b55"), button.getBorder().getStrokes().getFirst().getTopStroke());
                assertEquals(Insets.EMPTY, button.getBorder().getStrokes().getFirst().getInsets());
                Background background = button.getBackground();
                assertNotNull(background, "Кнопка має мати фонову заливку");
                assertEquals(new Insets(1.0), background.getFills().getFirst().getInsets(),
                        "Заливка повинна бути відступлена всередину рамки на 1 px");
                assertIconGeometry(button);
            }
        });
    }

    /** Переконується, що значок налаштувань є зубчастою фігурою з прозорим отвором, а не набором променів.
     *
     * @param button кнопка налаштувань, графічний значок якої перевіряється.
     */
    private static void assertSettingsGear(Button button) {
        Pane icon = assertInstanceOf(Pane.class, button.getGraphic());
        assertTrue(icon.getStyleClass().contains("neuron-menu-icon-settings"));
        Path gear = assertInstanceOf(Path.class, icon.getChildren().getFirst());
        assertEquals(FillRule.EVEN_ODD, gear.getFillRule(),
                "Правило заливки має залишати центральний отвір шестерні прозорим");
        assertTrue(gear.getElements().size() >= 30,
                "Зовнішній контур має містити достатньо сегментів для виразних зубців шестерні");
    }

    /** Перевіряє фіксоване полотно та однопіксельну товщину векторних контурів незалежно від шрифту.
     *
     * @param button кнопка, значок якої перевіряється.
     */
    private static void assertIconGeometry(Button button) {
        Pane icon = assertInstanceOf(Pane.class, button.getGraphic());
        assertTrue(icon.getStyleClass().contains("neuron-menu-icon"));
        assertEquals(20.0, icon.getMinWidth());
        assertEquals(20.0, icon.getMinHeight());
        assertEquals(20.0, icon.getPrefWidth());
        assertEquals(20.0, icon.getPrefHeight());
        assertEquals(20.0, icon.getMaxWidth());
        assertEquals(20.0, icon.getMaxHeight());
        assertTrue(icon.isMouseTransparent(), "Графічний значок не повинен перехоплювати дії миші");
        assertFalse(icon.getChildren().isEmpty(), "Полотно має містити векторну геометрію значка");
        for (Node child : icon.getChildren()) {
            Shape shape = assertInstanceOf(Shape.class, child);
            assertTrue(shape.getStyleClass().contains("neuron-menu-icon-stroke")
                            || shape.getStyleClass().contains("neuron-menu-icon-fill"),
                    "Контур значка має отримувати стиль із таблиці стилів нейрона");
            assertEquals(1.0, shape.getStrokeWidth(), 0.01,
                    "Векторні контури значків мають використовувати товщину штриха 1 px");
        }
    }

    /** Повертає першу фігуру графічного значка для перевірки кольору її контуру.
     *
     * @param button кнопка, з якої потрібно отримати значок.
     * @return перша векторна фігура значка.
     */
    private static Shape firstIconShape(Button button) {
        Pane icon = assertInstanceOf(Pane.class, button.getGraphic());
        return assertInstanceOf(Shape.class, icon.getChildren().getFirst());
    }

    /** Створює набір дій-заглушок, потрібний для перевірки лише візуальної поведінки меню.
     *
     * @return набір дій, які не змінюють зовнішній стан.
     */
    private static NeuronContextMenuView.Actions actions() {
        return new NeuronContextMenuView.Actions(
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { }
        );
    }
}
