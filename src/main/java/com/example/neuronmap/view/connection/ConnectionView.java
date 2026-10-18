package com.example.neuronmap.view.connection;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.util.GeometryUtils;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.Function;

/**
 * Відображає напрямлений зв'язок між двома нейронами та керує геометрією, стрілкою й підсвічуванням для видалення.
 */
public final class ConnectionView extends Pane {

    private static final Color HIT_TEST_STROKE_COLOR = Color.rgb(255, 255, 255, 0.001);
    private static final double DELETE_BLINK_MILLIS = 500.0;

    private final Connection model;
    private final Function<String, NeuronView> viewLookup;
    private final Function<String, Neuron> neuronLookup;
    private final ConnectionStyle style;
    private final Line line = new Line();
    private final Line hitLine = new Line();
    private final Polygon arrow = new Polygon();

    private Timeline blinkTimeline;
    private boolean deleteHighlighted;

    /**
     * Створює зв'язок зі стандартним власним стилем компонента.
     *
     * @param model модель карти нейронів.
     * @param viewLookup функція пошуку подання нейрона.
     * @param neuronLookup функція пошуку нейрона в моделі.
     */
    public ConnectionView(
            Connection model,
            Function<String, NeuronView> viewLookup,
            Function<String, Neuron> neuronLookup
    ) {
        this(model, viewLookup, neuronLookup, ConnectionStyle.DEFAULT);
    }

    /**
     * Створює зв'язок із переданим локальним описом стилю.
     *
     * @param model модель карти нейронів.
     * @param viewLookup функція пошуку подання нейрона.
     * @param neuronLookup функція пошуку нейрона в моделі.
     * @param style кольори та геометричні параметри саме цього виду зв'язку.
     * @throws NullPointerException якщо будь-яку залежність не задано.
     */
    public ConnectionView(
            Connection model,
            Function<String, NeuronView> viewLookup,
            Function<String, Neuron> neuronLookup,
            ConnectionStyle style
    ) {
        this.model = Objects.requireNonNull(model, "model");
        this.viewLookup = Objects.requireNonNull(viewLookup, "viewLookup");
        this.neuronLookup = Objects.requireNonNull(neuronLookup, "neuronLookup");
        this.style = Objects.requireNonNull(style, "style");
        setPickOnBounds(false);

        line.setStroke(baseColor());
        line.setStrokeWidth(style.strokeWidth());
        line.setMouseTransparent(true);
        hitLine.setStroke(HIT_TEST_STROKE_COLOR);
        hitLine.setStrokeWidth(style.hitStrokeWidth());
        hitLine.setFill(null);
        hitLine.setMouseTransparent(false);
        arrow.setFill(baseColor());
        arrow.setMouseTransparent(true);
        getChildren().addAll(line, hitLine, arrow);
        updateGeometry();
    }

    /**
     * Повертає модель карти нейронів.
     *
     * @return модель зв'язку.
     */
    public Connection model() {
        return model;
    }

    /**
     * Перевіряє стан підсвічування видалення.
     *
     * @return {@code true}, якщо анімація видалення активна.
     */
    public boolean isDeleteHighlighted() {
        return deleteHighlighted;
    }

    /**
     * Перераховує кінцеві точки зв'язку за поточними положеннями нейронів.
     */
    public void updateGeometry() {
        NeuronView source = viewLookup.apply(model.sourceId());
        NeuronView target = viewLookup.apply(model.targetId());
        if (source == null || target == null) {
            return;
        }
        Point2D start = source.outputPoint();
        Point2D end = target.inputPoint();
        line.setStartX(start.getX());
        line.setStartY(start.getY());
        line.setEndX(end.getX());
        line.setEndY(end.getY());
        hitLine.setStartX(start.getX());
        hitLine.setStartY(start.getY());
        hitLine.setEndX(end.getX());
        hitLine.setEndY(end.getY());
        updateArrow(start, end);
    }

