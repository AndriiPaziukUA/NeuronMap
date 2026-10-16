package com.example.neuronmap.window;

import java.util.Objects;

/**
 * Обчислює призначення точки у геометрії власного оформлення вікна без залежності від JavaFX та ОС.
 * Типи описують логічні області, а перетворення цих областей на коди конкретної платформи виконується адаптером.
 */
public final class WindowHitTestCalculator {

    /** Базова товщина інтерактивної смуги зміни розміру в логічних пікселях. */
    public static final double DEFAULT_RESIZE_BORDER = 6.0;

    /** Семантичні області власного заголовка й меж вікна. */
    public enum HitTarget {
        /** Звичайна клієнтська область. */
        CLIENT,
        /** Перетягування вікна за заголовок. */
        CAPTION,
        /** Кнопка згортання вікна. */
        MINIMIZE_BUTTON,
        /** Кнопка максимізації або відновлення. */
        MAXIMIZE_BUTTON,
        /** Кнопка закриття вікна. */
        CLOSE_BUTTON,
        /** Лівий край для зміни ширини. */
        RESIZE_LEFT,
        /** Правий край для зміни ширини. */
        RESIZE_RIGHT,
        /** Верхній край для зміни висоти. */
        RESIZE_TOP,
        /** Верхній лівий кут для зміни розміру. */
        RESIZE_TOP_LEFT,
        /** Верхній правий кут для зміни розміру. */
        RESIZE_TOP_RIGHT,
        /** Нижній край для зміни висоти. */
        RESIZE_BOTTOM,
        /** Нижній лівий кут для зміни розміру. */
        RESIZE_BOTTOM_LEFT,
        /** Нижній правий кут для зміни розміру. */
        RESIZE_BOTTOM_RIGHT;

        /**
         * Перевіряє, чи є область однією з інтерактивних кнопок заголовка.
         *
         * @return {@code true} для згортання, максимізації/відновлення або закриття.
         */
        public boolean isCaptionButton() {
            return this == MINIMIZE_BUTTON || this == MAXIMIZE_BUTTON || this == CLOSE_BUTTON;
        }
    }

    /** Прямокутник геометрії у вибраній системі координат. */
    public record Rectangle(double left, double top, double right, double bottom) {

        /**
         * Перевіряє коректність координат прямокутника.
         *
         * @throws IllegalArgumentException якщо координати не є скінченними або впорядкованими.
         */
        public Rectangle {
            if (!Double.isFinite(left) || !Double.isFinite(top)
                    || !Double.isFinite(right) || !Double.isFinite(bottom)
                    || right < left || bottom < top) {
                throw new IllegalArgumentException("rectangle coordinates must be finite and ordered");
            }
        }

        /**
         * Перевіряє, чи належить точка прямокутнику; правий і нижній краї не включаються.
         *
         * @param x горизонтальна координата точки.
         * @param y вертикальна координата точки.
         * @return {@code true}, якщо точка розміщена всередині прямокутника.
         */
        public boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    /** Незмінний знімок розмірів вікна й геометрії трьох кнопок заголовка. */
    public record Layout(
            double width,
            double height,
            double titleBarHeight,
            double resizeBorderX,
            double resizeBorderY,
            boolean maximized,
            Rectangle minimizeButton,
            Rectangle maximizeButton,
            Rectangle closeButton
    ) {

        /**
         * Перевіряє знімок геометрії перед використанням у платформному обробнику подій.
         *
         * @throws IllegalArgumentException якщо розміри або відступи некоректні.
         * @throws NullPointerException якщо не задано геометрію будь-якої кнопки.
         */
        public Layout {
            if (!Double.isFinite(width) || width <= 0.0
                    || !Double.isFinite(height) || height <= 0.0
                    || !Double.isFinite(titleBarHeight) || titleBarHeight < 0.0
                    || titleBarHeight > height
                    || !Double.isFinite(resizeBorderX) || resizeBorderX < 0.0
                    || !Double.isFinite(resizeBorderY) || resizeBorderY < 0.0) {
                throw new IllegalArgumentException("window layout contains invalid dimensions");
            }
            Objects.requireNonNull(minimizeButton, "minimizeButton");
            Objects.requireNonNull(maximizeButton, "maximizeButton");
            Objects.requireNonNull(closeButton, "closeButton");
        }
    }

    /** Створює безстанний обчислювач областей вікна. */
    private WindowHitTestCalculator() {
    }

    /**
     * Визначає інтерактивну область за координатами точки відносно вікна, включно з внутрішніми зонами resize.
     * Кнопки заголовка мають пріоритет над зонами resize, а resize — над заголовком і клієнтським вмістом.
     *
     * @param layout актуальний незмінний знімок геометрії.
     * @param x горизонтальна координата точки в логічних пікселях.
     * @param y вертикальна координата точки в логічних пікселях.
     * @return семантична область точки.
     * @throws NullPointerException якщо геометрію не задано.
     */
    public static HitTarget hitTest(Layout layout, double x, double y) {
        return hitTest(layout, x, y, true);
    }

    /**
     * Визначає область усередині клієнтського вмісту, не резервуючи внутрішню смугу під зміну розміру.
     * Цей варіант підходить нативним рамкам, де resize виконується окремою зовнішньою областю ОС.
     *
     * @param layout актуальний незмінний знімок геометрії.
     * @param x горизонтальна координата точки в логічних пікселях.
     * @param y вертикальна координата точки в логічних пікселях.
     * @return семантична область кнопки, заголовка або клієнтського вмісту.
     * @throws NullPointerException якщо геометрію не задано.
     */
    public static HitTarget hitTestClientArea(Layout layout, double x, double y) {
        return hitTest(layout, x, y, false);
    }

