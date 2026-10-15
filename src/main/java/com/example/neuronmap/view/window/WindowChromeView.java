package com.example.neuronmap.view.window;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.window.WindowChrome;
import com.example.neuronmap.window.WindowHitTestCalculator;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Відображає стилізовану смугу заголовка поверх наявного вмісту редактора.
 * Подання реалізує нейтральний контракт геометрії та дій, не залежачи від платформних адаптерів.
 */
public final class WindowChromeView extends BorderPane implements WindowChrome {

    /** Висота смуги заголовка в логічних пікселях. */
    public static final double TITLE_BAR_HEIGHT = 36.0;

    private static final String LABELS_BUNDLE = "i18n.windowChrome";
    private static final PseudoClass NATIVE_HOVER = PseudoClass.getPseudoClass("native-hover");
    private static final PseudoClass NATIVE_PRESSED = PseudoClass.getPseudoClass("native-pressed");

    private final Stage stage;
    private final LocalizationService localization;
    private final HBox titleBar = new HBox();
    private final Label titleLabel = new Label();
    private final Button minimizeButton = createButton("window-control-minimize", createMinimizeIcon());
    private final Button maximizeButton = createButton("window-control-maximize", createMaximizeIcon(false));
    private final Button closeButton = createButton("window-control-close", createCloseIcon());
    private final AtomicReference<WindowHitTestCalculator.Layout> hitTestLayout = new AtomicReference<>();
    private final Runnable closeAction;
    private final Runnable minimizeAction;
    private final Runnable toggleMaximizedAction;
    private final Consumer<Locale> localeChangeListener = ignored -> refreshControlLabels();
    private final ChangeListener<Boolean> maximizedListener = (observable, wasMaximized, isMaximized) -> {
        maximized = isMaximized;
        updateMaximizeIcon();
        refreshHitTestLayout();
    };
    private final EventHandler<WindowEvent> hiddenHandler = event -> dispose();
    private double resizeBorderX = WindowHitTestCalculator.DEFAULT_RESIZE_BORDER;
    private double resizeBorderY = WindowHitTestCalculator.DEFAULT_RESIZE_BORDER;
    private boolean maximized;
    private boolean disposed;

    /**
     * Створює смугу заголовка та кнопки, які делегують команди контролеру головного вікна.
     * Доступні назви кнопок оновлюються разом з активною локаллю застосунку.
     *
     * @param stage головне вікно, властивості якого відображає подання.
     * @param localization спільна служба локалізації застосунку.
     * @param closeAction команда закриття через спільний життєвий цикл застосунку.
     * @param minimizeAction команда згортання вікна.
     * @param toggleMaximizedAction команда перемикання максимізації та відновлення.
     * @throws NullPointerException якщо будь-який аргумент не задано.
     */
    public WindowChromeView(
            Stage stage,
            LocalizationService localization,
            Runnable closeAction,
            Runnable minimizeAction,
            Runnable toggleMaximizedAction
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
        this.minimizeAction = Objects.requireNonNull(minimizeAction, "minimizeAction");
        this.toggleMaximizedAction = Objects.requireNonNull(toggleMaximizedAction, "toggleMaximizedAction");

        getStyleClass().add("window-chrome-root");
        titleBar.getStyleClass().add("window-title-bar");
        titleBar.setMinHeight(TITLE_BAR_HEIGHT);
        titleBar.setPrefHeight(TITLE_BAR_HEIGHT);
        titleBar.setMaxHeight(TITLE_BAR_HEIGHT);
        titleBar.setPadding(new Insets(3.0, 6.0, 3.0, 12.0));
        titleBar.setFillHeight(true);

        titleLabel.getStyleClass().add("window-title");
        titleLabel.textProperty().bind(stage.titleProperty());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleBar.getChildren().addAll(titleLabel, spacer, minimizeButton, maximizeButton, closeButton);
        setTop(titleBar);

        minimizeButton.setOnAction(event -> this.minimizeAction.run());
        maximizeButton.setOnAction(event -> this.toggleMaximizedAction.run());
        closeButton.setOnAction(event -> this.closeAction.run());
        stage.maximizedProperty().addListener(maximizedListener);
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
        localization.addListener(localeChangeListener);
        maximized = stage.isMaximized();
        refreshControlLabels();
    }

