package com.example.neuronmap.view.simulation;

import com.example.neuronmap.view.connection.ConnectionPulseSnapshot;
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
 * Анімує імпульс уздовж зв'язку, оновлює геометрію траєкторії та повідомляє про завершення анімації.
 */
public final class PulseAnimationView extends Pane {

    private static final double TOTAL_MILLIS = 380.0;
    private static final double FADE_MILLIS = 180.0;
    private static final double ARROW_SIZE = 11.0;
    private static final double ARROW_HALF_WIDTH = 5.5;
    private static final double VISIBLE_OPACITY = 1.0;
    private static final double DIMMED_OPACITY = 0.2;

    private final Supplier<ConnectionPulseSnapshot> snapshotSupplier;
    private final Line line = new Line();
    private final Polygon arrow = new Polygon();
    private final Timeline timeline;
    private final AnimationTimer geometryUpdater;
    private final Runnable onFinished;
    private ConnectionPulseSnapshot lastSnapshot;
    private boolean finished;

    /**
     * Створює анімацію з обробником завершення за замовчуванням.
     *
     * @param initialSnapshot початкова геометрія й кольори імпульсу.
     * @param snapshotSupplier постачальник актуальної геометрії зв'язку.
     */
    public PulseAnimationView(
            ConnectionPulseSnapshot initialSnapshot,
            Supplier<ConnectionPulseSnapshot> snapshotSupplier
    ) {
        this(initialSnapshot, snapshotSupplier, () -> { });
    }

    /**
     * Створює анімацію імпульсу з переданим обробником завершення.
     *
     * @param initialSnapshot початкова геометрія й кольори імпульсу.
     * @param snapshotSupplier постачальник актуальної геометрії зв'язку.
     * @param onFinished callback після завершення або зупинки анімації.
     */
    public PulseAnimationView(
            ConnectionPulseSnapshot initialSnapshot,
            Supplier<ConnectionPulseSnapshot> snapshotSupplier,
            Runnable onFinished
    ) {
        lastSnapshot = Objects.requireNonNull(initialSnapshot, "initialSnapshot");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.onFinished = Objects.requireNonNull(onFinished, "onFinished");

        setManaged(false);
        setPickOnBounds(false);
        setMouseTransparent(true);
        line.getStyleClass().add("pulse-animation-line");
        arrow.getStyleClass().add("pulse-animation-arrow");
        applyGeometry(lastSnapshot.start(), lastSnapshot.end());
        line.setStroke(lastSnapshot.pulseColor());
        line.setMouseTransparent(true);
        arrow.setFill(lastSnapshot.pulseColor());
        arrow.setMouseTransparent(true);
        getChildren().addAll(line, arrow);

        timeline = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(line.strokeProperty(), lastSnapshot.pulseColor()),
                        new KeyValue(arrow.fillProperty(), lastSnapshot.pulseColor()),
                        new KeyValue(line.opacityProperty(), VISIBLE_OPACITY),
                        new KeyValue(arrow.opacityProperty(), VISIBLE_OPACITY)
                ),
                new KeyFrame(
                        Duration.millis(FADE_MILLIS),
                        new KeyValue(line.strokeProperty(), lastSnapshot.pulseColor()),
                        new KeyValue(arrow.fillProperty(), lastSnapshot.pulseColor()),
                        new KeyValue(line.opacityProperty(), DIMMED_OPACITY),
                        new KeyValue(arrow.opacityProperty(), DIMMED_OPACITY)
                ),
                new KeyFrame(
                        Duration.millis(TOTAL_MILLIS),
                        new KeyValue(line.strokeProperty(), lastSnapshot.normalColor()),
                        new KeyValue(arrow.fillProperty(), lastSnapshot.normalColor()),
                        new KeyValue(line.opacityProperty(), VISIBLE_OPACITY),
                        new KeyValue(arrow.opacityProperty(), VISIBLE_OPACITY)
                )
        );
        timeline.setOnFinished(event -> finish());
        geometryUpdater = new AnimationTimer() {
            /**
             * Оновлює геометрію імпульсу на кожному кадрі JavaFX.
             *
             * @param now час кадру у наносекундах.
             */
            @Override
            public void handle(long now) {
                refreshGeometry();
            }
        };
    }

    /**
     * Запускає анімацію вздовж поточної геометрії зв'язку.
     */
    public void play() {
        if (!finished) {
            geometryUpdater.start();
            timeline.play();
        }
    }

    /**
     * Призупиняє анімацію, зберігаючи її поточний прогрес.
     */
    public void pause() {
        if (!finished) {
            geometryUpdater.stop();
            timeline.pause();
        }
    }

    /**
     * Відновлює призупинену анімацію імпульсу.
     */
    public void resume() {
        if (!finished) {
            geometryUpdater.start();
            timeline.play();
        }
    }

    /**
     * Зупиняє анімацію та прибирає її візуальний елемент.
     */
    public void stop() {
        if (finished) {
            return;
        }
        timeline.stop();
        finish();
    }

    /**
     * Перевіряє, чи анімація завершена.
     *
     * @return {@code true}, якщо анімація більше не активна.
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Оновлює траєкторію імпульсу, якщо положення кінців зв'язку змінилося.
     */
    void refreshGeometry() {
        ConnectionPulseSnapshot currentSnapshot = snapshotSupplier.get();
        if (currentSnapshot == null) {
            return;
        }
        lastSnapshot = currentSnapshot;
        applyGeometry(currentSnapshot.start(), currentSnapshot.end());
    }

    /**
     * Встановлює точки лінії й стрілки за переданою геометрією зв'язку.
     *
     * @param start початкова точка.
     * @param end кінцева точка.
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
        Point2D perpendicular = new Point2D(-normalized.getY(), normalized.getX());
        Point2D tip = end;
        Point2D back = tip.subtract(normalized.multiply(ARROW_SIZE));
        Point2D left = back.add(perpendicular.multiply(ARROW_HALF_WIDTH));
        Point2D right = back.subtract(perpendicular.multiply(ARROW_HALF_WIDTH));
        arrow.getPoints().setAll(
                tip.getX(), tip.getY(),
                left.getX(), left.getY(),
                right.getX(), right.getY()
        );
    }

    /**
     * Завершує анімацію, прибирає графічний вузол і викликає обробник завершення.
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
