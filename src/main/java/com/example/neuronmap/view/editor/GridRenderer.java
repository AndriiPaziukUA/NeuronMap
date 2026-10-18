package com.example.neuronmap.view.editor;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Objects;

/**
 * Малює координатну сітку полотна з урахуванням масштабу й зміщення камери.
 */
public final class GridRenderer {

    private final Canvas canvas;
    private final GridStyle style;

    /**
     * Створює рендерер зі стандартним незалежним стилем координатної сітки.
     *
     * @param canvas полотно JavaFX, на якому малюється сітка.
     */
    public GridRenderer(Canvas canvas) {
        this(canvas, GridStyle.DEFAULT);
    }

    /**
     * Створює рендерер зі стилем, який належить лише координатній сітці.
     *
     * @param canvas полотно JavaFX, на якому малюється сітка.
     * @param style незмінний опис геометрії та кольору сітки.
     * @throws NullPointerException якщо полотно або стиль не задано.
     */
    public GridRenderer(Canvas canvas, GridStyle style) {
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.style = Objects.requireNonNull(style, "style");
    }

    /**
     * Перемальовує видиму частину координатної сітки з урахуванням розміру полотна й параметрів камери.
     *
     * @param width ширина видимої області.
     * @param height висота видимої області.
     * @param zoom коефіцієнт масштабування.
     * @param panX горизонтальне зміщення камери.
     * @param panY вертикальне зміщення камери.
     */
    public void redraw(double width, double height, double zoom, double panX, double panY) {
        if (width <= 0.0 || height <= 0.0 || zoom <= 0.0) {
            return;
        }

        canvas.setWidth(width);
        canvas.setHeight(height);
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.clearRect(0.0, 0.0, width, height);

        long firstColumn = indexAtWorld(-panX / zoom, true, style.gridSize());
        long lastColumn = indexAtWorld((width - panX) / zoom, false, style.gridSize());
        long firstRow = indexAtWorld(-panY / zoom, true, style.gridSize());
        long lastRow = indexAtWorld((height - panY) / zoom, false, style.gridSize());

        IndexRange columns = clampRange(firstColumn, lastColumn, style.maxLinesPerAxis());
        IndexRange rows = clampRange(firstRow, lastRow, style.maxLinesPerAxis());

        graphics.setStroke(style.color());
        graphics.setLineWidth(1.0);

        for (long column = columns.start(); column <= columns.end(); column++) {
            double screenX = column * style.gridSize() * zoom + panX;
            if (screenX < -1.0 || screenX > width + 1.0) {
                continue;
            }
            graphics.strokeLine(screenX, 0.0, screenX, height);
        }

        for (long row = rows.start(); row <= rows.end(); row++) {
            double screenY = row * style.gridSize() * zoom + panY;
            if (screenY < -1.0 || screenY > height + 1.0) {
                continue;
            }
            graphics.strokeLine(0.0, screenY, width, screenY);
        }
    }

    /**
     * Перетворює світову координату на індекс лінії сітки з урахуванням потрібного напряму округлення.
     *
     * @param worldCoordinate світова координата.
     * @param floor якщо {@code true}, округлює вниз; інакше округлює вгору.
     * @param gridSize відстань між сусідніми лініями у світових координатах.
     * @return індекс лінії сітки.
     */
    private static long indexAtWorld(double worldCoordinate, boolean floor, double gridSize) {
        double index = worldCoordinate / gridSize;
        return floor ? (long) Math.floor(index) : (long) Math.ceil(index);
    }

    /**
     * Обмежує кількість індексів у діапазоні, зберігаючи його центральну частину.
     *
     * @param start перший індекс видимого діапазону.
     * @param end останній індекс видимого діапазону.
     * @param maxLines максимальна кількість ліній на одній осі.
     * @return обмежений включний діапазон індексів.
     */
    private static IndexRange clampRange(long start, long end, int maxLines) {
        if (end - start <= maxLines) {
            return new IndexRange(start, end);
        }
        long center = Math.round((start + end) / 2.0);
        long half = maxLines / 2L;
        return new IndexRange(center - half, center + half);
    }

    /**
     * Незмінні параметри вигляду координатної сітки без зв'язку з іншими візуальними компонентами.
     */
    public record GridStyle(double gridSize, int maxLinesPerAxis, Color color) {
        /**
         * Стандартний опис стилю, який використовується сумісним конструктором рендерера.
         */
        public static final GridStyle DEFAULT = new GridStyle(40.0, 500, Color.web("#252a31"));

        /**
         * Перевіряє, що геометрія сітки має коректні додатні параметри, а колір визначено.
         *
         * @param gridSize відстань між лініями сітки у світових координатах.
         * @param maxLinesPerAxis обмеження кількості ліній на кожній осі.
         * @param color колір ліній сітки.
         * @throws IllegalArgumentException якщо геометричні параметри не додатні.
         * @throws NullPointerException якщо колір не задано.
         */
        public GridStyle {
            if (!Double.isFinite(gridSize) || gridSize <= 0.0) {
                throw new IllegalArgumentException("gridSize must be finite and positive");
            }
            if (maxLinesPerAxis < 1) {
                throw new IllegalArgumentException("maxLinesPerAxis must be positive");
            }
            Objects.requireNonNull(color, "color");
        }
    }

    /**
     * Описує включний діапазон індексів ліній, які потрібно перемалювати.
     */
    private record IndexRange(long start, long end) {
    }
}
