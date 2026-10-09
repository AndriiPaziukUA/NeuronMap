package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.MainMenuView;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression tests for Escape behavior on the main menu, its subpages, and unsaved-change confirmation. */
final class MainMenuControllerTest {

    /** Starts the JavaFX toolkit for keyboard-event tests. */
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

    /** Verifies that Escape backs out of saved-projects and settings, then closes the main menu. */
    @Test
    void escapeNavigatesBackFromSubpagesAndClosesMainPage() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = controllerFor(view, localization);
            Scene scene = new Scene(view, 900.0, 600.0);
            controller.install(scene);
            controller.open();

            view.showLoadPage(List.of());
            fireEscape(scene);
            assertTrue(controller.isOpen());
            assertFalse(view.isLoadPage());
            assertFalse(view.isSettingsPage());

            view.showSettingsPage();
            fireEscape(scene);
            assertTrue(controller.isOpen());
            assertFalse(view.isSettingsPage());

            fireEscape(scene);
            assertFalse(controller.isOpen());
            controller.dispose(scene);
        });
    }

    /** Verifies that Escape first opens the discard prompt and then completes the requested Back action. */
    @Test
    void escapeDiscardsDirtySettingsAndContinuesBackNavigation() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = controllerFor(view, localization);
            Scene scene = new Scene(view, 900.0, 600.0);
            controller.install(scene);
            controller.open();
            view.showSettingsPage();
            view.languageSelectorForTest().setValue(com.example.neuronmap.i18n.SupportedLanguage.UKRAINIAN);
            view.languageSelectorForTest().getOnAction().handle(
                    new javafx.event.ActionEvent(view.languageSelectorForTest(), null));

            assertTrue(view.hasUnsavedChanges());
            fireEscape(scene);
            assertTrue(view.isConfirmingUnsavedChanges());

            fireEscape(scene);
            assertFalse(view.isConfirmingUnsavedChanges());
            assertTrue(controller.isOpen());
            assertFalse(view.isSettingsPage());
            assertEquals(com.example.neuronmap.i18n.SupportedLanguage.ENGLISH, localization.language());
            assertFalse(view.hasUnsavedChanges());

            fireEscape(scene);
            assertFalse(controller.isOpen());
            controller.dispose(scene);
        });
    }

    /** Creates a controller with inert project/application callbacks for isolated keyboard tests. */
    private static MainMenuController controllerFor(
            MainMenuView view,
            LocalizationService localization
    ) {
        return new MainMenuController(
                view,
                localization,
                () -> List.of(),
                () -> { },
                ignored -> { },
                (project, name) -> project,
                ignored -> { },
                () -> { },
                () -> { },
                () -> { }
        );
    }

    /** Dispatches an Escape key-pressed event through the scene's event filters. */
    private static void fireEscape(Scene scene) {
        Event.fireEvent(scene, new KeyEvent(
                KeyEvent.KEY_PRESSED,
                "",
                "",
                KeyCode.ESCAPE,
                false,
                false,
                false,
                false
        ));
    }

    /** Runs an assertion block on the JavaFX thread and propagates failures to the test thread. */
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
}
