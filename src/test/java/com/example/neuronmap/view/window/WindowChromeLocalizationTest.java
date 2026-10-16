package com.example.neuronmap.view.window;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє локалізацію заголовка та вибір платформного стилю для різних операційних систем. */
final class WindowChromeLocalizationTest {

    private static final String BUNDLE_BASE_NAME = "i18n.windowChrome";

    /** Запускає JavaFX toolkit для перевірок фактичного дерева вузлів заголовка. */
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

    /** Перевіряє англійські назви кнопок через спільний механізм отримання перекладів. */
    @Test
    void providesEnglishLabelsByDefault() {
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);

        assertEquals("Minimize", localization.textFrom(BUNDLE_BASE_NAME, "minimize"));
        assertEquals("Maximize", localization.textFrom(BUNDLE_BASE_NAME, "maximize"));
        assertEquals("Restore", localization.textFrom(BUNDLE_BASE_NAME, "restore"));
        assertEquals("Close", localization.textFrom(BUNDLE_BASE_NAME, "close"));
    }

    /** Перевіряє, що заголовок отримує переклади активної локалі та попереднього перегляду мови. */
    @Test
    void followsActiveLocaleWhenLanguageChanges() {
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);

        localization.setLanguage(SupportedLanguage.UKRAINIAN);
        assertEquals("Згорнути", localization.textFrom(BUNDLE_BASE_NAME, "minimize"));
        assertEquals("Відновити", localization.textFrom(BUNDLE_BASE_NAME, "restore"));

        localization.previewLanguage(SupportedLanguage.RUSSIAN);
        assertEquals("Свернуть", localization.textFrom(BUNDLE_BASE_NAME, "minimize"));
        assertEquals("Восстановить", localization.textFrom(BUNDLE_BASE_NAME, "restore"));
    }

    /** Перевіряє поведінку для відсутнього ключа та валідацію імені ресурсного набору. */
    @Test
    void handlesMissingKeysAndInvalidBundleNames() {
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);

        assertEquals("unknown.label", localization.textFrom(BUNDLE_BASE_NAME, "unknown.label"));
        assertEquals("", localization.textFrom(BUNDLE_BASE_NAME, " "));
        assertThrows(IllegalArgumentException.class, () -> localization.textFrom(" ", "minimize"));
    }

    /** Перевіряє окремий стиль macOS та традиційне розташування кнопок ліворуч. */
    @Test
    void selectsMacOsPlatformStyle() {
        assertEquals(WindowChromePlatformStyle.MACOS, WindowChromePlatformStyle.forOperatingSystem("Mac OS X"));
        assertEquals(WindowChromePlatformStyle.MACOS, WindowChromePlatformStyle.forOperatingSystem("Darwin"));
        assertTrue(WindowChromePlatformStyle.MACOS.controlsOnLeadingEdge());
        assertEquals("window-chrome-macos", WindowChromePlatformStyle.MACOS.rootStyleClass());
        assertEquals("/window-chrome-macos.css", WindowChromePlatformStyle.MACOS.stylesheetResource().orElseThrow());
    }

    /** Перевіряє стилі Windows та Linux із кнопками керування праворуч. */
    @Test
    void selectsWindowsAndLinuxPlatformStyles() {
        assertEquals(WindowChromePlatformStyle.WINDOWS, WindowChromePlatformStyle.forOperatingSystem("Windows 11"));
        assertEquals(WindowChromePlatformStyle.LINUX, WindowChromePlatformStyle.forOperatingSystem("Linux"));
        assertFalse(WindowChromePlatformStyle.WINDOWS.controlsOnLeadingEdge());
        assertFalse(WindowChromePlatformStyle.LINUX.controlsOnLeadingEdge());
        assertTrue(WindowChromePlatformStyle.WINDOWS.stylesheetResource().isEmpty());
        assertEquals("/window-chrome-linux.css", WindowChromePlatformStyle.LINUX.stylesheetResource().orElseThrow());
    }


    /** Перевіряє доступні назви системної кнопки з урахуванням різної поведінки macOS та інших ОС. */
    @Test
    void selectsAccessibleMaximizeLabelsByPlatformState() {
        assertEquals("enterFullScreen", WindowChromePlatformStyle.MACOS.maximizeLabelKey(false, false));
        assertEquals("exitFullScreen", WindowChromePlatformStyle.MACOS.maximizeLabelKey(false, true));
        assertEquals("enterFullScreen", WindowChromePlatformStyle.MACOS.maximizeLabelKey(true, false));
        assertEquals("maximize", WindowChromePlatformStyle.WINDOWS.maximizeLabelKey(false, false));
        assertEquals("restore", WindowChromePlatformStyle.LINUX.maximizeLabelKey(true, false));

        LocalizationService english = new LocalizationService(Locale.ENGLISH);
        assertEquals("Enter Full Screen", english.textFrom(BUNDLE_BASE_NAME, "enterFullScreen"));
        assertEquals("Exit Full Screen", english.textFrom(BUNDLE_BASE_NAME, "exitFullScreen"));

        LocalizationService ukrainian = new LocalizationService(Locale.forLanguageTag("uk"));
        assertEquals("Увійти в повноекранний режим", ukrainian.textFrom(BUNDLE_BASE_NAME, "enterFullScreen"));
        assertEquals("Вийти з повноекранного режиму", ukrainian.textFrom(BUNDLE_BASE_NAME, "exitFullScreen"));

        LocalizationService russian = new LocalizationService(Locale.forLanguageTag("ru"));
        assertEquals("Перейти в полноэкранный режим", russian.textFrom(BUNDLE_BASE_NAME, "enterFullScreen"));
        assertEquals("Выйти из полноэкранного режима", russian.textFrom(BUNDLE_BASE_NAME, "exitFullScreen"));
    }

    /** Перевіряє реальне дерево заголовка, стилі та порядок кнопок для трьох платформ. */
    @Test
    void createsPlatformSpecificLayoutsAndRestoresSceneOnDispose() throws Exception {
        runOnFxThread(() -> {
            for (WindowChromePlatformStyle style : new WindowChromePlatformStyle[]{
                    WindowChromePlatformStyle.WINDOWS,
                    WindowChromePlatformStyle.MACOS,
                    WindowChromePlatformStyle.LINUX
            }) {
                Stage stage = new Stage();
                stage.initStyle(StageStyle.EXTENDED);
                StackPane editorRoot = new StackPane();
                Scene scene = new Scene(editorRoot, 800.0, 600.0);
                String baseStylesheet = getClass().getResource("/window-chrome.css").toExternalForm();
                scene.getStylesheets().add(baseStylesheet);
                WindowChromeView view = new WindowChromeView(
                        stage, new LocalizationService(Locale.ENGLISH), style);
                try {
                    view.installInto(scene);
                    view.installExtendedHeaderBar();

                    assertSame(view, scene.getRoot());
                    assertTrue(view.getStyleClass().contains(style.rootStyleClass()));
                    assertTrue(scene.getStylesheets().contains(baseStylesheet));
                    assertEquals(style.stylesheetResource().isPresent() ? 2 : 1, scene.getStylesheets().size());

                    HeaderBar headerBar = assertInstanceOf(HeaderBar.class, view.getTop());
                    HBox titleBar = assertInstanceOf(HBox.class, headerBar.getCenter());
                    Button minimize = assertInstanceOf(Button.class,
                            findByStyleClass(titleBar, "window-control-minimize"));
                    Button maximize = assertInstanceOf(Button.class,
                            findByStyleClass(titleBar, "window-control-maximize"));
                    Button close = assertInstanceOf(Button.class,
                            findByStyleClass(titleBar, "window-control-close"));
                    assertEquals(style == WindowChromePlatformStyle.MACOS ? "Enter Full Screen" : "Maximize",
                            maximize.getAccessibleText());

                    if (style == WindowChromePlatformStyle.MACOS) {
                        StackPane centeredHeader = assertInstanceOf(StackPane.class, titleBar.getChildren().get(0));
                        Label title = assertInstanceOf(Label.class, findByStyleClass(centeredHeader, "window-title"));
                        HBox controls = assertInstanceOf(HBox.class,
                                findByStyleClass(centeredHeader, "window-control-group"));
                        assertEquals(Pos.CENTER, StackPane.getAlignment(title));
                        assertEquals(Pos.CENTER_LEFT, StackPane.getAlignment(controls));
                        assertEquals(close, controls.getChildren().get(0));
                        assertEquals(minimize, controls.getChildren().get(1));
                        assertEquals(maximize, controls.getChildren().get(2));
                    } else {
                        assertEquals(titleBar.getChildren().get(2), minimize);
                        assertEquals(titleBar.getChildren().get(3), maximize);
                        assertEquals(titleBar.getChildren().get(4), close);
                        assertSame(findByStyleClass(titleBar, "window-title"), titleBar.getChildren().get(0));
                    }

                    view.dispose();
                    assertSame(editorRoot, scene.getRoot());
                    assertEquals(java.util.List.of(baseStylesheet), scene.getStylesheets());
                    assertNull(view.getCenter());
                } finally {
                    view.dispose();
                    stage.close();
                }
            }
        });
    }

    /** Переконується, що очищення оформлення не замінює корінь сцени, установлений стороннім компонентом. */
    @Test
    void doesNotOverwriteRootReplacedByAnotherComponent() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            stage.initStyle(StageStyle.EXTENDED);
            StackPane editorRoot = new StackPane();
            Scene scene = new Scene(editorRoot, 800.0, 600.0);
            WindowChromeView view = new WindowChromeView(
                    stage, new LocalizationService(Locale.ENGLISH), WindowChromePlatformStyle.MACOS);
            StackPane replacementRoot = new StackPane();
            try {
                view.installInto(scene);
                view.installExtendedHeaderBar();
                scene.setRoot(replacementRoot);

                view.dispose();

                assertSame(replacementRoot, scene.getRoot());
                assertNull(view.getCenter());
                assertTrue(scene.getStylesheets().isEmpty());
            } finally {
                view.dispose();
                stage.close();
            }
        });
    }

    /** Знаходить вузол за CSS-класом у всьому дереві подання. */
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

    /** Перевіряє стандартне компонування для невідомої ОС та відхилення відсутньої назви. */
    @Test
    void usesDefaultStyleForUnknownOperatingSystem() {
        assertEquals(WindowChromePlatformStyle.OTHER, WindowChromePlatformStyle.forOperatingSystem("FreeBSD"));
        assertEquals(WindowChromePlatformStyle.OTHER, WindowChromePlatformStyle.forOperatingSystem(""));
        assertTrue(WindowChromePlatformStyle.OTHER.stylesheetResource().isEmpty());
        assertThrows(NullPointerException.class, () -> WindowChromePlatformStyle.forOperatingSystem(null));
    }
}
