package com.example.neuronmap.windowing;

import com.example.neuronmap.window.WindowChrome;
import com.example.neuronmap.window.WindowHitTestCalculator;
import javafx.application.Platform;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            WindowChrome chrome = chromeStub(null);

            adapter.install(stage, chrome);
            assertThrows(IllegalStateException.class, () -> adapter.install(stage, chrome));
            adapter.dispose();
            adapter.dispose();

            assertThrows(IllegalStateException.class, () -> adapter.install(stage, chrome));
            stage.close();
        });
    }

    /**
     * Перевіряє, що клацання по краю кнопки не запускає resize, навіть якщо цільовий вузол не має CSS-класу кнопки.
     *
     * @throws InterruptedException якщо очікування завершення перевірки перервано.
     */
    @Test
    void captionButtonEdgeDoesNotStartResizeWithoutButtonStyleClass() throws InterruptedException {
        runOnFxThread(() -> {
            StackPane root = new StackPane();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 320.0, 240.0));
            stage.setX(100.0);
            stage.setY(100.0);
            stage.setWidth(320.0);
            stage.setHeight(240.0);
            stage.setMinWidth(100.0);
            stage.setMinHeight(100.0);

            WindowHitTestCalculator.Layout layout = new WindowHitTestCalculator.Layout(
                    320.0,
                    240.0,
                    36.0,
                    WindowHitTestCalculator.DEFAULT_RESIZE_BORDER,
                    WindowHitTestCalculator.DEFAULT_RESIZE_BORDER,
                    false,
                    new WindowHitTestCalculator.Rectangle(240.0, 3.0, 260.0, 33.0),
                    new WindowHitTestCalculator.Rectangle(260.0, 3.0, 280.0, 33.0),
                    new WindowHitTestCalculator.Rectangle(280.0, 3.0, 314.0, 33.0)
            );
            WindowChrome chrome = chromeStub(layout);
            JavaFxWindowPlatformAdapter adapter = new JavaFxWindowPlatformAdapter();
            adapter.install(stage, chrome);

            double initialX = stage.getX();
            double initialY = stage.getY();
            double initialWidth = stage.getWidth();
            double initialHeight = stage.getHeight();
            assertFalse(root.getStyleClass().contains("window-control-button"));

            root.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 300.0, 3.0, 400.0, 400.0));
            root.fireEvent(mouseEvent(MouseEvent.MOUSE_DRAGGED, 300.0, 43.0, 400.0, 440.0));

            assertEquals(initialX, stage.getX(), 0.001);
            assertEquals(initialY, stage.getY(), 0.001);
            assertEquals(initialWidth, stage.getWidth(), 0.001);
            assertEquals(initialHeight, stage.getHeight(), 0.001);
            adapter.dispose();
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
            WindowChrome chrome = chromeStub(null);

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

    /**
     * Створює реалізацію контракту, яка повертає переданий знімок геометрії й не виконує нативних команд.
     *
     * @param layout геометрія для перевірки або {@code null}, якщо hit-test не використовується.
     * @return тестова реалізація {@link WindowChrome}.
     */
    private static WindowChrome chromeStub(WindowHitTestCalculator.Layout layout) {
        return (WindowChrome) Proxy.newProxyInstance(
                WindowChrome.class.getClassLoader(),
                new Class<?>[]{WindowChrome.class},
                (proxy, method, arguments) -> method.getName().equals("hitTestLayout") ? layout : null
        );
    }

    /**
     * Створює синтетичну подію миші для тестового кореня JavaFX.
     *
     * @param type тип події миші.
     * @param x локальна координата курсора по горизонталі.
     * @param y локальна координата курсора по вертикалі.
     * @param screenX координата курсора на екрані по горизонталі.
     * @param screenY координата курсора на екрані по вертикалі.
     * @return синтетична подія основної кнопки миші.
     */
    private static MouseEvent mouseEvent(
            EventType<MouseEvent> type,
            double x,
            double y,
            double screenX,
            double screenY
    ) {
        return new MouseEvent(
                type,
                x,
                y,
                screenX,
                screenY,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                null
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
