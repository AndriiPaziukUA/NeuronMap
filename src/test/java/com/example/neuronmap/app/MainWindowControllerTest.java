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
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє архітектурний контракт контролера головного вікна та одноразовий життєвий цикл платформного адаптера.
 */
final class MainWindowControllerTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Запускає JavaFX і зберігає toolkit активним після закриття тестових вікон.
     * Це дозволяє всім тестам у межах процесу повторно використовувати один JavaFX Application Thread.
     *
     * @throws InterruptedException якщо очікування запуску JavaFX було перервано.
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

    /** Перевіряє, що контролер отримує перевірену конфігурацію вікна як єдиний параметр налаштувань. */
    @Test
    void configuresWindowFromValidatedWindowSettings() {
        Method configure = Arrays.stream(MainWindowController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("configure"))
                .findFirst()
                .orElse(null);

        assertNotNull(configure);
        assertArrayEquals(
                new Class<?>[]{AppConfig.Window.class, Scene.class, Runnable.class},
                configure.getParameterTypes()
        );
    }

    /** Перевіряє, що контролер не залежить безпосередньо від фасаду редактора й приймає лише дію його завершення. */
    @Test
    void dependsOnShutdownActionInsteadOfEditorFacade() {
        assertFalse(Arrays.stream(MainWindowController.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type == NeuronMapEditor.class));
    }

    /**
     * Переконується, що повторний виклик show() заборонений і не встановлює платформний адаптер вдруге.
     *
     * @throws Exception якщо тест не може виконати перевірку на JavaFX Application Thread.
     */
    @Test
    void rejectsRepeatedShowWithoutReinstallingAdapter() throws Exception {
        runOnFxThread(() -> {
            Stage stage = createTestStage();
            RecordingWindowPlatformAdapter adapter = new RecordingWindowPlatformAdapter(null);
            AtomicInteger showCalls = new AtomicInteger();
            MainWindowController controller = createController(stage, adapter, showCalls::incrementAndGet);
            controller.configure(windowConfig(), new Scene(new StackPane(), 640.0, 480.0), () -> { });

            controller.show();

            assertEquals(1, showCalls.get());
            assertFalse(stage.isShowing(), "Тест не повинен показувати нативне вікно");
            assertThrows(IllegalStateException.class, controller::show);
            assertEquals(1, showCalls.get());
            assertEquals(1, adapter.installCalls.get());

            controller.close();
            assertEquals(1, adapter.disposeCalls.get());
        });
    }

    /**
     * Перевіряє збереження JavaFX-адаптера як активного після помилки встановлення нативної реалізації.
     *
     * @throws Exception якщо тест не може виконати перевірку на JavaFX Application Thread.
     */
    @Test
    void keepsFallbackAdapterAsActiveAfterNativeInstallationFails() throws Exception {
        runOnFxThread(() -> {
            Stage stage = createTestStage();
            RecordingWindowPlatformAdapter failedAdapter = new RecordingWindowPlatformAdapter(
                    new IllegalStateException("native installation failed"));
            MainWindowController controller = createController(stage, failedAdapter);
            controller.configure(windowConfig(), new Scene(new StackPane(), 640.0, 480.0), () -> { });

            controller.show();

            assertEquals(1, failedAdapter.installCalls.get());
            assertEquals(1, failedAdapter.disposeCalls.get());
            assertInstanceOf(JavaFxWindowPlatformAdapter.class, fieldValue(controller, "windowPlatformAdapter"));

            controller.close();
            assertNull(fieldValue(controller, "windowPlatformAdapter"));
        });
    }

    /**
     * Перевіряє, що закриття звільняє активний адаптер і подання та прибирає їхні посилання з контролера.
     *
     * @throws Exception якщо тест не може виконати перевірку на JavaFX Application Thread.
     */
    @Test
    void closingWindowDisposesActiveAdapterAndClearsOwnedReferences() throws Exception {
        runOnFxThread(() -> {
            Stage stage = createTestStage();
            RecordingWindowPlatformAdapter adapter = new RecordingWindowPlatformAdapter(null);
            AtomicInteger shutdownCalls = new AtomicInteger();
            MainWindowController controller = createController(stage, adapter);
            controller.configure(windowConfig(), new Scene(new StackPane(), 640.0, 480.0), shutdownCalls::incrementAndGet);
            controller.show();

            controller.close();

            assertEquals(1, shutdownCalls.get());
            assertEquals(1, adapter.disposeCalls.get());
            assertNull(fieldValue(controller, "windowPlatformAdapter"));
            assertNull(fieldValue(controller, "windowChromeView"));
        });
    }

    /**
     * Перевіряє, що помилка завершення редактора не перешкоджає очищенню адаптера та подання вікна.
     *
     * @throws Exception якщо тест не може виконати перевірку на JavaFX Application Thread.
     */
    @Test
    void cleanupRunsWhenEditorShutdownFails() throws Exception {
        runOnFxThread(() -> {
            Stage stage = createTestStage();
            RecordingWindowPlatformAdapter adapter = new RecordingWindowPlatformAdapter(null);
            MainWindowController controller = createController(stage, adapter);
            controller.configure(windowConfig(), new Scene(new StackPane(), 640.0, 480.0), () -> {
                throw new IllegalStateException("editor shutdown failed");
            });
            controller.show();

            assertThrows(IllegalStateException.class, controller::close);

            assertEquals(1, adapter.disposeCalls.get());
            assertNull(fieldValue(controller, "windowPlatformAdapter"));
            assertNull(fieldValue(controller, "windowChromeView"));
        });
    }

    /**
     * Створює JavaFX Stage для перевірки конфігурації без створення видимого вікна операційної системи.
     * Переданий контролеру тестовий callback не викликає {@code Stage.show()}.
     *
     * @return створений, але не показаний Stage.
     */
    private static Stage createTestStage() {
        return new Stage();
    }

    /** Створює контролер із тестовим адаптером і без показу нативного вікна. */
    private MainWindowController createController(Stage stage, RecordingWindowPlatformAdapter adapter) {
        return createController(stage, adapter, () -> { });
    }

    /**
     * Створює контролер із заданою тестовою дією показу, не змінюючи публічного контракту застосунку.
     *
     * @param stage JavaFX-вікно, яке залишається непоказаним.
     * @param adapter адаптер, поведінку встановлення й очищення якого перевіряє тест.
     * @param showWindowAction тестова дія замість створення нативного вікна.
     * @return налаштований контролер.
     */
    private MainWindowController createController(
            Stage stage,
            RecordingWindowPlatformAdapter adapter,
            Runnable showWindowAction
    ) {
        return new MainWindowController(
                stage,
                new WindowStateStore(temporaryDirectory.resolve("window-state.properties")),
                new LocalizationService(Locale.forLanguageTag("uk")),
                () -> Optional.of(adapter),
                showWindowAction
        );
    }

    /** Повертає валідні налаштування вікна, які використовують ізольований файл стану тесту. */
    private static AppConfig.Window windowConfig() {
        return new AppConfig.Window("NeuronMap test", 640.0, 480.0, "window-state.properties");
    }

    /** Зчитує приватне поле контролера для перевірки фактичного власника активного ресурсу. */
    private static Object fieldValue(MainWindowController controller, String fieldName) {
        try {
            Field field = MainWindowController.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(controller);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Не вдалося прочитати поле контролера " + fieldName, exception);
        }
    }

    /** Виконує перевірку на JavaFX Application Thread і передає помилку назад у тестовий потік. */
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

    /** Тестова реалізація адаптера, що фіксує встановлення та очищення й за потреби імітує помилку підключення. */
    private static final class RecordingWindowPlatformAdapter implements WindowPlatformAdapter {

        private final RuntimeException installationFailure;
        private final AtomicInteger installCalls = new AtomicInteger();
        private final AtomicInteger disposeCalls = new AtomicInteger();

        /** Створює адаптер, який або успішно встановлюється, або повертає задану помилку. */
        private RecordingWindowPlatformAdapter(RuntimeException installationFailure) {
            this.installationFailure = installationFailure;
        }

        /** Рахує спроби встановлення та імітує невдале підключення, якщо для цього задано помилку. */
        @Override
        public void install(Stage stage, WindowChrome chrome) {
            installCalls.incrementAndGet();
            if (installationFailure != null) {
                throw installationFailure;
            }
        }

        /** Рахує очищення, щоб перевірити, що контролер звільняє ресурс рівно один раз. */
        @Override
        public void dispose() {
            disposeCalls.incrementAndGet();
        }
    }
}
