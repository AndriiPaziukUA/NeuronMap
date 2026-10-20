package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє створення кнопок контекстного меню, векторний значок напрямку та використання CSS-класів. */
final class NeuronMenuViewTest {

    /** Переконується, що кнопки спеціальних дій оформлюються CSS-класами без inline-стилів та обробників наведення.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void customizeMenuButtonUsesCssClassesWithoutInlineStyles() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMenuView view = new NeuronMenuView(
                    () -> null,
                    null,
                    new LocalizationService(Locale.forLanguageTag("uk"))
            );
            Button activation = new Button();
            activation.getStyleClass().add("neuron-menu-activate");
            Button deletion = new Button();
            deletion.getStyleClass().add("neuron-menu-delete");

            view.customizeMenuButton(activation);
            view.customizeMenuButton(deletion);
            view.customizeMenuButton(null);

            assertTrue(activation.getStyleClass().contains("neuron-menu-activate"));
            assertTrue(deletion.getStyleClass().contains("neuron-menu-delete"));
            assertTrue(activation.getStyle().isBlank());
            assertTrue(deletion.getStyle().isBlank());
            assertNull(activation.getOnMouseEntered());
            assertNull(activation.getOnMouseExited());
            assertNull(deletion.getOnMouseEntered());
            assertNull(deletion.getOnMouseExited());
            assertNotNull(activation.getTooltip());
            assertEquals("Імпульс", activation.getTooltip().getText());
        });
    }

    /** Перевіряє, що кнопка напрямку має окремий векторний значок і передає ідентифікатор через callback.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void directionButtonUsesVectorIconAndDelegatesSelectedNeuronIdToCallback() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            AtomicReference<String> handledId = new AtomicReference<>();
            NeuronMenuView view = new NeuronMenuView(
                    () -> "neuron-42",
                    handledId::set,
                    new LocalizationService(Locale.forLanguageTag("uk"))
            );
            Button direction = view.createDirectionButton();
            assertNotNull(direction);
            assertTrue(direction.getStyleClass().contains("menu-button"));
            assertTrue(direction.getStyleClass().contains("neuron-menu-direction"));
            assertTrue(direction.getStyle().isBlank());
            assertTrue(direction.getText().isBlank(), "Значок напрямку не повинен бути текстовим символом");
            assertNotNull(direction.getTooltip());
            assertEquals("Змінити напрямок", direction.getTooltip().getText());
            Pane icon = assertInstanceOf(Pane.class, direction.getGraphic());
            assertTrue(icon.getStyleClass().contains("neuron-menu-icon-direction"));
            Node shape = icon.getChildren().getFirst();
            assertTrue(shape.getStyleClass().contains("neuron-menu-icon-stroke"));

            direction.fire();
            assertEquals("neuron-42", handledId.get());
        });
    }

    /** Перевіряє, що кнопка напрямку не створюється, якщо дію для неї не задано.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void directionButtonIsOmittedWhenNoActionIsProvided() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMenuView view = new NeuronMenuView(
                    () -> null,
                    null,
                    new LocalizationService(Locale.forLanguageTag("uk"))
            );
            assertNull(view.createDirectionButton());
        });
    }

    /** Переконується, що локалізація залишається обов'язковою залежністю подання меню. */
    @Test
    void rejectsMissingLocalizationService() {
        assertThrows(NullPointerException.class, () -> new NeuronMenuView(() -> null, null, null));
    }
}