    /**
     * Встановлює вміст редактора під заголовком і додає окрему таблицю стилів власної рамки до сцени.
     * Виконується після створення сцени й до показу Stage.
     *
     * @param scene сцена з уже створеним редактором.
     * @throws IllegalStateException якщо ресурс стилів відсутній.
     * @throws NullPointerException якщо сцену не задано.
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
        if (!scene.getStylesheets().contains(stylesheet.toExternalForm())) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        refreshHitTestLayout();
    }

    /**
     * Звільняє слухачі локалі та стану максимізації, коли головне вікно закрито.
     * Повторний виклик безпечний.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        localization.removeListener(localeChangeListener);
        stage.maximizedProperty().removeListener(maximizedListener);
        stage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
        titleLabel.textProperty().unbind();
        hitTestLayout.set(null);
    }

    /**
     * Повертає контейнер смуги заголовка для подань або засобів автоматизації, яким потрібен доступ до її вузлів.
     * Платформні адаптери натомість використовують спільний контракт {@link WindowChrome}.
     *
     * @return контейнер смуги заголовка.
     */
    public HBox titleBar() {
        return titleBar;
    }

    /**
     * Перевіряє, чи є вузол або один із його предків однією з кнопок заголовка.
     *
     * @param node вузол, який перевіряється.
     * @return {@code true}, якщо вузол належить кнопці заголовка.
     */
    public boolean isCaptionButtonNode(Node node) {
        Node current = node;
        while (current != null && current != titleBar) {
            if (current == minimizeButton || current == maximizeButton || current == closeButton) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Застосовує виміряні системні відступи resize, щоб зона курсора відповідала метрикам поточного DPI.
     * Виклик виконується платформним адаптером у JavaFX Application Thread.
     *
     * @param horizontalBorder горизонтальна товщина зон resize у логічних пікселях.
     * @param verticalBorder вертикальна товщина зон resize у логічних пікселях.
     * @throws IllegalArgumentException якщо будь-який відступ не є скінченним додатним числом.
     */
    @Override
    public void setNativeResizeBorders(double horizontalBorder, double verticalBorder) {
        if (!Double.isFinite(horizontalBorder) || horizontalBorder <= 0.0
                || !Double.isFinite(verticalBorder) || verticalBorder <= 0.0) {
            throw new IllegalArgumentException("native resize borders must be finite and positive");
        }
        resizeBorderX = horizontalBorder;
        resizeBorderY = verticalBorder;
        titleBar.setPadding(new Insets(3.0, Math.max(6.0, horizontalBorder), 3.0, 12.0));
        requestLayout();
        refreshHitTestLayout();
    }

    /**
     * Повертає останній незмінний знімок розташування кнопок і меж вікна для платформного callback-а.
     *
     * @return знімок геометрії або {@code null}, доки JavaFX не виконає перше компонування.
     */
    @Override
    public WindowHitTestCalculator.Layout hitTestLayout() {
        return hitTestLayout.get();
    }

    /**
     * Виконує команду кнопки після нативного кліку, який Windows передає як non-client mouse event.
     * Виклик має відбуватися в JavaFX Application Thread.
     *
     * @param target кнопка, на якій завершився клік.
     */
    @Override
    public void performNativeButtonAction(WindowHitTestCalculator.HitTarget target) {
        if (target == WindowHitTestCalculator.HitTarget.MINIMIZE_BUTTON) {
            minimizeAction.run();
        } else if (target == WindowHitTestCalculator.HitTarget.MAXIMIZE_BUTTON) {
            toggleMaximizedAction.run();
        } else if (target == WindowHitTestCalculator.HitTarget.CLOSE_BUTTON) {
            closeAction.run();
        }
    }

    /** Перемикає максимізацію після подвійного клацання по вільній області заголовка. */
    @Override
    public void performNativeCaptionDoubleClick() {
        toggleMaximizedAction.run();
    }

    /**
     * Перемикає нативний стан наведення конкретної кнопки без залежності від JavaFX mouse events.
     * Windows класифікує ці точки як non-client areas, тому звичайні події миші JavaFX можуть не надходити.
     *
     * @param target область, над якою перебуває курсор.
     */
    @Override
    public void setNativeHoveredControl(WindowHitTestCalculator.HitTarget target) {
        updateNativeButtonState(target, false);
    }

    /**
     * Перемикає нативний стан натискання конкретної кнопки заголовка.
     *
     * @param target область кнопки, яку натискають, або {@code null}, якщо натискання завершено.
     */
    @Override
    public void setNativePressedControl(WindowHitTestCalculator.HitTarget target) {
        updateNativeButtonState(target, true);
    }

    /** Після компонування оновлює знімок геометрії, який читає нативний callback. */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        refreshHitTestLayout();
    }

    /** Оновлює графічний значок кнопки відповідно до фактичного стану Stage. */
    private void updateMaximizeIcon() {
        maximizeButton.setGraphic(createMaximizeIcon(maximized));
        String labelKey = maximized ? "restore" : "maximize";
        String label = localization.textFrom(LABELS_BUNDLE, labelKey);
        maximizeButton.setAccessibleText(label);
    }

    /** Оновлює доступні назви кнопок відповідно до активної локалі. */
    private void refreshControlLabels() {
        setControlLabel(minimizeButton, localization.textFrom(LABELS_BUNDLE, "minimize"));
        setControlLabel(closeButton, localization.textFrom(LABELS_BUNDLE, "close"));
        updateMaximizeIcon();
    }

    /** Застосовує локалізовану доступну назву кнопки для допоміжних технологій. */
    private static void setControlLabel(Button button, String label) {
        button.setAccessibleText(label);
    }

    /** Перераховує межі кнопок і публікує актуальний незмінний знімок для платформного адаптера. */
    private void refreshHitTestLayout() {
        if (disposed || getScene() == null || getWidth() <= 0.0 || getHeight() <= 0.0
                || minimizeButton.getScene() == null || maximizeButton.getScene() == null
                || closeButton.getScene() == null) {
            return;
        }
        hitTestLayout.set(new WindowHitTestCalculator.Layout(
                getWidth(),
                getHeight(),
                titleBar.getHeight(),
                resizeBorderX,
                resizeBorderY,
                maximized,
                buttonRectangle(minimizeButton),
                buttonRectangle(maximizeButton),
                buttonRectangle(closeButton)
        ));
    }

    /**
     * Перетворює межі кнопки на координати відносно кореня власного вікна.
     *
     * @param button кнопка, межі якої потрібно перетворити.
     * @return прямокутник кнопки в координатах сцени.
     */
    private WindowHitTestCalculator.Rectangle buttonRectangle(Button button) {
        Bounds sceneBounds = button.localToScene(button.getBoundsInLocal());
        Bounds rootBounds = sceneToLocal(sceneBounds);
        return new WindowHitTestCalculator.Rectangle(
                rootBounds.getMinX(),
                rootBounds.getMinY(),
                rootBounds.getMaxX(),
                rootBounds.getMaxY()
        );
    }

    /**
     * Застосовує псевдоклас наведення або натискання лише до відповідної кнопки.
     *
     * @param target активна область кнопки або {@code null}, якщо жодна кнопка не активна.
     * @param pressed {@code true}, якщо оновлюється стан натискання; інакше стан наведення.
     */
    private void updateNativeButtonState(WindowHitTestCalculator.HitTarget target, boolean pressed) {
        updateButtonState(minimizeButton, target == WindowHitTestCalculator.HitTarget.MINIMIZE_BUTTON, pressed);
        updateButtonState(maximizeButton, target == WindowHitTestCalculator.HitTarget.MAXIMIZE_BUTTON, pressed);
        updateButtonState(closeButton, target == WindowHitTestCalculator.HitTarget.CLOSE_BUTTON, pressed);
    }

    /**
     * Оновлює псевдоклас однієї кнопки; не змішує нативний стан натискання зі станом наведення.
     *
     * @param button кнопка інтерфейсу.
     * @param active чи належить кнопка до активної нативної області.
     * @param pressed тип стану, який треба оновити.
     */
    private void updateButtonState(Button button, boolean active, boolean pressed) {
        button.pseudoClassStateChanged(pressed ? NATIVE_PRESSED : NATIVE_HOVER, active);
    }

    /** Створює кнопку заголовка з векторним значком і класом стилю. */
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

    /** Створює значок максимізації або відновлення з прямокутників у стилі Windows. */
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

    /** Створює великий хрестик із двох діагональних ліній замість текстового символу. */
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
