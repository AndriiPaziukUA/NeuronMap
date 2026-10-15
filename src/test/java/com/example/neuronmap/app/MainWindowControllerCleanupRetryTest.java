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

/**
 * Перевіряє, що контролер зберігає адаптер, коли очищення не завершилося, і повторює його під час наступного shutdown.
 */
final class MainWindowControllerCleanupRetryTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Запускає JavaFX toolkit для перевірки життєвого циклу Stage на правильному потоці.
     *
     * @throws InterruptedException якщо очікування запуску JavaFX перервано.
     */
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

    /**
     * Перевіряє, що незавершене очищення не призводить до втрати адаптера, а наступний виклик завершує його життєвий цикл.
     *
     * @throws Exception якщо виконання на JavaFX Application Thread не завершилося вчасно.
     */
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
            assertSame(adapter, fieldValue(controller, "windowPlatformAdapter"),
                    "Контролер має зберігати адаптер, доки очищення не підтверджено");

            controller.shutdown();

            assertEquals(2, adapter.disposeCalls.get());
            assertNull(fieldValue(controller, "windowPlatformAdapter"),
                    "Посилання на адаптер треба прибрати після успішної повторної спроби");
            assertNull(fieldValue(controller, "windowChromeView"));
            stage.close();
        });
    }

    /**
     * Виконує перевірку на JavaFX Application Thread та переносить помилку до потоку JUnit.
     *
     * @param action перевірка життєвого циклу вікна.
     * @throws Exception якщо очікування завершилося тайм-аутом або перевірка не пройшла.
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

    /**
     * Зчитує приватне посилання контролера, щоб перевірити фактичне збереження власника ресурсу.
     *
     * @param controller контролер головного вікна.
     * @param fieldName назва поля, що перевіряється.
     * @return поточне значення поля.
     */
    private static Object fieldValue(MainWindowController controller, String fieldName) {
        try {
            Field field = MainWindowController.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(controller);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Не вдалося прочитати поле контролера " + fieldName, exception);
        }
    }

    /** Тестовий адаптер, який повертає незавершений результат очищення лише під час першої спроби. */
    private static final class RetryableWindowPlatformAdapter implements WindowPlatformAdapter {

        private final AtomicInteger disposeCalls = new AtomicInteger();

        /**
         * Приймає підключення без створення платформних ресурсів, потрібних лише для інтеграційного тесту.
         *
         * @param stage тестове JavaFX-вікно.
         * @param chrome контракт заголовка.
         */
        @Override
        public void install(Stage stage, WindowChrome chrome) {
            // У тесті перевіряється лише життєвий цикл очищення.
        }

        /** Збільшує лічильник викликів звичайного очищення. */
        @Override
        public void dispose() {
            disposeCalls.incrementAndGet();
        }

        /**
         * Імітує тимчасову помилку: перша спроба лишає ресурси активними, друга завершується успішно.
         *
         * @return {@code false} після першої спроби та {@code true} після наступної.
         */
        @Override
        public boolean disposeAndReportCompletion() {
            return disposeCalls.incrementAndGet() > 1;
        }
    }
}
