package com.example.neuronmap.view;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Відображає рухомий імпульс уздовж звʼязку між нейронами.
 */
public final class PulseAnimationView extends Pane {

    private static final double TOTAL_MILLIS = 380.0;
    private static final double ARROW_SIZE = 11.0;
    private static final double ARROW_HALF_WIDTH = 5.5;

    private final Supplier<ConnectionPulseSnapshot> snapshotSupplier;
    private final Line line = new Line();
    private final Polygon arrow = new Polygon();
    private final Timeline timeline;
    private final AnimationTimer geometryUpdater;
    private final Runnable onFinished;
    private ConnectionPulseSnapshot lastSnapshot;
    private boolean finished;

    /**
     * Повертає результат операції «імпульс анімація відображення».
     *
     * @param snapshot знімок стану.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public PulseAnimationView(ConnectionPulseSnapshot snapshot) {
        this(snapshot, () -> snapshot, () -> { });
    }

    /**
     * Повертає результат операції «імпульс анімація відображення».
     *
     * @param snapshot знімок стану.
     *
     * @param onFinished значення, що визначає завершений для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public PulseAnimationView(
            ConnectionPulseSnapshot snapshot,
            Runnable onFinished
    ) {
        this(snapshot, () -> snapshot, onFinished);
    }

    /**
     * Повертає результат операції «імпульс анімація відображення».
     *
     * @param initialSnapshot значення, що визначає знімок для цієї операції.
     *
     * @param snapshotSupplier значення, що визначає знімок для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public PulseAnimationView(
            ConnectionPulseSnapshot initialSnapshot,
            Supplier<ConnectionPulseSnapshot> snapshotSupplier
    ) {
        this(initialSnapshot, snapshotSupplier, () -> { });
    }

    /**
     * Повертає результат операції «імпульс анімація відображення».
     *
     * @param initialSnapshot значення, що визначає знімок для цієї операції.
     *
     * @param snapshotSupplier значення, що визначає знімок для цієї операції.
     *
     * @param onFinished значення, що визначає завершений для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public PulseAnimationView(
            ConnectionPulseSnapshot initialSnapshot,
            Supplier<ConnectionPulseSnapshot> snapshotSupplier,
            Runnable onFinished
    ) {
        this.lastSnapshot = Objects.requireNonNull(
                initialSnapshot,
                "initialSnapshot"
        );
        this.snapshotSupplier = Objects.requireNonNull(
                snapshotSupplier,
                "snapshotSupplier"
        );
        this.onFinished = Objects.requireNonNull(
                onFinished,
                "onFinished"
        );

        setManaged(false);
        setPickOnBounds(false);
        setMouseTransparent(true);

        applyGeometry(lastSnapshot.start(), lastSnapshot.end());
        line.setStroke(lastSnapshot.pulseColor());
        line.setStrokeWidth(2.3);
        line.setMouseTransparent(true);

        arrow.setFill(lastSnapshot.pulseColor());
        arrow.setMouseTransparent(true);

        getChildren().addAll(line, arrow);

        timeline = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(line.strokeProperty(), lastSnapshot.pulseColor()),
                        new KeyValue(arrow.fillProperty(), lastSnapshot.pulseColor()),
                        new KeyValue(line.opacityProperty(), 1.0),
                        new KeyValue(arrow.opacityProperty(), 1.0)
                ),
                new KeyFrame(
                        Duration.millis(180.0),
                        new KeyValue(line.strokeProperty(), lastSnapshot.pulseColor()),
                        new KeyValue(arrow.fillProperty(), lastSnapshot.pulseColor()),
                        new KeyValue(line.opacityProperty(), 0.2),
                        new KeyValue(arrow.opacityProperty(), 0.2)
                ),
                new KeyFrame(
                        Duration.millis(TOTAL_MILLIS),
                        new KeyValue(line.strokeProperty(), lastSnapshot.normalColor()),
                        new KeyValue(arrow.fillProperty(), lastSnapshot.normalColor()),
                        new KeyValue(line.opacityProperty(), 1.0),
                        new KeyValue(arrow.opacityProperty(), 1.0)
                )
        );

        timeline.setOnFinished(event -> finish());

        geometryUpdater = new AnimationTimer() {
            /**
             * Обробляє «потрібні дані».
             *
             * @param now значення, що визначає відповідну операцію для цієї операції.
             */
            @Override
            public void handle(long now) {
                refreshGeometry();
            }
        };
    }

    /**
     * Виконує операцію «відповідну операцію».
     */
    public void play() {
        if (!finished) {
            geometryUpdater.start();
            timeline.play();
        }
    }

    /**
     * Виконує операцію «відповідну операцію».
     */
    public void pause() {
        if (!finished) {
            geometryUpdater.stop();
            timeline.pause();
        }
    }

    /**
     * Виконує операцію «відповідну операцію».
     */
    public void resume() {
        if (!finished) {
            geometryUpdater.start();
            timeline.play();
        }
    }

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    public void stop() {
        if (finished) {
            return;
        }
        timeline.stop();
        finish();
    }

    /**
     * Перевіряє, чи виконується умова «завершений».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isFinished() {
        return finished;
    }

/**
 * Обробляє «геометрія».
 */
void refreshGeometry() {
        ConnectionPulseSnapshot currentSnapshot = snapshotSupplier.get();
        if (currentSnapshot == null) {
            return;
        }

        lastSnapshot = currentSnapshot;
        applyGeometry(
                currentSnapshot.start(),
                currentSnapshot.end()
        );
    }

    /**
     * Обробляє «геометрія».
     *
     * @param start значення, що визначає запуск для цієї операції.
     *
     * @param end значення, що визначає відповідну операцію для цієї операції.
     */
    private void applyGeometry(Point2D start, Point2D end) {
        line.setStartX(start.getX());
        line.setStartY(start.getY());
        line.setEndX(end.getX());
        line.setEndY(end.getY());

        Point2D direction = end.subtract(start);
        if (direction.magnitude() == 0.0) {
            arrow.getPoints().clear();
            return;
        }

        Point2D normalized = direction.normalize();
        Point2D perpendicular = new Point2D(
                -normalized.getY(),
                normalized.getX()
        );
        Point2D tip = end;
        Point2D back = tip.subtract(
                normalized.multiply(ARROW_SIZE)
        );
        Point2D left = back.add(
                perpendicular.multiply(ARROW_HALF_WIDTH)
        );
        Point2D right = back.subtract(
                perpendicular.multiply(ARROW_HALF_WIDTH)
        );

        arrow.getPoints().setAll(
                tip.getX(), tip.getY(),
                left.getX(), left.getY(),
                right.getX(), right.getY()
        );
    }

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    private void finish() {
        if (finished) {
            return;
        }

        finished = true;
        geometryUpdater.stop();

        Pane parent = getParent() instanceof Pane pane ? pane : null;
        if (parent != null) {
            parent.getChildren().remove(this);
        }

        onFinished.run();
    }
}
