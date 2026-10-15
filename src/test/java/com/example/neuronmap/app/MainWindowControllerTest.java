package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.window.WindowStateStore;
import com.example.neuronmap.view.window.WindowChromeView;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє розподіл відповідальностей контролера головного вікна та делегований життєвий цикл оформлення. */
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

    /** Перевіряє, що метод конфігурації приймає лише перевірені налаштування, сцену й дію завершення редактора. */
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

    /** Переконується, що контролер не утримує адаптер або подання заголовка напряму. */
    @Test
    void delegatesChromeOwnershipToLifecycleComponent() {
        assertFalse(Arrays.stream(MainWindowController.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type == WindowChromeView.class || WindowPlatformAdapter.class.isAssignableFrom(type)));
        assertTrue(Arrays.stream(MainWindowController.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == WindowChromeLifecycle.class));
    }

    /** Перевіряє, що контролер не залежить від фасаду редактора та отримує лише його дію завершення. */
    @Test
    void dependsOnShutdownActionInsteadOfEditorFacade() {
        assertFalse(Arrays.stream(MainWindowController.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type == NeuronMapEditor.class));
    }

    /** Переконується, що публічний конструктор використовує спільну службу локалізації застосунку. */
    @Test
    void requiresSharedLocalizationServiceAtConstruction() {
        assertTrue(Arrays.stream(MainWindowController.class.getConstructors())
                .anyMatch(constructor -> Arrays.equals(
                        constructor.getParameterTypes(),
                        new Class<?>[]{Stage.class, WindowStateStore.class, LocalizationService.class}
                )));
    }

    /** Перевіряє одноразовий показ вікна та заборону повторного встановлення адаптера. */
    @Test
    void rejectsRepeatedShowWithoutReinstallingAdapter() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
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

    /** Перевіряє перехід до JavaFX fallback після збою основного адаптера. */
    @Test
    void keepsFallbackAdapterAsActiveAfterNativeInstallationFails() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            RecordingWindowPlatformAdapter failedAdapter = new RecordingWindowPlatformAdapter(
                    new IllegalStateException("native installation failed"));
            MainWindowController controller = createController(stage, failedAdapter);
            controller.configure(windowConfig(), new Scene(new StackPane(), 640.0, 480.0), () -> { });

            controller.show();

            assertEquals(1, failedAdapter.installCalls.get());
            assertEquals(1, failedAdapter.disposeCalls.get());
            assertInstanceOf(JavaFxWindowPlatformAdapter.class, lifecycleFieldValue(controller, "activeAdapter"));
            controller.close();
            assertNull(lifecycleFieldValue(controller, "activeAdapter"));
        });
    }

    /** Перевіряє, що завершення редактора зі збоєм не перешкоджає очищенню ресурсів оформлення. */
    @Test
    void cleanupRunsWhenEditorShutdownFails() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            RecordingWindowPlatformAdapter adapter = new RecordingWindowPlatformAdapter(null);
            MainWindowController controller = createController(stage, adapter);
            controller.configure(windowConfig(), new Scene(new StackPane(), 640.0, 480.0), () -> {
                throw new IllegalStateException("editor shutdown failed");
            });
            controller.show();

            assertThrows(IllegalStateException.class, controller::close);

            assertEquals(1, adapter.disposeCalls.get());
            assertNull(lifecycleFieldValue(controller, "activeAdapter"));
            assertNull(lifecycleFieldValue(controller, "windowChromeView"));
        });
    }

    /** Створює контролер із тестовим адаптером та дією показу, що не відкриває вікно ОС. */
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

    /** Створює контролер із тестовим адаптером та порожньою дією показу. */
    private MainWindowController createController(Stage stage, RecordingWindowPlatformAdapter adapter) {
        return createController(stage, adapter, () -> { });
    }

    /** Повертає узгоджені налаштування для ізольованого тестового вікна. */
    private static AppConfig.Window windowConfig() {
        return new AppConfig.Window("NeuronMap test", 640.0, 480.0, "window-state.properties");
    }

    /** Зчитує приватний ресурс компонента життєвого циклу, щоб перевірити фактичне володіння ним. */
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

    /** Виконує дії на JavaFX Application Thread і передає помилку тестовому потоку. */
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

    /** Тестовий адаптер рахує спроби встановлення й очищення та може імітувати збій інтеграції. */
    private static final class RecordingWindowPlatformAdapter implements WindowPlatformAdapter {

        private final RuntimeException installationFailure;
        private final AtomicInteger installCalls = new AtomicInteger();
        private final AtomicInteger disposeCalls = new AtomicInteger();

        /** Створює адаптер, який успішно встановлюється або кидає передану помилку. */
        private RecordingWindowPlatformAdapter(RuntimeException installationFailure) {
            this.installationFailure = installationFailure;
        }

        /** Фіксує спробу встановлення та відтворює помилку, якщо вона задана тестом. */
        @Override
        public void install(Stage stage, WindowChrome chrome) {
            installCalls.incrementAndGet();
            if (installationFailure != null) {
                throw installationFailure;
            }
        }

        /** Рахує звільнення ресурсів для перевірки одноразового очищення. */
        @Override
        public void dispose() {
            disposeCalls.incrementAndGet();
        }
    }
}
