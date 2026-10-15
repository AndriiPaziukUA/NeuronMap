package com.example.neuronmap.windowing;

import com.example.neuronmap.window.WindowChrome;
import com.example.neuronmap.window.WindowHitTestCalculator;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Забезпечує переміщення та зміну розміру безрамкового JavaFX-вікна як резервний механізм.
 * Працює з коренем Scene та платформонезалежним контрактом заголовка, а не з конкретним класом подання.
 */
public final class JavaFxWindowPlatformAdapter implements WindowPlatformAdapter {

    private static final Logger LOGGER = Logger.getLogger(JavaFxWindowPlatformAdapter.class.getName());
    private static final double RESIZE_BORDER = WindowHitTestCalculator.DEFAULT_RESIZE_BORDER;
    private static final String CAPTION_BUTTON_STYLE_CLASS = "window-control-button";

    private final AtomicBoolean disposed = new AtomicBoolean();
    private Stage stage;
    private WindowChrome chrome;
    private Node eventSource;
    private EventHandler<MouseEvent> pressedHandler;
    private EventHandler<MouseEvent> draggedHandler;
    private EventHandler<MouseEvent> movedHandler;
    private EventHandler<MouseEvent> releasedHandler;
    private EventHandler<MouseEvent> clickedHandler;
    private final EventHandler<WindowEvent> hiddenHandler = event -> dispose();
    private boolean installAttempted;
    private ResizeEdge activeResizeEdge = ResizeEdge.NONE;
    private boolean moving;
    private double initialScreenX;
    private double initialScreenY;
    private double initialX;
    private double initialY;
    private double initialWidth;
    private double initialHeight;

    /** Створює резервний адаптер без підписки на події JavaFX. */
    public JavaFxWindowPlatformAdapter() {
    }

    /**
     * Під'єднує обробники миші до кореня сцени для ручного переміщення й зміни розміру вікна.
     * Екземпляр одноразовий: у разі помилки часткова реєстрація обробників очищується, а адаптер відкидається.
     *
     * @param stage безрамкове JavaFX-вікно зі створеною сценою.
     * @param chrome платформонезалежний контракт геометрії та команд заголовка.
     * @throws NullPointerException якщо не задано вікно або контракт.
     * @throws IllegalStateException якщо встановлення вже починалося або сцена відсутня.
     */
    @Override
    public void install(Stage stage, WindowChrome chrome) {
        if (installAttempted || disposed.get()) {
            throw new IllegalStateException("JavaFX window adapter can only be installed once");
        }
        Stage requestedStage = Objects.requireNonNull(stage, "stage");
        WindowChrome requestedChrome = Objects.requireNonNull(chrome, "chrome");
        installAttempted = true;
        this.stage = requestedStage;
        this.chrome = requestedChrome;

        try {
            if (requestedStage.getScene() == null || requestedStage.getScene().getRoot() == null) {
                throw new IllegalStateException(
                        "Stage must have a scene root before installing the JavaFX adapter");
            }
            eventSource = requestedStage.getScene().getRoot();
            pressedHandler = this::onMousePressed;
            draggedHandler = this::onMouseDragged;
            movedHandler = this::onMouseMoved;
            releasedHandler = this::onMouseReleased;
            clickedHandler = this::onMouseClicked;
            eventSource.addEventFilter(MouseEvent.MOUSE_PRESSED, pressedHandler);
            eventSource.addEventFilter(MouseEvent.MOUSE_DRAGGED, draggedHandler);
            eventSource.addEventFilter(MouseEvent.MOUSE_MOVED, movedHandler);
            eventSource.addEventFilter(MouseEvent.MOUSE_RELEASED, releasedHandler);
            eventSource.addEventFilter(MouseEvent.MOUSE_CLICKED, clickedHandler);
            requestedStage.addEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
        } catch (RuntimeException | LinkageError installationFailure) {
            try {
                dispose();
            } catch (RuntimeException | LinkageError cleanupFailure) {
                if (cleanupFailure != installationFailure) {
                    installationFailure.addSuppressed(cleanupFailure);
                }
            }
            throw installationFailure;
        }
    }

