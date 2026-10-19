package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.application.Platform;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє створення кнопок контекстного меню та використання незалежних CSS-класів. */
final class NeuronMenuViewTest {

    /** Запускає JavaFX toolkit перед перевірками графічних компонентів меню нейрона. */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /** Переконується, що спеціальні кнопки отримують CSS-класи замість inline-стилів і обробників наведення. */
    @Test
    void customizeMenuButtonUsesCssClassesWithoutInlineStyles() throws Exception {
        runOnFxThread(() -> {
            NeuronMenuView view = new NeuronMenuView(
                    () -> null,
                    null,
                    new LocalizationService(Locale.forLanguageTag("uk"))
            );
            Button activation = new Button("⚡");
            activation.getStyleClass().add("neuron-menu-activate");
            Button deletion = new Button("✕");
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

    /** Перевіряє, що кнопка напрямку використовує спільний клас меню та передає ідентифікатор через callback. */
    @Test
    void directionButtonDelegatesSelectedNeuronIdToCallback() throws Exception {
        runOnFxThread(() -> {
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
            assertNotNull(direction.getTooltip());
            direction.fire();
            assertEquals("neuron-42", handledId.get());
        });
    }

    /** Перевіряє, що кнопка напрямку не створюється, якщо дію для неї не задано. */
    @Test
    void directionButtonIsOmittedWhenNoActionIsProvided() throws Exception {
        runOnFxThread(() -> {
            NeuronMenuView view = new NeuronMenuView(
                    () -> null,
                    null,
                    new LocalizationService(Locale.forLanguageTag("uk"))
            );
            assertNull(view.createDirectionButton());
        });
    }

    /** Перевіряє, що локалізація є обов'язковою залежністю, а не приводом створити окремий сервіс. */
    @Test
    void rejectsMissingLocalizationService() {
        assertThrows(NullPointerException.class, () -> new NeuronMenuView(() -> null, null, null));
    }

    /** Виконує дію в потоці JavaFX і передає помилки до потоку JUnit. */
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
