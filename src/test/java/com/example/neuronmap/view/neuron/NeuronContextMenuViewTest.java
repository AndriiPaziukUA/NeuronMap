package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє структуру контекстного меню нейрона та передавання натискань до дій контролера.
 */
final class NeuronContextMenuViewTest {

    /**
     * Запускає JavaFX toolkit перед виконанням тестів подання.
     *
     * @throws Exception якщо JavaFX toolkit не запускається в межах установленого часу.
     */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Перевіряє порядок кнопок, їхні CSS-класи та відсутність дії видалення зв’язку, коли зв’язків немає.
     */
    @Test
    void createsMenuWithoutConnectionDeletionAction() throws Exception {
        runOnFxThread(() -> {
            AtomicInteger customizedButtons = new AtomicInteger();
            NeuronContextMenuView view = new NeuronContextMenuView(
                    new LocalizationService(Locale.forLanguageTag("uk")),
                    button -> customizedButtons.incrementAndGet(),
                    () -> null
            );

            VBox menu = view.createMenu(false, actions());
            assertTrue(menu.getStyleClass().contains("connection-menu"));
            assertEquals(1, menu.getChildren().size());
            HBox buttons = (HBox) menu.getChildren().getFirst();
            assertEquals(5, buttons.getChildren().size());
            assertEquals(
                    List.of("⚡", "↗", "⚙", "⇄", "✕"),
                    buttons.getChildren().stream()
                            .map(node -> ((Button) node).getText())
                            .toList()
            );
            assertTrue(((Button) buttons.getChildren().getFirst())
                    .getStyleClass().contains("neuron-menu-activate"));
            assertTrue(((Button) buttons.getChildren().getLast())
                    .getStyleClass().contains("neuron-menu-delete"));
            assertEquals(5, customizedButtons.get());
        });
    }

    /**
     * Перевіряє додавання кнопок видалення зв’язку та зміни напрямку за наявності відповідних умов.
     */
    @Test
    void includesOptionalActionsWhenAvailable() throws Exception {
        runOnFxThread(() -> {
            Button directionButton = new Button("↔");
            NeuronContextMenuView view = new NeuronContextMenuView(
                    new LocalizationService(Locale.forLanguageTag("uk")),
                    button -> { },
                    () -> directionButton
            );

            HBox buttons = (HBox) view.createMenu(true, actions()).getChildren().getFirst();

            assertEquals(7, buttons.getChildren().size());
            assertEquals(
                    List.of("⚡", "↗", "⚙", "⛓", "↔", "⇄", "✕"),
                    buttons.getChildren().stream()
                            .map(node -> ((Button) node).getText())
                            .toList()
            );
        });
    }

    /**
     * Перевіряє, що всі кнопки передають керування відповідним діям контролера.
     */
    @Test
    void delegatesButtonActionsToCallbacks() throws Exception {
        runOnFxThread(() -> {
            List<String> actionsCalled = new ArrayList<>();
            NeuronContextMenuView view = new NeuronContextMenuView(
                    new LocalizationService(Locale.forLanguageTag("uk")),
                    button -> { },
                    () -> null
            );

            NeuronContextMenuView.Actions actions = new NeuronContextMenuView.Actions(
                    () -> actionsCalled.add("fire"),
                    () -> actionsCalled.add("add"),
                    () -> actionsCalled.add("settings"),
                    () -> actionsCalled.add("delete-connection"),
                    () -> actionsCalled.add("toggle-type"),
                    () -> actionsCalled.add("delete-neuron")
            );
            HBox buttons = (HBox) view.createMenu(true, actions).getChildren().getFirst();
            buttons.getChildren().forEach(node -> ((Button) node).fire());

            assertEquals(
                    List.of("fire", "add", "settings", "delete-connection", "toggle-type", "delete-neuron"),
                    actionsCalled
            );
        });
    }

    /**
     * Перевіряє, що інтерфейс дій відхиляє відсутні callback-и.
     */
    @Test
    void actionsRequireAllCallbacks() throws Exception {
        runOnFxThread(() -> {
            org.junit.jupiter.api.Assertions.assertThrows(
                    NullPointerException.class,
                    () -> new NeuronContextMenuView.Actions(
                            () -> { }, () -> { }, () -> { }, null, () -> { }, () -> { }
                    )
            );
        });
    }

    /**
     * Виконує перевірку в потоці JavaFX і повертає виняток у потік тестів.
     *
     * @param action дія, яку потрібно виконати в потоці JavaFX.
     * @throws Exception якщо дія не завершується в межах установленого часу.
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
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }

    /**
     * Створює набір тестових дій, які не змінюють зовнішній стан.
     *
     * @return заповнений набір callback-ів меню.
     */
    private static NeuronContextMenuView.Actions actions() {
        return new NeuronContextMenuView.Actions(
                () -> { },
                () -> { },
                () -> { },
                () -> { },
                () -> { },
                () -> { }
        );
    }
}
