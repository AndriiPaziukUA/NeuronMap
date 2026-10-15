package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.window.WindowStateStore;
import com.example.neuronmap.window.WindowChrome;
import com.example.neuronmap.windowing.JavaFxWindowPlatformAdapter;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє повторне очищення активних і витіснених адаптерів оформлення вікна. */
final class MainWindowControllerCleanupRetryTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Запускає JavaFX toolkit для перевірки Stage на відповідному потоці.
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
     * Переконується, що активний адаптер зберігається до підтвердження завершення очищення.
     *
     * @throws Exception якщо виконання тесту на JavaFX-потоці перервано або не завершилося.
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

    /**
     * Перевіряє, що невдало встановлений адаптер лишається доступним для очищення після запуску fallback.
     * Дві перші спроби очищення повертають незавершений стан, а очищення під час shutdown завершується успішно.
     *
     * @throws Exception якщо виконання тесту на JavaFX-потоці перервано або не завершилося.
     */
    @Test
    void retriesFailedPrimaryAdapterAfterFallbackHasBeenInstalled() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            FailingInstallationRetryableAdapter primaryAdapter = new FailingInstallationRetryableAdapter();
            MainWindowController controller = new MainWindowController(
                    stage,
                    new WindowStateStore(temporaryDirectory.resolve("fallback-window-state.properties")),
                    new LocalizationService(Locale.forLanguageTag("uk")),
                    () -> Optional.of(primaryAdapter),
                    () -> { }
            );
            controller.configure(
                    new AppConfig.Window("NeuronMap fallback test", 640.0, 480.0, "window-state.properties"),
                    new Scene(new StackPane(), 640.0, 480.0),
                    () -> { }
            );

            controller.show();

            assertEquals(2, primaryAdapter.cleanupCalls.get(),
                    "Адаптер має пережити дві невдалі спроби очищення під час встановлення");
            assertInstanceOf(JavaFxWindowPlatformAdapter.class, lifecycleFieldValue(controller, "activeAdapter"),
                    "Після збою первинного адаптера має бути активований JavaFX fallback");
            assertTrue(pendingAdapters(controller).contains(primaryAdapter),
                    "Первинний адаптер має залишатися в реєстрі до завершення його очищення");

            controller.shutdown();

            assertEquals(3, primaryAdapter.cleanupCalls.get(),
                    "Завершення вікна має повторити очищення первинного адаптера");
            assertTrue(pendingAdapters(controller).isEmpty(),
                    "Після підтвердженого очищення адаптер має бути вилучений із реєстру очікування");
            assertNull(lifecycleFieldValue(controller, "activeAdapter"),
                    "Після завершення очищення fallback також має бути звільнений");
            stage.close();
        });
    }

    /**
     * Зчитує приватний ресурс компонента оформлення для перевірки збереження адаптера між спробами.
     *
     * @param controller контролер, життєвий цикл якого перевіряється.
     * @param fieldName назва поля компонента життєвого циклу.
     * @return значення відповідного поля.
     */
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

    /**
     * Повертає список адаптерів, які очікують на завершення очищення.
     *
     * @param controller контролер, життєвий цикл якого перевіряється.
     * @return список утримуваних адаптерів.
     */
    private static List<?> pendingAdapters(MainWindowController controller) {
        Object adapters = lifecycleFieldValue(controller, "adaptersPendingCleanup");
        if (!(adapters instanceof List<?> pendingAdapters)) {
            throw new AssertionError("Реєстр адаптерів, що очікують очищення, має бути списком");
        }
        return pendingAdapters;
    }

    /**
     * Виконує тестову дію на JavaFX Application Thread і передає помилку тестовому потоку.
     *
     * @param action дія, яку потрібно виконати.
     * @throws Exception якщо очікування завершення тестової дії перервано або дія завершилася помилкою.
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

    /** Адаптер моделює очищення, що завершується лише під час другої спроби. */
    private static final class RetryableWindowPlatformAdapter implements WindowPlatformAdapter {

        private final AtomicInteger disposeCalls = new AtomicInteger();

        /** У тесті нативне встановлення не потребує системного вікна. */
        @Override
        public void install(Stage stage, WindowChrome chrome) {
        }

        /** Не виконує окремого очищення, оскільки результат визначається методом із підтвердженням завершення. */
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

    /**
     * Адаптер для регресійного сценарію: встановлення й дві перші спроби очищення завершуються помилкою.
     */
    private static final class FailingInstallationRetryableAdapter implements WindowPlatformAdapter {

        private final AtomicInteger cleanupCalls = new AtomicInteger();

        /**
         * Імітує внутрішню спробу очищення адаптера та кидає помилку встановлення.
         *
         * @param stage тестове вікно.
         * @param chrome контракт заголовка, який у цьому тесті не використовується.
         */
        @Override
        public void install(Stage stage, WindowChrome chrome) {
            disposeAndReportCompletion();
            throw new IllegalStateException("simulated native installation failure");
        }

        /** Делегує результат очищення лічильнику тестового сценарію. */
        @Override
        public void dispose() {
            disposeAndReportCompletion();
        }

        /**
         * Імітує два невдалих очищення та успішне завершення під час наступної спроби.
         *
         * @return {@code true} починаючи з третьої спроби очищення.
         */
        @Override
        public boolean disposeAndReportCompletion() {
            return cleanupCalls.incrementAndGet() > 2;
        }
    }
}
