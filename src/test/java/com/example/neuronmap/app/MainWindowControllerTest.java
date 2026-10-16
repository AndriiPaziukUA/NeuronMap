package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.window.WindowStateStore;
import com.example.neuronmap.view.window.WindowChromeView;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє контракт контролера головного вікна без залежностей від платформних адаптерів. */
final class MainWindowControllerTest {

    @TempDir
    private Path temporaryDirectory;

    /** Запускає JavaFX один раз, залишаючи toolkit доступним для тестових Stage. */
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

    /** Перевіряє публічний контракт конфігурації вікна. */
    @Test
    void configuresWindowFromValidatedSettings() {
        var configure = Arrays.stream(MainWindowController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("configure"))
                .findFirst()
                .orElseThrow();
        assertArrayEquals(
                new Class<?>[]{AppConfig.Window.class, Scene.class, Runnable.class},
                configure.getParameterTypes()
        );
    }

    /** Переконується, що контролер делегує оформлення одному lifecycle-компоненту. */
    @Test
    void delegatesChromeOwnershipToLifecycleComponent() {
        assertFalse(Arrays.stream(MainWindowController.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type == WindowChromeView.class));
        assertTrue(Arrays.stream(MainWindowController.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == WindowChromeLifecycle.class));
    }

    /** Перевіряє очищення оформлення навіть у разі помилки під час завершення редактора. */
    @Test
    void disposesWindowChromeWhenEditorShutdownFails() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            MainWindowController controller = createController(stage);
            controller.configure(windowConfig(), new Scene(new StackPane(), 640.0, 480.0), () -> {
                throw new IllegalStateException("editor shutdown failed");
            });

            assertThrows(IllegalStateException.class, controller::close);
            assertNull(lifecycleFieldValue(controller, "windowChromeView"));
        });
    }

    /** Перевіряє, що стандартна команда закриття одноразово запускає завершення редактора. */
    @Test
    void closesEditorOnlyOnce() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            AtomicInteger shutdownCalls = new AtomicInteger();
            MainWindowController controller = createController(stage);
            controller.configure(windowConfig(), new Scene(new StackPane(), 640.0, 480.0), shutdownCalls::incrementAndGet);

            Event.fireEvent(stage, new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
            controller.close();
            controller.shutdown();

            assertEquals(1, shutdownCalls.get());
            assertNull(lifecycleFieldValue(controller, "windowChromeView"));
        });
    }

    /** Створює тестовий контролер без показу реального вікна. */
    private MainWindowController createController(Stage stage) {
        return new MainWindowController(
                stage,
                new WindowStateStore(temporaryDirectory.resolve("window-state.properties")),
                new LocalizationService(Locale.forLanguageTag("uk")),
                () -> { },
                () -> true
        );
    }

    /** Повертає узгоджені налаштування тестового вікна. */
    private static AppConfig.Window windowConfig() {
        return new AppConfig.Window("NeuronMap test", 640.0, 480.0, "window-state.properties");
    }

    /** Зчитує внутрішній ресурс життєвого циклу для перевірки очищення. */
    private static Object lifecycleFieldValue(MainWindowController controller, String fieldName) {
        try {
            Field lifecycleField = MainWindowController.class.getDeclaredField("windowChromeLifecycle");
            lifecycleField.setAccessible(true);
            Object lifecycle = lifecycleField.get(controller);
            Field resourceField = WindowChromeLifecycle.class.getDeclaredField(fieldName);
            resourceField.setAccessible(true);
            return resourceField.get(lifecycle);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Не вдалося прочитати ресурс оформлення вікна " + fieldName, exception);
        }
    }

    /** Виконує дію на JavaFX Application Thread і передає помилки тестовому потоку. */
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
