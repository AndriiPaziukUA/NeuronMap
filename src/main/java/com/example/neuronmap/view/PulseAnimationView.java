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
 * Анімує імпульс уздовж зв’язку, оновлює геометрію траєкторії та повідомляє про завершення анімації.
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
     * Створює екземпляр PulseAnimationView та зберігає передані залежності, потрібні для його роботи.
     *
     * @param snapshot значення «snapshot», яке використовується в цьому методі.
     */
    public PulseAnimationView(ConnectionPulseSnapshot snapshot) {
        this(snapshot, () -> snapshot, () -> { });
    }

    /**
     * Створює екземпляр PulseAnimationView та зберігає передані залежності, потрібні для його роботи.
     *
     * @param snapshot значення «snapshot», яке використовується в цьому методі.
     * @param onFinished callback, який викликається після завершення анімації.
     */
    public PulseAnimationView(
            ConnectionPulseSnapshot snapshot,
            Runnable onFinished
    ) {
        this(snapshot, () -> snapshot, onFinished);
    }

    /**
     * Створює екземпляр PulseAnimationView та зберігає передані залежності, потрібні для його роботи.
     *
     * @param initialSnapshot значення «initial snapshot», яке використовується в цьому методі.
     * @param snapshotSupplier функція, яка повертає актуальну геометрію зв’язку.
     */
    public PulseAnimationView(
            ConnectionPulseSnapshot initialSnapshot,
            Supplier<ConnectionPulseSnapshot> snapshotSupplier
    ) {
        this(initialSnapshot, snapshotSupplier, () -> { });
    }

    /**
     * Створює екземпляр PulseAnimationView та зберігає передані залежності, потрібні для його роботи.
     *
     * @param initialSnapshot значення «initial snapshot», яке використовується в цьому методі.
     * @param snapshotSupplier функція, яка повертає актуальну геометрію зв’язку.
     * @param onFinished callback, який викликається після завершення анімації.
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

            @Override
            public void handle(long now) {
                refreshGeometry();
            }
        };
    }

    /**
     * Запускає анімацію імпульсу вздовж поточної геометрії зв’язку.
     */
    public void play() {
        if (!finished) {
            geometryUpdater.start();
            timeline.play();
        }
    }

    /**
     * Призупиняє рух імпульсу, зберігаючи поточний прогрес анімації.
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
     * Перевіряє, чи finished за поточного стану компонента.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean isFinished() {
        return finished;
    }

/**
 * Оновлює траєкторію імпульсу, якщо положення кінців зв’язку змінилося.
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
     * Установлює початкову й кінцеву точки лінії імпульсу за геометрією знімка зв’язку.
     *
     * @param start початкова точка зв’язку у координатах сцени.
     * @param end кінцева точка зв’язку у координатах сцени.
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
     * Завершує анімацію та викликає переданий обробник завершення.
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
