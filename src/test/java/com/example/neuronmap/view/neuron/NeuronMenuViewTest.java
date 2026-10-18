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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє створення, стилі та поведінку кнопок контекстного меню нейрона у view-шарі. */
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

    /**
     * Перевіряє приглушені початкові кольори та яскравіші кольори під час наведення для активації й видалення.
     *
     * @throws Exception якщо перевірку неможливо виконати в потоці JavaFX.
     */
    @Test
    void customizeMenuButtonAppliesMutedStylesAndBrightensColorsOnHover() throws Exception {
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

            assertTrue(activation.getStyle().contains("#c9a52d"));
            assertTrue(activation.getStyle().contains("black"));
            assertNotNull(activation.getTooltip());
            assertEquals("Імпульс", activation.getTooltip().getText());
            activation.getOnMouseEntered().handle(null);
            assertTrue(activation.getStyle().contains("#f5c542"));
            activation.getOnMouseExited().handle(null);
            assertTrue(activation.getStyle().contains("#c9a52d"));

            assertTrue(deletion.getStyle().contains("#b83b3b"));
            assertTrue(deletion.getStyle().contains("white"));
            deletion.getOnMouseEntered().handle(null);
            assertTrue(deletion.getStyle().contains("#d64545"));
            deletion.getOnMouseExited().handle(null);
            assertTrue(deletion.getStyle().contains("#b83b3b"));
        });
    }

    /**
     * Перевіряє, що кнопка напрямку використовує спільний стиль меню та передає ідентифікатор нейрона callback-у.
     *
     * @throws Exception якщо перевірку неможливо виконати в потоці JavaFX.
     */
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

    /**
     * Перевіряє, що кнопка напрямку не створюється, якщо дію для неї не задано.
     *
     * @throws Exception якщо перевірку неможливо виконати в потоці JavaFX.
     */
    @Test
    void directionButtonIsOmittedWhenNoActionIsProvided() throws Exception {
        runOnFxThread(() -> {
            NeuronMenuView view = new NeuronMenuView();
            assertNull(view.createDirectionButton());
        });
    }

    /**
     * Виконує перевірки в потоці JavaFX і передає помилки до потоку JUnit.
     *
     * @param action дія, яку потрібно виконати в потоці JavaFX.
     * @throws Exception якщо перевірка не завершується в межах установленого часу.
     */
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
