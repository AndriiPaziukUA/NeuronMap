package com.example.neuronmap.view.window;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Перевіряє геометрію кнопок стилізованого заголовка на платформах із різним компонуванням. */
final class WindowChromeControlGeometryTest {

    /** Перевіряє квадратні кнопки Windows/Linux, повну висоту заголовка та відсутність верхнього/правого відступу. */
    @Test
    void windowsAndLinuxControlsAreSquareAndFlushWithHeaderEdges() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            for (WindowChromePlatformStyle style : new WindowChromePlatformStyle[]{
                    WindowChromePlatformStyle.WINDOWS,
                    WindowChromePlatformStyle.LINUX
            }) {
                Stage stage = new Stage();
                stage.initStyle(StageStyle.EXTENDED);
                StackPane originalRoot = new StackPane();
                Scene scene = new Scene(originalRoot, 800.0, 600.0);
                stage.setScene(scene);
                WindowChromeView view = new WindowChromeView(
                        stage, new LocalizationService(Locale.ENGLISH), style);
                try {
                    view.installInto(scene);
                    view.installExtendedHeaderBar();
                    layoutWindowChrome(view);

                    HeaderBar headerBar = (HeaderBar) view.getTop();
                    HBox titleBar = (HBox) headerBar.getCenter();
                    Button minimize = button(titleBar, "window-control-minimize");
                    Button maximize = button(titleBar, "window-control-maximize");
                    Button close = button(titleBar, "window-control-close");

                    for (Button control : new Button[]{minimize, maximize, close}) {
                        assertNull(control.getEffect(),
                                "Кнопка не повинна мати ефектів, що візуально виходять за межі її прямокутника");
                        assertEquals(WindowChromeView.TITLE_BAR_HEIGHT, control.getWidth(), 0.5,
                                "Кнопка має бути квадратною за шириною: " + control.getStyleClass());
                        assertEquals(control.getWidth(), control.getHeight(), 0.5,
                                "Ширина та висота кнопки мають збігатися: " + control.getStyleClass());
                        Bounds bounds = control.getBoundsInParent();
                        assertEquals(0.0, bounds.getMinY(), 0.5,
                                "Кнопка має починатися біля верхнього краю заголовка");
                        assertEquals(titleBar.getHeight(), bounds.getMaxY(), 0.5,
                                "Кнопка має доходити до нижнього краю заголовка");
                    }

                    assertEquals(titleBar.getWidth(), close.getBoundsInParent().getMaxX(), 0.5,
                            "Кнопка закриття має доходити до правого краю заголовка");
                } finally {
                    view.dispose();
                    stage.close();
                }
            }
        });
    }

    /** Переконується, що загальний розмір кнопок не скасовує кругле компактне оформлення macOS. */
    @Test
    void macOsTrafficLightControlsRemainCompactAndDistinctFromWindowsAndLinux() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Stage stage = new Stage();
            stage.initStyle(StageStyle.EXTENDED);
            Scene scene = new Scene(new StackPane(), 800.0, 600.0);
            stage.setScene(scene);
            WindowChromeView view = new WindowChromeView(
                    stage, new LocalizationService(Locale.ENGLISH), WindowChromePlatformStyle.MACOS);
            try {
                view.installInto(scene);
                view.installExtendedHeaderBar();
                layoutWindowChrome(view);

                HeaderBar headerBar = (HeaderBar) view.getTop();
                HBox titleBar = (HBox) headerBar.getCenter();
                Button close = button(titleBar, "window-control-close");

                assertEquals(13.0, close.getWidth(), 0.5);
                assertEquals(close.getWidth(), close.getHeight(), 0.5);
                assertEquals(13.0, close.getHeight(), 0.5,
                        "Кнопка macOS має залишатися компактною, а не займати всю висоту смуги");
            } finally {
                view.dispose();
                stage.close();
            }
        });
    }

    /** Застосовує CSS і компонування до всіх контейнерів заголовка перед вимірюванням геометрії. */
    private static void layoutWindowChrome(WindowChromeView view) {
        view.resize(800.0, 600.0);
        view.applyCss();
        view.layout();
        HeaderBar headerBar = (HeaderBar) view.getTop();
        headerBar.applyCss();
        headerBar.layout();
        HBox titleBar = (HBox) headerBar.getCenter();
        titleBar.applyCss();
        titleBar.layout();
    }

    /** Знаходить кнопку заголовка за її CSS-класом і повідомляє тесту про помилку структури, якщо її немає. */
    private static Button button(HBox titleBar, String styleClass) {
        javafx.scene.Node control = titleBar.lookup("." + styleClass);
        assertNotNull(control, "У заголовку відсутня кнопка " + styleClass);
        return (Button) control;
    }
}
