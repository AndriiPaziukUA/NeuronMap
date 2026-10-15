package com.example.neuronmap.windowing;

import com.example.neuronmap.window.WindowChrome;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє одноразовий життєвий цикл резервного адаптера та очищення після невдалого встановлення. */
final class JavaFxWindowPlatformAdapterLifecycleTest {

    /**
     * Запускає JavaFX toolkit для перевірок реєстрації обробників на сцені.
     *
     * @throws InterruptedException якщо очікування запуску JavaFX перервано.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                started.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                started.countDown();
            });
        }
        if (!started.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /**
     * Перевіряє, що повторне очищення безпечне, але очищений екземпляр не можна встановити повторно.
     *
     * @throws InterruptedException якщо очікування завершення перевірки перервано.
     */
    @Test
    void disposeIsIdempotentAndAdapterCannotBeReinstalled() throws InterruptedException {
        runOnFxThread(() -> {
            Stage stage = stageWithScene();
            JavaFxWindowPlatformAdapter adapter = new JavaFxWindowPlatformAdapter();
            WindowChrome chrome = chromeStub();

            adapter.install(stage, chrome);
            assertThrows(IllegalStateException.class, () -> adapter.install(stage, chrome));
            adapter.dispose();
            adapter.dispose();

            assertThrows(IllegalStateException.class, () -> adapter.install(stage, chrome));
            stage.close();
        });
    }

    /**
     * Перевіряє, що невдале встановлення очищує посилання та завершує життєвий цикл адаптера.
     *
     * @throws InterruptedException якщо очікування завершення перевірки перервано.
     */
    @Test
    void failedInstallationLeavesAdapterDisposed() throws InterruptedException {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            JavaFxWindowPlatformAdapter adapter = new JavaFxWindowPlatformAdapter();
            WindowChrome chrome = chromeStub();

            assertThrows(IllegalStateException.class, () -> adapter.install(stage, chrome));
            stage.setScene(new Scene(new StackPane(), 320.0, 240.0));
            assertThrows(IllegalStateException.class, () -> adapter.install(stage, chrome));
            adapter.dispose();
            stage.close();
        });
    }

    /** Створює тестовий Stage із коренем сцени без показу нативного вікна. */
    private static Stage stageWithScene() {
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(), 320.0, 240.0));
        return stage;
    }

    /** Створює нейтральну реалізацію контракту, оскільки тести перевіряють лише життєвий цикл обробників. */
    private static WindowChrome chromeStub() {
        return (WindowChrome) Proxy.newProxyInstance(
                WindowChrome.class.getClassLoader(),
                new Class<?>[]{WindowChrome.class},
                (proxy, method, arguments) -> null
        );
    }

    /**
     * Виконує тестову дію на JavaFX Application Thread та передає виняток назад у тестовий потік.
     *
     * @param operation операція, яку потрібно виконати на потоці JavaFX.
     * @throws InterruptedException якщо очікування завершення операції перервано.
     */
    private static void runOnFxThread(Runnable operation) throws InterruptedException {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                operation.run();
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                finished.countDown();
            }
        });
        if (!finished.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX operation timed out");
        }
        Throwable exception = failure.get();
        if (exception instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (exception instanceof Error error) {
            throw error;
        }
        if (exception != null) {
            throw new AssertionError("JavaFX operation failed", exception);
        }
    }
}
