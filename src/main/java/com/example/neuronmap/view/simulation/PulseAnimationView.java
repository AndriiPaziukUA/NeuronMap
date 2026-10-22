package com.example.neuronmap.view.simulation;

import com.example.neuronmap.view.connection.ConnectionPulseSnapshot;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Анімує трикутний наконечник сигналу вздовж актуальної геометрії зв'язку.
 * Прогрес руху має одне джерело часу — {@link Timeline}, зміни геометрії надходять окремими подіями,
 * а межа такту може синхронно завершити імпульс у кінцевій точці.
 */
public final class PulseAnimationView extends Pane {

    /** Тривалість для використання подання окремо від симуляції. */
    public static final double DEFAULT_TICK_DURATION_MILLIS = 380.0;

    private static final double MIDPOINT_MILLIS = 180.0;
    private static final double MAX_PULSE_LENGTH = 32.0;
    private static final double PULSE_LENGTH_RATIO = 0.22;
    private static final double ARROW_SIZE = 11.0;
    private static final double ARROW_HALF_WIDTH = 5.5;

    private Supplier<ConnectionPulseSnapshot> snapshotSupplier;
    private final Polygon arrow = new Polygon();
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
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
        arrow.getStyleClass().add("pulse-animation-arrow");
        arrow.setFill(lastSnapshot.pulseColor());
        arrow.setMouseTransparent(true);
        getChildren().add(arrow);
        progress.addListener((observable, oldValue, newValue) -> updatePulsePosition(newValue.doubleValue()));
        updatePulsePosition(0.0);
        timeline = createTimeline();
        timeline.setRate(animationRate(tickDurationMillis));
    }

    /** Запускає анімацію, не скидаючи вже накопичений прогрес. */
    public void play() {
        if (!finished) {
            timeline.play();
        }
    }

    /** Призупиняє анімацію, зберігаючи її поточний прогрес. */
    public void pause() {
        if (!finished) {
            timeline.pause();
        }
    }

    /** Відновлює призупинену анімацію імпульсу. */
    public void resume() {
        if (!finished) {
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
     * Завершує рух у кінцевій точці перед обробкою наступного такту симуляції.
     * Це усуває гонку між незалежними часовими шкалами таймера симуляції та імпульсу.
     */
    public void completeAtTickBoundary() {
        if (finished) {
            return;
        }
        timeline.stop();
        progress.set(1.0);
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

    /**
     * Переприв'язує джерело геометрії, не перезапускаючи часову шкалу імпульсу.
     * Це дозволяє тому самому імпульсу продовжитися після відновлення подання зв'язку.
     *
     * @param supplier постачальник актуального знімка зв'язку; може повертати {@code null}, поки зв'язок відсутній.
     * @throws NullPointerException якщо постачальник не задано.
     */
    public void setSnapshotSupplier(Supplier<ConnectionPulseSnapshot> supplier) {
        snapshotSupplier = Objects.requireNonNull(supplier, "supplier");
        refreshGeometry();
    }

    /**
     * Оновлює траєкторію й колір наконечника після подієвого повідомлення про зміну зв'язку.
     * Не змінює прогрес або тривалість часової шкали.
     */
    public void refreshGeometry() {
        ConnectionPulseSnapshot currentSnapshot = snapshotSupplier.get();
        if (currentSnapshot == null) {
            return;
        }
        if (!currentSnapshot.equals(lastSnapshot)) {
            lastSnapshot = currentSnapshot;
            arrow.setFill(lastSnapshot.pulseColor());
        }
        updatePulsePosition(progress.get());
    }

    /**
     * Розташовує лише трикутний наконечник на відповідній частині траєкторії зв'язку.
     *
     * @param value частка пройденого шляху в діапазоні від нуля до одиниці.
     */
    void updatePulsePosition(double value) {
        Point2D start = lastSnapshot.start();
        Point2D end = lastSnapshot.end();
        Point2D direction = end.subtract(start);
        double length = direction.magnitude();
        if (length == 0.0) {
            arrow.getPoints().clear();
            return;
        }

        double fraction = Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
        Point2D unitDirection = direction.normalize();
        double pulseLength = Math.min(MAX_PULSE_LENGTH, length * PULSE_LENGTH_RATIO);
        double travelDistance = (length - pulseLength) * fraction;
        Point2D tip = start.add(unitDirection.multiply(travelDistance + pulseLength));
        applyArrowGeometry(tip, unitDirection, pulseLength);
    }

    /**
     * Створює часову шкалу руху наконечника без зміни його кольору чи прозорості.
     * Наконечник видаляється з полотна після завершення руху.
     *
     * @return часова шкала руху наконечника.
     */
    private Timeline createTimeline() {
        Timeline result = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(progress, 0.0)
                ),
                new KeyFrame(
                        Duration.millis(MIDPOINT_MILLIS),
                        new KeyValue(progress, 0.5)
                ),
                new KeyFrame(
                        Duration.millis(DEFAULT_TICK_DURATION_MILLIS),
                        new KeyValue(progress, 1.0)
                )
        );
        result.setOnFinished(event -> finish());
        return result;
    }

    /**
     * Обчислює коефіцієнт відтворення так, щоб рух імпульсу тривав один такт.
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
     * Формує геометрію трикутного наконечника, спрямованого вздовж зв'язку.
     *
     * @param tip вершина наконечника.
     * @param unitDirection нормалізований напрямок руху.
     * @param pulseLength довжина імпульсу, потрібна для збереження масштабу наконечника.
     */
    private void applyArrowGeometry(Point2D tip, Point2D unitDirection, double pulseLength) {
        Point2D perpendicular = new Point2D(-unitDirection.getY(), unitDirection.getX());
        double arrowLength = Math.min(ARROW_SIZE, pulseLength);
        double arrowHalfWidth = Math.min(ARROW_HALF_WIDTH, arrowLength / 2.0);
        Point2D back = tip.subtract(unitDirection.multiply(arrowLength));
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
        Pane parent = getParent() instanceof Pane pane ? pane : null;
        if (parent != null) {
            parent.getChildren().remove(this);
        }
        onFinished.run();
    }
}
