package com.example.neuronmap.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Малює координатну сітку робочої області редактора.
 */
public final class GridRenderer {

    private static final double GRID_SIZE = 40.0;
    private static final int MAX_LINES_PER_AXIS = 500;
    private static final Color GRID_COLOR = Color.web("#252a31");

    private final Canvas canvas;

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param canvas значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public GridRenderer(Canvas canvas) {
        this.canvas = canvas;
    }

    /**
     * Виконує операцію «відповідну операцію».
     *
     * @param width ширина області.
     *
     * @param height висота області.
     *
     * @param zoom значення, що визначає масштаб для цієї операції.
     *
     * @param panX значення, що визначає відповідну операцію для цієї операції.
     *
     * @param panY значення, що визначає відповідну операцію для цієї операції.
     */
    public void redraw(
            double width,
            double height,
            double zoom,
            double panX,
            double panY
    ) {
        if (width <= 0.0 || height <= 0.0 || zoom <= 0.0) {
            return;
        }

        canvas.setWidth(width);
        canvas.setHeight(height);

        GraphicsContext graphics =
                canvas.getGraphicsContext2D();

        graphics.clearRect(
                0.0,
                0.0,
                width,
                height
        );

        long firstColumn = indexAtWorld(-panX / zoom, true);
        long lastColumn = indexAtWorld((width - panX) / zoom, false);
        long firstRow = indexAtWorld(-panY / zoom, true);
        long lastRow = indexAtWorld((height - panY) / zoom, false);

        IndexRange columns = clampRange(
                firstColumn,
                lastColumn
        );

        IndexRange rows = clampRange(
                firstRow,
                lastRow
        );

        graphics.setStroke(GRID_COLOR);
        graphics.setLineWidth(1.0);

        for (long column = columns.start();
             column <= columns.end();
             column++) {

            double screenX =
                    column * GRID_SIZE * zoom + panX;

            if (screenX < -1.0 || screenX > width + 1.0) {
                continue;
            }

            graphics.strokeLine(
                    screenX,
                    0.0,
                    screenX,
                    height
            );
        }

        for (long row = rows.start();
             row <= rows.end();
             row++) {

            double screenY =
                    row * GRID_SIZE * zoom + panY;

            if (screenY < -1.0 || screenY > height + 1.0) {
                continue;
            }

            graphics.strokeLine(
                    0.0,
                    screenY,
                    width,
                    screenY
            );
        }
    }

    /**
     * Повертає результат операції «карта».
     *
     * @param worldCoordinate значення, що визначає карта координата для цієї операції.
     *
     * @param floor значення, що визначає відповідну операцію для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    private static long indexAtWorld(
            double worldCoordinate,
            boolean floor
    ) {
        double index = worldCoordinate / GRID_SIZE;
        return floor
                ? (long) Math.floor(index)
                : (long) Math.ceil(index);
    }

    /**
     * Повертає результат операції «обмежити».
     *
     * @param start значення, що визначає запуск для цієї операції.
     *
     * @param end значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static IndexRange clampRange(
            long start,
            long end
    ) {
        if (end - start <= MAX_LINES_PER_AXIS) {
            /**
             * Повертає результат операції «відповідну операцію».
             *
             * @param start значення, що визначає запуск для цієї операції.
             *
             * @param end значення, що визначає відповідну операцію для цієї операції.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            return new IndexRange(start, end);
        }

        long center = Math.round((start + end) / 2.0);
        long half = MAX_LINES_PER_AXIS / 2L;

        /**
         * Повертає результат операції «відповідну операцію».
         *
         * @param half значення, що визначає відповідну операцію для цієї операції.
         *
         * @param half значення, що визначає відповідну операцію для цієї операції.
         *
         * @return значення або обʼєкт, визначений описаною операцією.
         */
        return new IndexRange(
                center - half,
                center + half
        );
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param start значення, що визначає запуск для цієї операції.
     *
     * @param end значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    /**
     * Компонент IndexRange у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
     */
    private record IndexRange(long start, long end) {
    }
}
