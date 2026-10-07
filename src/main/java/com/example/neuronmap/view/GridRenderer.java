package com.example.neuronmap.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public final class GridRenderer {

    private static final double GRID_SIZE = 40.0;
    private static final int MAX_LINES_PER_AXIS = 500;
    private static final Color GRID_COLOR = Color.web("#252a31");

    private final Canvas canvas;

    public GridRenderer(Canvas canvas) {
        this.canvas = canvas;
    }

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

    private static long indexAtWorld(
            double worldCoordinate,
            boolean floor
    ) {
        double index = worldCoordinate / GRID_SIZE;
        return floor
                ? (long) Math.floor(index)
                : (long) Math.ceil(index);
    }

    private static IndexRange clampRange(
            long start,
            long end
    ) {
        if (end - start <= MAX_LINES_PER_AXIS) {
            return new IndexRange(start, end);
        }

        long center = Math.round((start + end) / 2.0);
        long half = MAX_LINES_PER_AXIS / 2L;

        return new IndexRange(
                center - half,
                center + half
        );
    }

    private record IndexRange(long start, long end) {
    }
}
