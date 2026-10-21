package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.menu.MainMenuView;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що меню не відкривається після Esc, який уже обробив активний режим редактора. */
final class MainMenuControllerConsumedEscapeTest {

    /** Запускає JavaFX toolkit перед тестуванням маршрутизації клавіатурних подій. */
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

    /** Перевіряє, що Esc у режимі додавання лише скасовує режим, а наступне Esc відкриває меню як раніше. */
    @Test
    void consumedEscapeCancelsPlacementWithoutOpeningMenuAndNextEscapeOpensIt() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = new MainMenuController(
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
            Scene scene = new Scene(view, 900.0, 600.0);
            AtomicBoolean placementModeActive = new AtomicBoolean(true);

            // Імітує фільтр редактора, зареєстрований раніше за фільтр головного меню.
            scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE && placementModeActive.get()) {
                    placementModeActive.set(false);
                    event.consume();
                }
            });
            controller.install(scene);

            fireEscape(scene);

            assertFalse(placementModeActive.get(), "Перше Esc має вимкнути режим розміщення");
            assertFalse(controller.isOpen(), "Спожите Esc не має відкривати головне меню");

            fireEscape(scene);

            assertTrue(controller.isOpen(), "Наступне Esc має знову виконувати стандартну дію меню");
            controller.dispose();
        });
    }

    /** Надсилає клавіатурну подію Esc через фільтри сцени у звичайному порядку JavaFX. */
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

    /** Виконує тестову дію у JavaFX Application Thread і передає помилки до потоку JUnit. */
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
            throw new AssertionError("Перевірка спожитого Esc завершилася помилкою", failure[0]);
        }
    }
}
