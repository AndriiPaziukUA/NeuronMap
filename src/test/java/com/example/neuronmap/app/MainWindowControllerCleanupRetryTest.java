package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.window.WindowStateStore;
import com.example.neuronmap.window.WindowChrome;
import com.example.neuronmap.windowing.WindowPlatformAdapter;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Перевіряє повторну спробу очищення, коли платформний адаптер не може звільнити всі ресурси відразу. */
final class MainWindowControllerCleanupRetryTest {

    @TempDir
    private Path temporaryDirectory;

    /** Запускає JavaFX toolkit для перевірки Stage на відповідному потоці. */
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

    /** Переконується, що адаптер залишається доступним для очищення до підтвердження завершення. */
    @Test
    void retainsAdapterUntilCleanupRetryCompletes() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            RetryableWindowPlatformAdapter adapter = new RetryableWindowPlatformAdapter();
            MainWindowController controller = new MainWindowController(
                    stage,
                    new WindowStateStore(temporaryDirectory.resolve("window-state.properties")),
                    new LocalizationService(Locale.forLanguageTag("uk")),
                    () -> Optional.of(adapter),
                    () -> { }
            );
            controller.configure(
                    new AppConfig.Window("NeuronMap test", 640.0, 480.0, "window-state.properties"),
                    new Scene(new StackPane(), 640.0, 480.0),
                    () -> { }
            );
            controller.show();

            controller.shutdown();

            assertEquals(1, adapter.disposeCalls.get());
            assertSame(adapter, lifecycleFieldValue(controller, "activeAdapter"),
                    "Компонент має зберігати адаптер, доки очищення не підтверджено");

            controller.shutdown();

            assertEquals(2, adapter.disposeCalls.get());
            assertNull(lifecycleFieldValue(controller, "activeAdapter"),
                    "Після успішної повторної спроби посилання на адаптер має бути очищене");
            assertNull(lifecycleFieldValue(controller, "windowChromeView"));
            stage.close();
        });
    }

    /** Зчитує приватний ресурс компонента оформлення для перевірки збереження адаптера між спробами. */
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

    /** Виконує тестову дію на JavaFX Application Thread і передає помилку тестовому потоку. */
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

    /** Адаптер моделює очищення, що завершується лише під час другої спроби. */
    private static final class RetryableWindowPlatformAdapter implements WindowPlatformAdapter {

        private final AtomicInteger disposeCalls = new AtomicInteger();

        /** У тесті нативне встановлення не потребує системного вікна. */
        @Override
        public void install(Stage stage, WindowChrome chrome) {
        }

        /** Не виконує окремого очищення, оскільки його результат визначає метод із підтвердженням завершення. */
        @Override
        public void dispose() {
            disposeCalls.incrementAndGet();
        }

        /** Повертає незавершений стан після першої спроби та успіх після другої. */
        @Override
        public boolean disposeAndReportCompletion() {
            return disposeCalls.incrementAndGet() > 1;
        }
    }
}