    /**
     * Визначає resize-область між зовнішнім прямокутником HWND і видимими межами DWM.
     * Прямокутники й точка мають бути задані в одній системі екранних координат.
     *
     * @param windowBounds зовнішній прямокутник HWND, отриманий через {@code GetWindowRect}.
     * @param visibleBounds видимий прямокутник рамки, отриманий через DWM.
     * @param screenX горизонтальна екранна координата курсора.
     * @param screenY вертикальна екранна координата курсора.
     * @param maximized чи максимізоване вікно.
     * @return системний край або кут; {@code null}, якщо точка не належить зовнішній resize-області.
     * @throws NullPointerException якщо один із прямокутників не задано.
     */
    public static HitTarget hitTestOuterResizeArea(
            Rectangle windowBounds,
            Rectangle visibleBounds,
            double screenX,
            double screenY,
            boolean maximized
    ) {
        Objects.requireNonNull(windowBounds, "windowBounds");
        Objects.requireNonNull(visibleBounds, "visibleBounds");
        if (maximized || !Double.isFinite(screenX) || !Double.isFinite(screenY)
                || !windowBounds.contains(screenX, screenY)
                || visibleBounds.contains(screenX, screenY)) {
            return null;
        }

        boolean left = screenX < visibleBounds.left() && windowBounds.left() < visibleBounds.left();
        boolean right = screenX >= visibleBounds.right() && windowBounds.right() > visibleBounds.right();
        boolean top = screenY < visibleBounds.top() && windowBounds.top() < visibleBounds.top();
        boolean bottom = screenY >= visibleBounds.bottom() && windowBounds.bottom() > visibleBounds.bottom();

        if (top && left) {
            return HitTarget.RESIZE_TOP_LEFT;
        }
        if (top && right) {
            return HitTarget.RESIZE_TOP_RIGHT;
        }
        if (bottom && left) {
            return HitTarget.RESIZE_BOTTOM_LEFT;
        }
        if (bottom && right) {
            return HitTarget.RESIZE_BOTTOM_RIGHT;
        }
        if (left) {
            return HitTarget.RESIZE_LEFT;
        }
        if (right) {
            return HitTarget.RESIZE_RIGHT;
        }
        if (top) {
            return HitTarget.RESIZE_TOP;
        }
        if (bottom) {
            return HitTarget.RESIZE_BOTTOM;
        }
        return null;
    }

    /**
     * Визначає область точки з можливістю вимкнути resize-пороги всередині вікна.
     *
     * @param layout геометрія вікна.
     * @param x горизонтальна координата точки.
     * @param y вертикальна координата точки.
     * @param includeClientResize чи потрібно вважати внутрішні краї клієнтської області зонами resize.
     * @return семантична область точки.
     */
    private static HitTarget hitTest(Layout layout, double x, double y, boolean includeClientResize) {
        Objects.requireNonNull(layout, "layout");
        if (!Double.isFinite(x) || !Double.isFinite(y)
                || x < 0.0 || y < 0.0 || x >= layout.width() || y >= layout.height()) {
            return HitTarget.CLIENT;
        }

        if (layout.minimizeButton().contains(x, y)) {
            return HitTarget.MINIMIZE_BUTTON;
        }
        if (layout.maximizeButton().contains(x, y)) {
            return HitTarget.MAXIMIZE_BUTTON;
        }
        if (layout.closeButton().contains(x, y)) {
            return HitTarget.CLOSE_BUTTON;
        }

        if (includeClientResize && !layout.maximized()) {
            HitTarget edge = resizeTarget(layout, x, y);
            if (edge != null) {
                return edge;
            }
        }

        if (y < layout.titleBarHeight()) {
            return HitTarget.CAPTION;
        }
        return HitTarget.CLIENT;
    }

    /**
     * Визначає інтерактивний край або кут звичайного вікна.
     *
     * @param layout геометрія вікна.
     * @param x горизонтальна координата точки.
     * @param y вертикальна координата точки.
     * @return край або {@code null}, якщо точка не належить зоні зміни розміру.
     */
    private static HitTarget resizeTarget(Layout layout, double x, double y) {
        double borderX = Math.min(layout.resizeBorderX(), layout.width() / 2.0);
        double borderY = Math.min(layout.resizeBorderY(), layout.height() / 2.0);
        boolean left = x < borderX;
        boolean right = x >= layout.width() - borderX;
        boolean top = y < borderY;
        boolean bottom = y >= layout.height() - borderY;

        if (top && left) {
            return HitTarget.RESIZE_TOP_LEFT;
        }
        if (top && right) {
            return HitTarget.RESIZE_TOP_RIGHT;
        }
        if (bottom && left) {
            return HitTarget.RESIZE_BOTTOM_LEFT;
        }
        if (bottom && right) {
            return HitTarget.RESIZE_BOTTOM_RIGHT;
        }
        if (left) {
            return HitTarget.RESIZE_LEFT;
        }
        if (right) {
            return HitTarget.RESIZE_RIGHT;
        }
        if (top) {
            return HitTarget.RESIZE_TOP;
        }
        if (bottom) {
            return HitTarget.RESIZE_BOTTOM;
        }
        return null;
    }
}
