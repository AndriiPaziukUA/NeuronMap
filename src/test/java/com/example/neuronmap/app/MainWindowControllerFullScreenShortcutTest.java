package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.controller.FullScreenShortcutEvent;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.settings.GlobalSettingsFullScreenPreferenceStore;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.persistence.window.WindowStateStore;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє глобальну клавішу F11 та негайне збереження повноекранного налаштування. */
final class MainWindowControllerFullScreenShortcutTest {

    /** Запускає JavaFX toolkit для створення тестових сцен і вікон. */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /** Перевіряє обидва напрями перемикання, негайний запис і звільнення клавіатурного обробника. */
    @Test
    void f11TogglesAndPersistsFullScreenImmediately(@TempDir Path directory) throws Exception {
        runOnFxThread(() -> {
            Path settingsPath = directory.resolve("global.properties");
            GlobalSettingsFullScreenPreferenceStore preferences = new GlobalSettingsFullScreenPreferenceStore(
                    new GlobalSettingsStore(settingsPath));
            Stage stage = new Stage();
            StackPane root = new StackPane();
            Scene scene = new Scene(root, 640.0, 480.0);
            AtomicReference<Boolean> announcedFullScreenState = new AtomicReference<>();
            scene.addEventHandler(FullScreenShortcutEvent.FULL_SCREEN_CHANGED,
                    event -> announcedFullScreenState.set(event.isEnabled()));
            MainWindowController controller = new MainWindowController(
                    stage,
                    new WindowStateStore(directory.resolve("window-state.properties")),
                    new LocalizationService(Locale.ENGLISH),
                    () -> { },
                    () -> true,
                    preferences);
            controller.configure(
                    new AppConfig.Window("NeuronMap test", 640.0, 480.0, "window-state.properties"),
                    scene,
                    () -> { });

            try {
                assertFalse(controller.isFullScreen());
                assertFalse(preferences.loadFullScreenEnabled());

                fireKey(root, KeyCode.F11);
                assertTrue(controller.isFullScreen());
                assertTrue(stage.isFullScreen());
                assertTrue(Boolean.TRUE.equals(announcedFullScreenState.get()),
                        "Зміна через F11 має сповістити меню про новий збережений стан");
                assertTrue(new GlobalSettingsFullScreenPreferenceStore(
                        new GlobalSettingsStore(settingsPath)).loadFullScreenEnabled(),
                        "F11 має зберігати параметр відразу, не очікуючи підтвердження в меню");

                fireKey(root, KeyCode.F12);
                assertTrue(controller.isFullScreen(), "Інші клавіші не повинні перемикати режим");
                assertTrue(preferences.loadFullScreenEnabled());

                fireKey(root, KeyCode.F11);
                assertFalse(controller.isFullScreen());
                assertFalse(stage.isFullScreen());
                assertFalse(Boolean.TRUE.equals(announcedFullScreenState.get()),
                        "Вихід з повноекранного режиму має передати меню вимкнений стан");
                assertFalse(new GlobalSettingsFullScreenPreferenceStore(
                        new GlobalSettingsStore(settingsPath)).loadFullScreenEnabled());

                controller.shutdown();
                fireKey(root, KeyCode.F11);
                assertFalse(controller.isFullScreen(), "Обробник має бути знятий під час завершення вікна");
                assertFalse(preferences.loadFullScreenEnabled());
            } finally {
                controller.shutdown();
                stage.close();
            }
        });
    }

    /** Надсилає натискання клавіші у сцену, щоб перевірити реальний маршрут JavaFX-події. */
    private static void fireKey(javafx.scene.Node target, KeyCode code) {
        Event.fireEvent(target, new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    /** Виконує перевірку у JavaFX Application Thread і повертає помилки до JUnit-потоку. */
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
            throw new AssertionError("Помилка тесту клавіші F11", failure[0]);
        }
    }
}
