package com.example.neuronmap.view.window;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderButtonType;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Відображає локалізований заголовок редактора всередині системної {@link HeaderBar} JavaFX.
 * Переміщення вікна, зміна його розміру та семантика кнопок делегуються JavaFX і операційній системі.
 */
public final class WindowChromeView extends BorderPane {

    /** Висота смуги заголовка в логічних пікселях. */
    public static final double TITLE_BAR_HEIGHT = 36.0;

    private static final String LABELS_BUNDLE = "i18n.windowChrome";

    private final Stage stage;
    private final LocalizationService localization;
    private final HBox titleBar = new HBox();
    private final Label titleLabel = new Label();
    private final Region spacer = new Region();
    private final Button minimizeButton = createButton("window-control-minimize", createMinimizeIcon());
    private final Button maximizeButton = createButton("window-control-maximize", createMaximizeIcon(false));
    private final Button closeButton = createButton("window-control-close", createCloseIcon());
    private final Consumer<Locale> localeChangeListener = ignored -> refreshControlLabels();
    private final ChangeListener<Boolean> maximizedListener =
            (observable, wasMaximized, isMaximized) -> refreshWindowState();
    private final ChangeListener<Boolean> fullScreenListener =
            (observable, wasFullScreen, isFullScreen) -> refreshWindowState();

    private boolean maximized;
    private boolean disposed;
    private boolean extendedHeaderBarInstalled;

    /**
     * Створює заголовок і підписує його на зміни назви, локалі та стану системного вікна.
     *
     * @param stage головне вікно, властивості якого відображає подання.
     * @param localization спільна служба локалізації застосунку.
     * @throws NullPointerException якщо будь-який аргумент не задано.
     */
    public WindowChromeView(Stage stage, LocalizationService localization) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.localization = Objects.requireNonNull(localization, "localization");

        getStyleClass().add("window-chrome-root");
        titleBar.getStyleClass().add("window-title-bar");
        titleBar.setMinHeight(TITLE_BAR_HEIGHT);
        titleBar.setPrefHeight(TITLE_BAR_HEIGHT);
        titleBar.setMaxHeight(TITLE_BAR_HEIGHT);
        titleBar.setPadding(new Insets(3.0, 6.0, 3.0, 12.0));
        titleBar.setFillHeight(true);

        titleLabel.getStyleClass().add("window-title");
        titleLabel.textProperty().bind(stage.titleProperty());
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleBar.getChildren().addAll(titleLabel, spacer, minimizeButton, maximizeButton, closeButton);
        setTop(titleBar);

