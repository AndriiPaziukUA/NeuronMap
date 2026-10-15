package com.example.neuronmap.windowing;

import java.util.Objects;

/**
 * Обчислює роль точки в безрамковому вікні незалежно від JavaFX і Win32.
 * Окремий розрахунок дозволяє тестувати правила заголовка та країв і повторно використовувати їх іншими платформними адаптерами.
 */
public final class WindowHitTestCalculator {

    /** Базова товщина інтерактивної смуги зміни розміру в логічних пікселях JavaFX. */
    public static final double DEFAULT_RESIZE_BORDER = 6.0;

    /** Види нативних областей, які повертає перевірка наведення. */
    public enum HitTarget {
        /** Звичайна клієнтська область. */
        CLIENT(1),
        /** Перетягування вікна за заголовок. */
        CAPTION(2),
        /** Кнопка згортання вікна. */
        MINIMIZE_BUTTON(8),
        /** Кнопка максимізації або відновлення; підтримує системне меню Snap Layouts. */
        MAXIMIZE_BUTTON(9),
        /** Кнопка закриття вікна. */
        CLOSE_BUTTON(20),
        /** Лівий край для зміни ширини. */
        RESIZE_LEFT(10),
        /** Правий край для зміни ширини. */
        RESIZE_RIGHT(11),
        /** Верхній край для зміни висоти. */
        RESIZE_TOP(12),
        /** Верхній лівий кут для зміни розміру. */
        RESIZE_TOP_LEFT(13),
        /** Верхній правий кут для зміни розміру. */
        RESIZE_TOP_RIGHT(14),
        /** Нижній край для зміни висоти. */
        RESIZE_BOTTOM(15),
        /** Нижній лівий кут для зміни розміру. */
        RESIZE_BOTTOM_LEFT(16),
        /** Нижній правий кут для зміни розміру. */
        RESIZE_BOTTOM_RIGHT(17);

        private final int nativeCode;

        /**
         * Створює відповідність між логічною областю та кодом Win32.
         *
         * @param nativeCode код області для повідомлення {@code WM_NCHITTEST}.
         */
        HitTarget(int nativeCode) {
            this.nativeCode = nativeCode;
        }

        /**
         * Повертає код Win32 для результату {@code WM_NCHITTEST}.
         *
         * @return нативний hit-test-код.
         */
        public int nativeCode() {
            return nativeCode;
        }

        /**
         * Перевіряє, чи відповідає область одній із трьох кнопок заголовка.
         *
         * @return {@code true} для кнопки згортання, максимізації/відновлення або закриття.
         */
        public boolean isCaptionButton() {
            return this == MINIMIZE_BUTTON || this == MAXIMIZE_BUTTON || this == CLOSE_BUTTON;
        }
    }

    /** Прямокутник області заголовка у координатах сцени. */
    public record Rectangle(double left, double top, double right, double bottom) {

        /**
         * Перевіряє коректність координат прямокутника.
         *
         * @throws IllegalArgumentException якщо координати не є скінченними або утворюють порожній прямокутник.
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
         * @return {@code true}, якщо точка лежить усередині прямокутника.
         */
        public boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    /** Геометрія вікна та інтерактивних кнопок, потрібна нативному callback-у. */
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
         * Перевіряє геометрію, перш ніж її використовуватиме обробник Win32.
         *
         * @throws IllegalArgumentException якщо розміри невалідні.
         * @throws NullPointerException якщо не передано геометрію однієї з кнопок.
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

    /** Створює безстанний обчислювач нативних областей вікна. */
    private WindowHitTestCalculator() {
    }

    /**
     * Визначає, що має відбуватися під курсором: системне перетягування, команда заголовка, зміна розміру чи вміст.
     * Кнопки перевіряються перед клієнтською областю, а краї вікна мають пріоритет над неактивною частиною заголовка.
     *
     * @param layout актуальний незмінний знімок геометрії вікна.
     * @param x горизонтальна координата точки відносно вікна в логічних пікселях.
     * @param y вертикальна координата точки відносно вікна в логічних пікселях.
     * @return відповідна нативна інтерактивна область.
     * @throws NullPointerException якщо геометрію не задано.
     */
    public static HitTarget hitTest(Layout layout, double x, double y) {
        Objects.requireNonNull(layout, "layout");
        if (!Double.isFinite(x) || !Double.isFinite(y)
                || x < 0.0 || y < 0.0 || x >= layout.width() || y >= layout.height()) {
            return HitTarget.CLIENT;
        }

        if (!layout.maximized()) {
            HitTarget edge = resizeTarget(layout, x, y);
            if (edge != null) {
                return edge;
            }
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
        if (y < layout.titleBarHeight()) {
            return HitTarget.CAPTION;
        }
        return HitTarget.CLIENT;
    }

    /**
     * Визначає код зміни розміру для точки біля країв звичайного вікна.
     *
     * @param layout геометрія вікна.
     * @param x горизонтальна координата.
     * @param y вертикальна координата.
     * @return відповідний край або {@code null}, якщо точка не є зоною зміни розміру.
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
