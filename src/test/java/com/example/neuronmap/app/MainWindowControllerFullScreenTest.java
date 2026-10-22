package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.settings.GlobalSettingsFullScreenPreferenceStore;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.persistence.window.WindowState;
import com.example.neuronmap.persistence.window.WindowStateStore;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє застосування повноекранного режиму контролером Stage без залежності від платформи оформлення. */
final class MainWindowControllerFullScreenTest {

    /** Запускає JavaFX toolkit для тестів життєвого циклу Stage. */
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

    /** Перевіряє, що попередній перегляд керує Stage, але не змінює глобальне налаштування до підтвердження. */
    @Test
    void previewAppliesModeAndPersistenceOccursOnlyAfterCommit(@TempDir Path directory) throws Exception {
        runOnFxThread(() -> {
            Path settingsPath = directory.resolve("global.properties");
            GlobalSettingsFullScreenPreferenceStore preferences = new GlobalSettingsFullScreenPreferenceStore(
                    new GlobalSettingsStore(settingsPath));
            Stage stage = new Stage();
            MainWindowController controller = new MainWindowController(
                    stage,
                    new WindowStateStore(directory.resolve("window-state.properties")),
                    new LocalizationService(Locale.ENGLISH),
                    () -> { },
                    () -> true,
                    preferences);

            controller.configure(
                    new AppConfig.Window("NeuronMap test", 640.0, 480.0, "window-state.properties"),
                    new Scene(new StackPane(), 640.0, 480.0),
                    () -> { });

            assertFalse(controller.isFullScreen());
            assertFalse(preferences.loadFullScreenEnabled());

            controller.previewFullScreen(true);
            assertTrue(controller.isFullScreen());
            assertTrue(stage.isFullScreen());
            assertFalse(preferences.loadFullScreenEnabled(),
                    "Попередній перегляд не повинен записувати глобальні налаштування");

            controller.persistFullScreen(true);
            assertTrue(new GlobalSettingsFullScreenPreferenceStore(
                    new GlobalSettingsStore(settingsPath)).loadFullScreenEnabled());

            controller.previewFullScreen(false);
            assertFalse(controller.isFullScreen());
            assertTrue(preferences.loadFullScreenEnabled(),
                    "Відкат попереднього перегляду не повинен переписувати підтверджене значення");

            controller.shutdown();
            stage.close();
        });
    }

    /** Перевіряє відновлення збереженого повноекранного режиму без втрати геометрії звичайного вікна. */
    @Test
    void restoresSavedFullScreenPreferenceAndPreservesWindowedGeometry(@TempDir Path directory) throws Exception {
        runOnFxThread(() -> {
            Path settingsPath = directory.resolve("global.properties");
            GlobalSettingsFullScreenPreferenceStore preferences = new GlobalSettingsFullScreenPreferenceStore(
                    new GlobalSettingsStore(settingsPath));
            preferences.saveFullScreenEnabled(true);

            Path windowStatePath = directory.resolve("window-state.properties");
            WindowStateStore windowStateStore = new WindowStateStore(windowStatePath);
            WindowState savedGeometry = new WindowState(720.0, 500.0, 45.0, 55.0);
            windowStateStore.save(savedGeometry);

            Stage stage = new Stage();
            MainWindowController controller = new MainWindowController(
                    stage,
                    windowStateStore,
                    new LocalizationService(Locale.ENGLISH),
                    () -> { },
                    () -> true,
                    preferences);
            controller.configure(
                    new AppConfig.Window("NeuronMap test", 640.0, 480.0, "window-state.properties"),
                    new Scene(new StackPane(), 640.0, 480.0),
                    () -> { });

            assertTrue(controller.isFullScreen());
            assertTrue(stage.isFullScreen());
            controller.shutdown();
            assertEquals(savedGeometry, windowStateStore.load().orElseThrow());
            stage.close();
        });
    }

    /** Виконує дію в JavaFX Application Thread і передає винятки потоку JUnit. */
    private static void runOnFxThread(Runnable action) throws InterruptedException {
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
