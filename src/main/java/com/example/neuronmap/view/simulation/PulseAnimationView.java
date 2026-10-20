package com.example.neuronmap.view.simulation;

import com.example.neuronmap.view.connection.ConnectionPulseSnapshot;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Анімує короткий імпульс уздовж зв'язку незалежно від життєвого циклу самого подання зв'язку.
 * Тривалість руху відповідає тривалості поточного такту симуляції.
 */
public final class PulseAnimationView extends Pane {

    /** Тривалість для використання подання окремо від симуляції. */
    public static final double DEFAULT_TICK_DURATION_MILLIS = 380.0;

    private static final double MIDPOINT_MILLIS = 180.0;
    private static final double MAX_PULSE_LENGTH = 32.0;
    private static final double PULSE_LENGTH_RATIO = 0.22;
    private static final double ARROW_SIZE = 11.0;
    private static final double ARROW_HALF_WIDTH = 5.5;
    private static final double VISIBLE_OPACITY = 1.0;
    private static final double HIDDEN_OPACITY = 0.0;

    private final Supplier<ConnectionPulseSnapshot> snapshotSupplier;
    private final Line line = new Line();
    private final Polygon arrow = new Polygon();
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
    private final AnimationTimer geometryUpdater;
    private final Runnable onFinished;

