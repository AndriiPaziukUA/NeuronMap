package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє створення та оформлення елементів контекстного меню нейрона у view-шарі.
 */
final class NeuronMenuViewTest {

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

    @Test
    void rejectsNullToolbarRoot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NeuronMenuView(null)
        );
    }

    @Test
    void customizeMenuButtonAppliesActivationAndDeleteStyles() throws Exception {
        runOnFxThread(() -> {
            NeuronMenuView view = new NeuronMenuView(
                    new VBox(),
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

            assertTrue(activation.getStyle().contains("#f5c542"));
            assertTrue(activation.getStyle().contains("black"));
            assertNotNull(activation.getTooltip());
            assertTrue(deletion.getStyle().contains("#d64545"));
            assertTrue(deletion.getStyle().contains("white"));
        });
    }

    @Test
    void directionButtonDelegatesSelectedNeuronIdToCallback() throws Exception {
        runOnFxThread(() -> {
            AtomicReference<String> handledId = new AtomicReference<>();
            NeuronMenuView view = new NeuronMenuView(
                    new VBox(),
                    () -> "neuron-42",
                    handledId::set,
                    new LocalizationService(Locale.forLanguageTag("uk"))
            );

            Button direction = view.createDirectionButton();
            assertNotNull(direction);
            assertTrue(direction.getStyleClass().contains("neuron-menu-direction"));
            assertNotNull(direction.getTooltip());
            direction.fire();

            assertEquals("neuron-42", handledId.get());
        });
    }

    @Test
    void directionButtonIsOmittedWhenNoActionIsProvided() throws Exception {
        runOnFxThread(() -> {
            NeuronMenuView view = new NeuronMenuView(new VBox());
            assertNull(view.createDirectionButton());
        });
    }

    @Test
    void removesExitButtonFromNestedToolbarPane() throws Exception {
        runOnFxThread(() -> {
            Button exit = new Button("Exit");
            exit.getStyleClass().add("delete-connection-exit-button");
            HBox toolbarRow = new HBox(exit);
            VBox toolbar = new VBox(toolbarRow);
            new Scene(toolbar, 300.0, 100.0);
            toolbar.applyCss();

            NeuronMenuView view = new NeuronMenuView(toolbar);
            view.removeDeleteConnectionModeExitButton();

            assertFalse(toolbarRow.getChildren().contains(exit));
        });
    }

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