        stage.maximizedProperty().addListener(maximizedListener);
        stage.fullScreenProperty().addListener(fullScreenListener);
        localization.addListener(localeChangeListener);
        maximized = stage.isMaximized() || stage.isFullScreen();
        refreshControlLabels();
    }

    /**
     * Встановлює вміст редактора під заголовком і додає таблицю стилів до сцени.
     * Виконується після створення сцени й до показу Stage.
     *
     * @param scene сцена з уже створеним редактором.
     * @throws NullPointerException якщо сцену не задано.
     * @throws IllegalStateException якщо ресурс стилів відсутній.
     */
    public void installInto(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        Node editorContent = scene.getRoot();
        if (editorContent == this) {
            return;
        }
        setCenter(editorContent);
        scene.setRoot(this);

        URL stylesheet = getClass().getResource("/window-chrome.css");
        if (stylesheet == null) {
            throw new IllegalStateException("window-chrome.css is missing");
        }
        String stylesheetUrl = stylesheet.toExternalForm();
        if (!scene.getStylesheets().contains(stylesheetUrl)) {
            scene.getStylesheets().add(stylesheetUrl);
        }
    }

    /**
     * Передає системну взаємодію заголовка API JavaFX та задає семантичні ролі трьом кнопкам.
     * Інтерактивні кнопки явно виключено з успадкованої перетягуваної області.
     *
     * @throws IllegalStateException якщо Stage не використовує {@link StageStyle#EXTENDED} або операцію вже виконано.
     */
    public void installExtendedHeaderBar() {
        if (stage.getStyle() != StageStyle.EXTENDED) {
            throw new IllegalStateException("Extended header bar requires StageStyle.EXTENDED");
        }
        if (extendedHeaderBarInstalled) {
            throw new IllegalStateException("Extended header bar is already installed");
        }

        HeaderBar headerBar = new HeaderBar();
        headerBar.getStyleClass().add("window-title-bar-host");
        headerBar.setMinHeight(TITLE_BAR_HEIGHT);
        headerBar.setPrefHeight(TITLE_BAR_HEIGHT);
        headerBar.setMaxHeight(TITLE_BAR_HEIGHT);
        HeaderBar.setSystemButtonHeight(stage, 0.0);
        HeaderBar.setButtonType(minimizeButton, HeaderButtonType.ICONIFY);
        HeaderBar.setButtonType(maximizeButton, HeaderButtonType.MAXIMIZE);
        HeaderBar.setButtonType(closeButton, HeaderButtonType.CLOSE);
        HeaderBar.setDragType(titleBar, HeaderDragType.DRAGGABLE_SUBTREE);
        HeaderBar.setDragType(minimizeButton, HeaderDragType.NONE);
        HeaderBar.setDragType(maximizeButton, HeaderDragType.NONE);
        HeaderBar.setDragType(closeButton, HeaderDragType.NONE);

        titleBar.setPadding(new Insets(3.0, 0.0, 3.0, 12.0));
        titleBar.setMaxWidth(Double.MAX_VALUE);
        setTop(null);
        headerBar.setCenter(titleBar);
        setTop(headerBar);

        // Стандартні дії кнопок визначаються їхніми системними ролями HeaderButtonType.
        extendedHeaderBarInstalled = true;
        requestLayout();
    }

    /**
     * Звільняє слухачі локалі та стану вікна й від'єднує прив'язку назви заголовка.
     * Повторний виклик безпечний.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        localization.removeListener(localeChangeListener);
        stage.maximizedProperty().removeListener(maximizedListener);
        stage.fullScreenProperty().removeListener(fullScreenListener);
        titleLabel.textProperty().unbind();
    }

    /** Оновлює доступні назви кнопок відповідно до активної локалі. */
    private void refreshControlLabels() {
        setControlLabel(minimizeButton, localization.textFrom(LABELS_BUNDLE, "minimize"));
        setControlLabel(closeButton, localization.textFrom(LABELS_BUNDLE, "close"));
        updateMaximizeIcon();
    }

    /** Узгоджує значок і доступну назву кнопки максимізації зі станом Stage. */
    private void refreshWindowState() {
        maximized = stage.isMaximized() || stage.isFullScreen();
        updateMaximizeIcon();
    }

    /** Оновлює графічний значок і доступну назву кнопки максимізації або відновлення. */
    private void updateMaximizeIcon() {
        maximizeButton.setGraphic(createMaximizeIcon(maximized));
        String labelKey = maximized ? "restore" : "maximize";
        maximizeButton.setAccessibleText(localization.textFrom(LABELS_BUNDLE, labelKey));
    }

    /** Застосовує локалізовану доступну назву кнопки для допоміжних технологій. */
    private static void setControlLabel(Button button, String label) {
        button.setAccessibleText(label);
    }

    /** Створює кнопку заголовка з векторним значком і класами стилю. */
    private static Button createButton(String styleClass, Node graphic) {
        Button button = new Button();
        button.setGraphic(graphic);
        button.getStyleClass().addAll("window-control-button", styleClass);
        button.setFocusTraversable(false);
        return button;
    }

    /** Створює значок згортання у вигляді короткої горизонтальної лінії. */
    private static Node createMinimizeIcon() {
        Line line = new Line(3.0, 8.0, 13.0, 8.0);
        line.getStyleClass().add("window-icon-stroke");
        line.setStrokeWidth(1.2);
        return iconCanvas(line);
    }

    /** Створює значок максимізації або відновлення з прямокутників. */
    private static Node createMaximizeIcon(boolean restore) {
        Rectangle front = new Rectangle(3.0, restore ? 5.0 : 3.0, 10.0, 10.0);
        front.setFill(Color.TRANSPARENT);
        front.getStyleClass().add("window-icon-outline");
        front.setStrokeWidth(1.2);
        if (!restore) {
            return iconCanvas(front);
        }
        Rectangle back = new Rectangle(5.0, 3.0, 9.0, 9.0);
        back.setFill(Color.TRANSPARENT);
        back.getStyleClass().add("window-icon-outline");
        back.setStrokeWidth(1.2);
        return iconCanvas(back, front);
    }

    /** Створює значок закриття із двох діагональних ліній. */
    private static Node createCloseIcon() {
        Line first = new Line(3.0, 3.0, 13.0, 13.0);
        Line second = new Line(3.0, 13.0, 13.0, 3.0);
        first.getStyleClass().add("window-icon-stroke");
        second.getStyleClass().add("window-icon-stroke");
        first.setStrokeWidth(1.5);
        second.setStrokeWidth(1.5);
        return iconCanvas(first, second);
    }

    /** Розміщує графічні елементи в області однакового розміру для центрування значків. */
    private static Pane iconCanvas(Node... shapes) {
        Pane canvas = new Pane(shapes);
        canvas.setMinSize(16.0, 16.0);
        canvas.setPrefSize(16.0, 16.0);
        canvas.setMaxSize(16.0, 16.0);
        canvas.setMouseTransparent(true);
        return canvas;
    }
}