    /**
     * Створює знімок геометрії та палітри зв'язку для анімації імпульсу.
     *
     * @return знімок або {@code null}, якщо джерело чи довжина відсутні.
     */
    public ConnectionPulseSnapshot capturePulseSnapshot() {
        Neuron source = neuronLookup.apply(model.sourceId());
        if (source == null) {
            return null;
        }
        Point2D start = new Point2D(line.getStartX(), line.getStartY());
        Point2D end = new Point2D(line.getEndX(), line.getEndY());
        if (start.distance(end) == 0.0) {
            return null;
        }
        Color pulseColor = style.pulseColor(source.type());
        return new ConnectionPulseSnapshot(start, end, pulseColor, style.sourceColor(source.type()));
    }

    /**
     * Установлює або скасовує анімоване підсвічування зв'язку для режиму видалення.
     *
     * @param highlighted {@code true}, щоб запустити підсвічування; {@code false}, щоб прибрати його.
     */
    public void setDeleteHighlight(boolean highlighted) {
        deleteHighlighted = highlighted;
        stopDeleteHighlight();
        if (!highlighted) {
            return;
        }
        deleteHighlighted = true;
        Color normal = baseColor();
        Color highlight = style.deleteHighlightColor();
        blinkTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(line.strokeProperty(), normal),
                        new KeyValue(arrow.fillProperty(), normal)),
                new KeyFrame(Duration.millis(DELETE_BLINK_MILLIS),
                        new KeyValue(line.strokeProperty(), highlight),
                        new KeyValue(arrow.fillProperty(), highlight))
        );
        blinkTimeline.setAutoReverse(true);
        blinkTimeline.setCycleCount(Animation.INDEFINITE);
        blinkTimeline.play();
    }

    /**
     * Призупиняє підсвічування видалення, зберігаючи можливість відновлення.
     */
    public void pauseDeleteHighlight() {
        if (blinkTimeline != null && blinkTimeline.getStatus() == Animation.Status.RUNNING) {
            blinkTimeline.pause();
        }
    }

    /**
     * Відновлює анімацію підсвічування видалення після паузи.
     */
    public void resumeDeleteHighlight() {
        if (blinkTimeline != null && blinkTimeline.getStatus() == Animation.Status.PAUSED) {
            blinkTimeline.play();
        }
    }

    /**
     * Зупиняє анімацію підсвічування та повертає зв'язок до базового кольору.
     */
    public void stopDeleteHighlight() {
        if (blinkTimeline != null) {
            blinkTimeline.stop();
            blinkTimeline = null;
        }
        deleteHighlighted = false;
        Color normal = baseColor();
        line.setStroke(normal);
        arrow.setFill(normal);
    }

    /**
     * Розташовує наконечник стрілки відповідно до напрямку лінії зв'язку.
     *
     * @param start початкова точка.
     * @param end кінцева точка.
     */
    private void updateArrow(Point2D start, Point2D end) {
        Point2D direction = end.subtract(start);
        if (direction.magnitude() == 0.0) {
            arrow.getPoints().clear();
            return;
        }
        Point2D normalized = direction.normalize();
        Point2D perpendicular = GeometryUtils.perpendicularUnitVector(direction);
        Point2D tip = end;
        Point2D back = tip.subtract(normalized.multiply(style.arrowSize()));
        Point2D left = back.add(perpendicular.multiply(style.arrowHalfWidth()));
        Point2D right = back.subtract(perpendicular.multiply(style.arrowHalfWidth()));
        arrow.getPoints().setAll(tip.getX(), tip.getY(), left.getX(), left.getY(), right.getX(), right.getY());
    }

    /**
     * Повертає базовий колір, заданий локальним стилем за типом джерела.
     *
     * @return базовий колір зв'язку.
     */
    private Color baseColor() {
        Neuron source = neuronLookup.apply(model.sourceId());
        return style.sourceColor(source == null ? null : source.type());
    }
}
