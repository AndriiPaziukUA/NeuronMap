package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.window.WindowStateStore;
import com.example.neuronmap.view.window.WindowChromeView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє єдиний механізм оформлення вікна через розширений заголовок JavaFX на різних ОС. */
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

    /** Перевіряє стиль Stage, семантичні кнопки та виключення кнопок із перетягуваної області. */
    @Test
    void configuresExtendedHeaderWithOneNativeWindowMechanism() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            StackPane editorRoot = new StackPane();
            Scene scene = new Scene(editorRoot, 640.0, 480.0);
            AtomicInteger editorShutdownCalls = new AtomicInteger();
            MainWindowController controller = createController(stage, true);

            controller.configure(windowConfig(), scene, editorShutdownCalls::incrementAndGet);

            assertEquals(StageStyle.EXTENDED, stage.getStyle());
            WindowChromeView chromeView = assertInstanceOf(WindowChromeView.class, scene.getRoot());
            HeaderBar headerBar = assertInstanceOf(HeaderBar.class, chromeView.getTop());
            assertEquals(0.0, HeaderBar.getSystemButtonHeight(stage));
            HBox titleBar = assertInstanceOf(HBox.class, headerBar.getCenter());
            Label titleLabel = assertInstanceOf(Label.class, findByStyleClass(titleBar, "window-title"));
            Button minimizeButton = assertInstanceOf(Button.class,
                    findByStyleClass(titleBar, "window-control-minimize"));
            Button maximizeButton = assertInstanceOf(Button.class,
                    findByStyleClass(titleBar, "window-control-maximize"));
            Button closeButton = assertInstanceOf(Button.class,
                    findByStyleClass(titleBar, "window-control-close"));

            assertEquals(HeaderDragType.DRAGGABLE_SUBTREE, HeaderBar.getDragType(titleBar));
            assertEquals(HeaderDragType.NONE, HeaderBar.getDragType(minimizeButton));
            assertEquals(HeaderDragType.NONE, HeaderBar.getDragType(maximizeButton));
            assertEquals(HeaderDragType.NONE, HeaderBar.getDragType(closeButton));
            assertEquals(HeaderButtonType.ICONIFY, HeaderBar.getButtonType(minimizeButton));
            assertEquals(HeaderButtonType.MAXIMIZE, HeaderBar.getButtonType(maximizeButton));
            assertEquals(HeaderButtonType.CLOSE, HeaderBar.getButtonType(closeButton));
            assertNull(minimizeButton.getOnAction());
            assertNull(maximizeButton.getOnAction());
            assertNull(closeButton.getOnAction());
            assertNotNull(titleLabel.getText());

            controller.close();
            assertEquals(1, editorShutdownCalls.get());
            assertSame(editorRoot, scene.getRoot());
            assertTrue(scene.getStylesheets().isEmpty());
        });
    }

    /** Перевіряє, що непідтримуване середовище завершує конфігурацію помилкою без запасного механізму. */
    @Test
    void rejectsUnsupportedExtendedWindowWithoutChangingSceneRoot() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            StackPane editorRoot = new StackPane();
            Scene scene = new Scene(editorRoot, 640.0, 480.0);
            MainWindowController controller = createController(stage, false);

            UnsupportedOperationException failure = assertThrows(
                    UnsupportedOperationException.class,
                    () -> controller.configure(windowConfig(), scene, () -> { })
            );

            assertTrue(failure.getMessage().contains("EXTENDED_WINDOW"));
            assertSame(editorRoot, scene.getRoot());
            assertEquals(StageStyle.DECORATED, stage.getStyle());
        });
    }

    /** Перевіряє, що повторний показ відхиляється й не запускає повторне налаштування оформлення. */
    @Test
    void preventsRepeatedShow() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            AtomicInteger showCalls = new AtomicInteger();
            MainWindowController controller = new MainWindowController(
                    stage,
                    new WindowStateStore(temporaryDirectory.resolve("show-window-state.properties")),
                    new LocalizationService(Locale.forLanguageTag("uk")),
                    showCalls::incrementAndGet,
                    () -> true
            );
            controller.configure(windowConfig(), new Scene(new StackPane(), 640.0, 480.0), () -> { });

            controller.show();

            assertEquals(1, showCalls.get());
            assertThrows(IllegalStateException.class, controller::show);
            assertEquals(1, showCalls.get());
            controller.close();
        });
    }

    /** Створює контролер із передбачуваним результатом перевірки можливостей JavaFX. */
    private MainWindowController createController(Stage stage, boolean extendedWindowSupported) {
        return new MainWindowController(
                stage,
                new WindowStateStore(temporaryDirectory.resolve("window-state.properties")),
                new LocalizationService(Locale.forLanguageTag("uk")),
                () -> { },
                () -> extendedWindowSupported
        );
    }

    /** Повертає узгоджені налаштування для ізольованого тестового вікна. */
    private static AppConfig.Window windowConfig() {
        return new AppConfig.Window("NeuronMap test", 640.0, 480.0, "window-state.properties");
    }

    /** Знаходить вузол у дереві JavaFX за CSS-класом, не покладаючись на порядок дітей. */
    private static Node findByStyleClass(Node root, String styleClass) {
        if (root.getStyleClass().contains(styleClass)) {
            return root;
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node match = findByStyleClass(child, styleClass);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    /** Виконує дію на JavaFX Application Thread і передає її помилки тестовому потоку. */
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
