package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Перевіряє спільну квадратну геометрію кнопок меню нейрона та відокремлення заливки від рамки. */
final class NeuronMenuButtonStyleTest {

    /** Переконується, що всі кнопки мають однаковий квадратний розмір і спільну рамку з внутрішньою заливкою. */
    @Test
    void menuButtonsShareSquareDimensionsAndConsistentBorderInsets() throws Exception {
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
            assertEquals(Color.web("#c9a52d"), ((Button) buttons.getChildren().getFirst())
                    .getBackground().getFills().getFirst().getFill(), "Кнопка імпульсу зберігає власний колір");
            assertEquals(Color.web("#b83b3b"), ((Button) buttons.getChildren().getLast())
                    .getBackground().getFills().getFirst().getFill(), "Кнопка видалення зберігає власний колір");
            for (javafx.scene.Node node : buttons.getChildren()) {
                Button button = (Button) node;
                assertEquals(32.0, button.getWidth(), 0.1, "Ширина кнопок меню має бути однаковою");
                assertEquals(32.0, button.getHeight(), 0.1, "Висота кнопок меню має бути однаковою");
                assertEquals(button.getWidth(), button.getHeight(), 0.1,
                        "Кнопки меню мають бути квадратними");
                assertNotNull(button.getBorder(), "Кнопка має мати рамку");
                assertEquals(Color.web("#454b55"), button.getBorder().getStrokes().getFirst().getTopStroke());
                assertEquals(Insets.EMPTY, button.getBorder().getStrokes().getFirst().getInsets());
                Background background = button.getBackground();
                assertNotNull(background, "Кнопка має мати фонову заливку");
                assertEquals(new Insets(1.0), background.getFills().getFirst().getInsets(),
                        "Заливка повинна бути відступлена всередину рамки на 1 px");
            }
        });
    }

    /** Створює набір дій-заглушок, потрібний для перевірки лише візуальної поведінки меню. */
    private static NeuronContextMenuView.Actions actions() {
        return new NeuronContextMenuView.Actions(
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { }
        );
    }
}