    /**
     * Від'єднує всі обробники миші, скидає активну операцію та звільняє посилання на сцену.
     * Очищення є ідемпотентним; після нього цей екземпляр не можна встановлювати повторно.
     */
    @Override
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        Stage currentStage = stage;
        Node currentEventSource = eventSource;
        Throwable cleanupFailure = null;
        if (currentEventSource != null) {
            cleanupFailure = attemptCleanup(
                    () -> currentEventSource.removeEventFilter(MouseEvent.MOUSE_PRESSED, pressedHandler),
                    cleanupFailure);
            cleanupFailure = attemptCleanup(
                    () -> currentEventSource.removeEventFilter(MouseEvent.MOUSE_DRAGGED, draggedHandler),
                    cleanupFailure);
            cleanupFailure = attemptCleanup(
                    () -> currentEventSource.removeEventFilter(MouseEvent.MOUSE_MOVED, movedHandler),
                    cleanupFailure);
            cleanupFailure = attemptCleanup(
                    () -> currentEventSource.removeEventFilter(MouseEvent.MOUSE_RELEASED, releasedHandler),
                    cleanupFailure);
            cleanupFailure = attemptCleanup(
                    () -> currentEventSource.removeEventFilter(MouseEvent.MOUSE_CLICKED, clickedHandler),
                    cleanupFailure);
            cleanupFailure = attemptCleanup(() -> currentEventSource.setCursor(Cursor.DEFAULT), cleanupFailure);
        }
        if (currentStage != null) {
            cleanupFailure = attemptCleanup(
                    () -> currentStage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler),
                    cleanupFailure);
        }
        clearReferences();
        if (cleanupFailure != null) {
            LOGGER.log(Level.WARNING, "Не вдалося повністю очистити резервний адаптер вікна JavaFX", cleanupFailure);
            if (cleanupFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw (LinkageError) cleanupFailure;
        }
    }

    /**
     * Виконує одну дію очищення та зберігає попередні помилки, щоб решта ресурсів теж була звільнена.
     *
     * @param cleanup дія звільнення одного ресурсу.
     * @param previousFailure раніше отримана помилка або {@code null}.
     * @return перша помилка очищення із доданими наступними помилками як suppressed.
     */
    private Throwable attemptCleanup(Runnable cleanup, Throwable previousFailure) {
        try {
            cleanup.run();
        } catch (RuntimeException | LinkageError cleanupFailure) {
            if (previousFailure == null) {
                return cleanupFailure;
            }
            if (previousFailure != cleanupFailure) {
                previousFailure.addSuppressed(cleanupFailure);
            }
        }
        return previousFailure;
    }

    /** Скидає активну операцію та звільняє всі посилання, які утримували Stage або корінь сцени. */
    private void clearReferences() {
        activeResizeEdge = ResizeEdge.NONE;
        moving = false;
        stage = null;
        chrome = null;
        eventSource = null;
        pressedHandler = null;
        draggedHandler = null;
        movedHandler = null;
        releasedHandler = null;
        clickedHandler = null;
    }

    /**
     * Перемикає максимізацію за подвійним клацанням по вільній області заголовка.
     *
     * @param event подія кліку миші.
     */
    private void onMouseClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                && event.getSceneY() <= titleBarHeight()
                && (!(event.getTarget() instanceof Node target) || !isCaptionButtonNode(target))) {
            chrome.performNativeCaptionDoubleClick();
            event.consume();
        }
    }

    /**
     * Починає переміщення або зміну розміру за координатами початкового натискання.
     *
     * @param event подія натискання кнопки миші.
     */
    private void onMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY
                || (event.getTarget() instanceof Node target && isCaptionButtonNode(target))) {
            return;
        }
        initialScreenX = event.getScreenX();
        initialScreenY = event.getScreenY();
        initialX = stage.getX();
        initialY = stage.getY();
        initialWidth = stage.getWidth();
        initialHeight = stage.getHeight();
        activeResizeEdge = stage.isMaximized() ? ResizeEdge.NONE : resizeEdgeAt(event.getSceneX(), event.getSceneY());
        moving = activeResizeEdge == ResizeEdge.NONE && event.getSceneY() <= titleBarHeight();
        if (moving || activeResizeEdge != ResizeEdge.NONE) {
            event.consume();
        }
    }

    /**
     * Застосовує дельту руху миші до розташування або розміру вікна.
     *
     * @param event подія перетягування миші.
     */
    private void onMouseDragged(MouseEvent event) {
        if (moving) {
            stage.setX(initialX + event.getScreenX() - initialScreenX);
            stage.setY(initialY + event.getScreenY() - initialScreenY);
            event.consume();
            return;
        }
        if (activeResizeEdge == ResizeEdge.NONE || stage.isMaximized()) {
            return;
        }
        double deltaX = event.getScreenX() - initialScreenX;
        double deltaY = event.getScreenY() - initialScreenY;
        resizeWindow(deltaX, deltaY);
        event.consume();
    }

    /**
     * Оновлює курсор відповідно до інтерактивної зони біля країв вікна.
     *
     * @param event подія переміщення миші.
     */
    private void onMouseMoved(MouseEvent event) {
        if (event.getTarget() instanceof Node target && isCaptionButtonNode(target)) {
            eventSource.setCursor(Cursor.DEFAULT);
            return;
        }
        ResizeEdge edge = stage.isMaximized() ? ResizeEdge.NONE : resizeEdgeAt(event.getSceneX(), event.getSceneY());
        eventSource.setCursor(edge.cursor());
    }

    /**
     * Завершує поточну операцію переміщення або зміни розміру.
     *
     * @param event подія відпускання кнопки миші.
     */
    private void onMouseReleased(MouseEvent event) {
        moving = false;
        activeResizeEdge = ResizeEdge.NONE;
    }

    /**
     * Повертає висоту заголовка за спільним знімком геометрії.
     *
     * @return висота заголовка або нуль, якщо компонування ще не завершилося.
     */
    private double titleBarHeight() {
        WindowHitTestCalculator.Layout layout = chrome.hitTestLayout();
        return layout == null ? 0.0 : layout.titleBarHeight();
    }

    /**
     * Перевіряє, чи належить вузол або один із його предків до кнопки заголовка за спільним класом стилю.
     *
     * @param node вузол події миші.
     * @return {@code true}, якщо вузол належить кнопці заголовка.
     */
    private boolean isCaptionButtonNode(Node node) {
        Node current = node;
        while (current != null && current != eventSource) {
            if (current.getStyleClass().contains(CAPTION_BUTTON_STYLE_CLASS)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Визначає зону зміни розміру для точки, що лежить біля одного або двох країв вікна.
     *
     * @param x горизонтальна координата сцени.
     * @param y вертикальна координата сцени.
     * @return зона resize або {@link ResizeEdge#NONE}.
     */
    private ResizeEdge resizeEdgeAt(double x, double y) {
        double width = eventSource.getBoundsInLocal().getWidth();
        double height = eventSource.getBoundsInLocal().getHeight();
        boolean left = x >= 0.0 && x < RESIZE_BORDER;
        boolean right = x <= width && x > width - RESIZE_BORDER;
        boolean top = y >= 0.0 && y < RESIZE_BORDER;
        boolean bottom = y <= height && y > height - RESIZE_BORDER;
        if (top && left) {
            return ResizeEdge.TOP_LEFT;
        }
        if (top && right) {
            return ResizeEdge.TOP_RIGHT;
        }
        if (bottom && left) {
            return ResizeEdge.BOTTOM_LEFT;
        }
        if (bottom && right) {
            return ResizeEdge.BOTTOM_RIGHT;
        }
        if (left) {
            return ResizeEdge.LEFT;
        }
        if (right) {
            return ResizeEdge.RIGHT;
        }
        if (top) {
            return ResizeEdge.TOP;
        }
        if (bottom) {
            return ResizeEdge.BOTTOM;
        }
        return ResizeEdge.NONE;
    }

    /**
     * Розраховує нові координати й розміри з урахуванням мінімальних розмірів Stage.
     *
     * @param deltaX горизонтальне переміщення курсора від початку зміни розміру.
     * @param deltaY вертикальне переміщення курсора від початку зміни розміру.
     */
    private void resizeWindow(double deltaX, double deltaY) {
        double width = initialWidth;
        double height = initialHeight;
        double x = initialX;
        double y = initialY;
        if (activeResizeEdge.hasLeft()) {
            width = Math.max(stage.getMinWidth(), initialWidth - deltaX);
            x = initialX + initialWidth - width;
        } else if (activeResizeEdge.hasRight()) {
            width = Math.max(stage.getMinWidth(), initialWidth + deltaX);
        }
        if (activeResizeEdge.hasTop()) {
            height = Math.max(stage.getMinHeight(), initialHeight - deltaY);
            y = initialY + initialHeight - height;
        } else if (activeResizeEdge.hasBottom()) {
            height = Math.max(stage.getMinHeight(), initialHeight + deltaY);
        }
        stage.setX(x);
        stage.setY(y);
        stage.setWidth(width);
        stage.setHeight(height);
    }

    /** Опис резервних зон зміни розміру та їхніх курсорів. */
    private enum ResizeEdge {
        /** Зміна розміру не активна. */
        NONE(Cursor.DEFAULT, false, false, false, false),
        /** Лівий край. */
        LEFT(Cursor.W_RESIZE, true, false, false, false),
        /** Правий край. */
        RIGHT(Cursor.E_RESIZE, false, true, false, false),
        /** Верхній край. */
        TOP(Cursor.N_RESIZE, false, false, true, false),
        /** Нижній край. */
        BOTTOM(Cursor.S_RESIZE, false, false, false, true),
        /** Верхній лівий кут. */
        TOP_LEFT(Cursor.NW_RESIZE, true, false, true, false),
        /** Верхній правий кут. */
        TOP_RIGHT(Cursor.NE_RESIZE, false, true, true, false),
        /** Нижній лівий кут. */
        BOTTOM_LEFT(Cursor.SW_RESIZE, true, false, false, true),
        /** Нижній правий кут. */
        BOTTOM_RIGHT(Cursor.SE_RESIZE, false, true, false, true);

        private final Cursor cursor;
        private final boolean left;
        private final boolean right;
        private final boolean top;
        private final boolean bottom;

        /**
         * Створює опис краю та відповідного курсора.
         *
         * @param cursor курсор resize.
         * @param left чи змінюється лівий край.
         * @param right чи змінюється правий край.
         * @param top чи змінюється верхній край.
         * @param bottom чи змінюється нижній край.
         */
        ResizeEdge(Cursor cursor, boolean left, boolean right, boolean top, boolean bottom) {
            this.cursor = cursor;
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }

        /** @return курсор, що відповідає зоні. */
        private Cursor cursor() {
            return cursor;
        }

        /** @return чи потрібно змінювати лівий край. */
        private boolean hasLeft() {
            return left;
        }

        /** @return чи потрібно змінювати правий край. */
        private boolean hasRight() {
            return right;
        }

        /** @return чи потрібно змінювати верхній край. */
        private boolean hasTop() {
            return top;
        }

        /** @return чи потрібно змінювати нижній край. */
        private boolean hasBottom() {
            return bottom;
        }
    }
}
