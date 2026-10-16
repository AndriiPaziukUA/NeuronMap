package com.example.neuronmap.app;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.window.WindowStateStore;
import com.example.neuronmap.view.window.WindowChromeView;
import com.example.neuronmap.windowing.WindowPlatformAdapter;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderButtonType;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Перевіряє, що вбудований заголовок JavaFX замінює ручний платформний hit-testing за наявності підтримки. */
final class WindowChromeLifecycleExtendedWindowTest {

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

    /** Перевіряє нативний стиль, семантику кнопок і те, що фабрика ручних адаптерів не викликається. */
    @Test
    void configuresExtendedHeaderWithoutCreatingPlatformAdapter() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            Scene scene = new Scene(new StackPane(), 640.0, 480.0);
            AtomicInteger adapterFactoryCalls = new AtomicInteger();
            AtomicInteger editorShutdownCalls = new AtomicInteger();
            Supplier<Optional<WindowPlatformAdapter>> adapterFactory = () -> {
                adapterFactoryCalls.incrementAndGet();
                return Optional.empty();
            };
            MainWindowController controller = new MainWindowController(
                    stage,
                    new WindowStateStore(temporaryDirectory.resolve("window-state.properties")),
                    new LocalizationService(Locale.forLanguageTag("uk")),
                    adapterFactory,
                    () -> { },
                    () -> true
            );

            controller.configure(
                    new com.example.neuronmap.config.AppConfig.Window(
                            "NeuronMap test", 640.0, 480.0, "window-state.properties"),
                    scene,
                    editorShutdownCalls::incrementAndGet
            );

            assertEquals(StageStyle.EXTENDED, stage.getStyle());
            assertEquals(0, adapterFactoryCalls.get(), "У нативному режимі ручний адаптер не повинен створюватися");
            WindowChromeView chromeView = assertInstanceOf(WindowChromeView.class, scene.getRoot());
            HeaderBar headerBar = assertInstanceOf(HeaderBar.class, chromeView.getTop());
            assertEquals(0.0, HeaderBar.getSystemButtonHeight(stage));
            HBox titleBar = assertInstanceOf(HBox.class, headerBar.getCenter());
            assertEquals(HeaderDragType.DRAGGABLE_SUBTREE, HeaderBar.getDragType(titleBar));
            assertEquals(HeaderButtonType.ICONIFY, HeaderBar.getButtonType((Button) titleBar.getChildren().get(2)));
            assertEquals(HeaderButtonType.MAXIMIZE, HeaderBar.getButtonType((Button) titleBar.getChildren().get(3)));
            assertEquals(HeaderButtonType.CLOSE, HeaderBar.getButtonType((Button) titleBar.getChildren().get(4)));

            controller.close();
            assertEquals(1, editorShutdownCalls.get());
        });
    }

    /** Виконує дію в JavaFX Application Thread і передає її помилки тестовому потоку. */
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
