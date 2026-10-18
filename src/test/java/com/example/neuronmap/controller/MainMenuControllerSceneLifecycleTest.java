package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.menu.MainMenuView;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє перенесення клавіатурного фільтра меню між сценами та його очищення. */
final class MainMenuControllerSceneLifecycleTest {

    /** Перевіряє, що стара сцена більше не обробляє клавіші після встановлення меню на нову сцену. */
    @Test
    void installMovesHandlerFromPreviousSceneAndDisposeRemovesIt() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            AtomicInteger pauseCalls = new AtomicInteger();
            MainMenuController controller = new MainMenuController(
                    view, localization, List::of, () -> { }, ignored -> { }, (project, name) -> project,
                    ignored -> { }, () -> { }, pauseCalls::incrementAndGet, () -> { });
            Scene firstScene = new Scene(new StackPane(), 400.0, 300.0);
            Scene secondScene = new Scene(new StackPane(), 400.0, 300.0);

            controller.install(firstScene);
            controller.install(secondScene);
            controller.install(secondScene);
            fireEscape(firstScene);
            assertFalse(controller.isOpen());
            assertEquals(0, pauseCalls.get());

            fireEscape(secondScene);
            assertTrue(controller.isOpen());
            assertEquals(1, pauseCalls.get());
            controller.dispose();

            fireEscape(secondScene);
            assertFalse(controller.isOpen());
            fireEscape(firstScene);
            assertFalse(controller.isOpen());
        });
    }

    /** Надсилає Escape безпосередньо сцені, щоб перевірити її зареєстровані фільтри подій. */
    private static void fireEscape(Scene scene) {
        Event.fireEvent(scene, new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                false, false, false, false));
    }
}