    private Timeline timeline;
    private ConnectionPulseSnapshot lastSnapshot;
    private double tickDurationMillis;
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
        this(initialSnapshot, snapshotSupplier, DEFAULT_TICK_DURATION_MILLIS, () -> { });
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
        this(initialSnapshot, snapshotSupplier, DEFAULT_TICK_DURATION_MILLIS, onFinished);
    }

    /**
     * Створює анімацію імпульсу, тривалість якої відповідає заданому такту симуляції.
     *
     * @param initialSnapshot початкова геометрія й кольори імпульсу.
     * @param snapshotSupplier постачальник актуальної геометрії зв'язку.
     * @param tickDurationMillis тривалість такту в мілісекундах.
     * @param onFinished callback після завершення або зупинки анімації.
     * @throws NullPointerException якщо обов'язкову залежність не задано.
     * @throws IllegalArgumentException якщо тривалість такту не є скінченним додатним числом або не дає коректної швидкості.
     */
    public PulseAnimationView(
            ConnectionPulseSnapshot initialSnapshot,
            Supplier<ConnectionPulseSnapshot> snapshotSupplier,
            double tickDurationMillis,
            Runnable onFinished
    ) {
        lastSnapshot = Objects.requireNonNull(initialSnapshot, "initialSnapshot");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.onFinished = Objects.requireNonNull(onFinished, "onFinished");
        this.tickDurationMillis = requireTickDurationMillis(tickDurationMillis);

        setManaged(false);
        setPickOnBounds(false);
        setMouseTransparent(true);
        line.getStyleClass().add("pulse-animation-line");
        arrow.getStyleClass().add("pulse-animation-arrow");
        line.setStroke(lastSnapshot.pulseColor());
        line.setMouseTransparent(true);
        arrow.setFill(lastSnapshot.pulseColor());
        arrow.setMouseTransparent(true);
        getChildren().addAll(line, arrow);
        progress.addListener((observable, oldValue, newValue) -> updatePulsePosition(newValue.doubleValue()));
        updatePulsePosition(0.0);
        timeline = createTimeline();
        timeline.setRate(animationRate(tickDurationMillis));

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

    /** Запускає анімацію уздовж поточної геометрії зв'язку. */
    public void play() {
        if (!finished) {
            geometryUpdater.start();
            timeline.play();
        }
    }

    /** Призупиняє анімацію, зберігаючи її поточний прогрес. */
    public void pause() {
        if (!finished) {
            geometryUpdater.stop();
            timeline.pause();
        }
    }

    /** Відновлює призупинену анімацію імпульсу. */
    public void resume() {
        if (!finished) {
            geometryUpdater.start();
            timeline.play();
        }
    }

    /** Зупиняє анімацію та прибирає її візуальний елемент. */
    public void stop() {
        if (finished) {
            return;
        }
        timeline.stop();
        finish();
    }

    /**
     * Змінює тривалість анімації, не скидаючи вже пройдений шлях імпульсу.
     * Якщо анімація виконувалася або була призупинена, її стан зберігається.
     *
     * @param millis нова тривалість такту в мілісекундах.
     * @throws IllegalArgumentException якщо значення не є скінченним додатним числом або не дає коректної швидкості.
     */
    public void setTickDurationMillis(double millis) {
        double validatedMillis = requireTickDurationMillis(millis);
        double rate = animationRate(validatedMillis);
        if (Double.compare(tickDurationMillis, validatedMillis) == 0) {
            return;
        }
        tickDurationMillis = validatedMillis;
        if (!finished) {
            timeline.setRate(rate);
        }
    }

    /**
     * Повертає тривалість, до якої узгоджується рух імпульсу.
     *
     * @return поточна тривалість такту в мілісекундах.
     */
    public double getTickDurationMillis() {
        return tickDurationMillis;
    }

    /**
     * Перевіряє, чи анімація завершена.
     *
     * @return {@code true}, якщо анімація більше не активна.
     */
    public boolean isFinished() {
        return finished;
    }

    /** Оновлює траєкторію імпульсу, якщо положення кінців зв'язку змінилося. */
    void refreshGeometry() {
        ConnectionPulseSnapshot currentSnapshot = snapshotSupplier.get();
        if (currentSnapshot == null) {
            return;
        }
        lastSnapshot = currentSnapshot;
        updatePulsePosition(progress.get());
    }

    /**
     * Розташовує короткий імпульс на відповідній частині траєкторії зв'язку.
     *
     * @param value частка пройденого шляху в діапазоні від нуля до одиниці.
     */
    void updatePulsePosition(double value) {
        Point2D start = lastSnapshot.start();
        Point2D end = lastSnapshot.end();
        Point2D direction = end.subtract(start);
        double length = direction.magnitude();
        if (length == 0.0) {
            applyGeometry(start, start);
            return;
        }

        double fraction = Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
        Point2D unitDirection = direction.normalize();
        double pulseLength = Math.min(MAX_PULSE_LENGTH, length * PULSE_LENGTH_RATIO);
        double travelDistance = (length - pulseLength) * fraction;
        Point2D pulseStart = start.add(unitDirection.multiply(travelDistance));
        Point2D pulseEnd = pulseStart.add(unitDirection.multiply(pulseLength));
        applyGeometry(pulseStart, pulseEnd);
    }

    /**
     * Створює часову шкалу руху імпульсу зі збереженням наявного ритму та згасання.
     *
     * @return часова шкала руху, зміни кольору й прозорості імпульсу.
     */
    private Timeline createTimeline() {
        Timeline result = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(progress, 0.0),
                        new KeyValue(line.strokeProperty(), lastSnapshot.pulseColor()),
                        new KeyValue(arrow.fillProperty(), lastSnapshot.pulseColor()),
                        new KeyValue(line.opacityProperty(), VISIBLE_OPACITY),
                        new KeyValue(arrow.opacityProperty(), VISIBLE_OPACITY)
                ),
                new KeyFrame(
                        Duration.millis(MIDPOINT_MILLIS),
                        new KeyValue(progress, 0.5),
                        new KeyValue(line.opacityProperty(), VISIBLE_OPACITY),
                        new KeyValue(arrow.opacityProperty(), VISIBLE_OPACITY)
                ),
                new KeyFrame(
                        Duration.millis(DEFAULT_TICK_DURATION_MILLIS),
                        new KeyValue(progress, 1.0),
                        new KeyValue(line.strokeProperty(), lastSnapshot.normalColor()),
                        new KeyValue(arrow.fillProperty(), lastSnapshot.normalColor()),
                        new KeyValue(line.opacityProperty(), HIDDEN_OPACITY),
                        new KeyValue(arrow.opacityProperty(), HIDDEN_OPACITY)
                )
        );
        result.setOnFinished(event -> finish());
        return result;
    }

    /**
     * Обчислює коефіцієнт відтворення так, щоб рух імпульсу тривав рівно один такт.
     *
     * @param millis тривалість такту в мілісекундах.
     * @return коефіцієнт швидкості часової шкали.
     */
    private static double animationRate(double millis) {
        double rate = DEFAULT_TICK_DURATION_MILLIS / millis;
        if (!Double.isFinite(rate) || rate <= 0.0) {
            throw new IllegalArgumentException("tick duration produces an invalid animation rate");
        }
        return rate;
    }

    /**
     * Перевіряє, що тривалість анімації є скінченною та додатною.
     *
     * @param millis тривалість такту в мілісекундах.
     * @return перевірене значення.
     * @throws IllegalArgumentException якщо тривалість некоректна.
     */
    private static double requireTickDurationMillis(double millis) {
        if (!Double.isFinite(millis) || millis <= 0.0) {
            throw new IllegalArgumentException("tick duration must be finite and positive");
        }
        return millis;
    }

    /**
     * Встановлює кінцеві точки короткої лінії та стрілки імпульсу.
     *
     * @param start початкова точка видимого імпульсу.
     * @param end кінцева точка видимого імпульсу.
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
        double arrowLength = Math.min(ARROW_SIZE, direction.magnitude());
        double arrowHalfWidth = Math.min(ARROW_HALF_WIDTH, arrowLength / 2.0);
        Point2D tip = end;
        Point2D back = tip.subtract(normalized.multiply(arrowLength));
        Point2D left = back.add(perpendicular.multiply(arrowHalfWidth));
        Point2D right = back.subtract(perpendicular.multiply(arrowHalfWidth));
        arrow.getPoints().setAll(
                tip.getX(), tip.getY(),
                left.getX(), left.getY(),
                right.getX(), right.getY()
        );
    }

    /** Завершує анімацію, прибирає графічний вузол і викликає обробник завершення. */
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
