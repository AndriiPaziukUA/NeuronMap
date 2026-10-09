package com.example.neuronmap.view;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.util.GeometryUtils;
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

import java.util.function.Function;

/**
 * Відображає напрямлений зв’язок між двома нейронами та керує його геометрією, стрілкою й підсвічуванням для видалення.
 */
public final class ConnectionView extends Pane {

    private final Connection model;
    private final Function<String, NeuronView> viewLookup;
    private final Function<String, Neuron> neuronLookup;

    private final Line line = new Line();
    private final Line hitLine = new Line();
    private final Polygon arrow = new Polygon();

    private Timeline blinkTimeline;
    private boolean deleteHighlighted;

    /**
     * Створює екземпляр ConnectionView та зберігає передані залежності, потрібні для його роботи.
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
        this.model = model;
        this.viewLookup = viewLookup;
        this.neuronLookup = neuronLookup;

        setPickOnBounds(false);

        line.setStroke(baseColor());
        line.setStrokeWidth(2.3);
        line.setMouseTransparent(true);

        hitLine.setStroke(Color.rgb(255, 255, 255, 0.001));
        hitLine.setStrokeWidth(18);
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
     * @return модель карти нейронів.
     */
    public Connection model() {
        return model;
    }

    /**
     * Перевіряє, чи delete highlighted за поточного стану компонента.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean isDeleteHighlighted() {
        return deleteHighlighted;
    }

    /**
     * Перераховує початкову й кінцеву точки зв’язку за поточними положеннями нейронів.
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
 * Створює знімок геометрії зв’язку для анімації імпульсу.
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

        Color pulseColor = source.type() == NeuronType.EXCITATORY
                ? Color.web("#74ef9a")
                : Color.web("#ef8e8e");

        return new ConnectionPulseSnapshot(
                start,
                end,
                pulseColor,
                baseColor()
        );
    }

    /**
     * Установлює delete highlight для поточного об’єкта.
     *
     * @param highlighted значення «highlighted», яке використовується в цьому методі.
     */
    public void setDeleteHighlight(boolean highlighted) {
        deleteHighlighted = highlighted;
        stopDeleteHighlight();

        if (!highlighted) {
            return;
        }

        deleteHighlighted = true;
        Color normal = baseColor();
        Color yellow = Color.web("#ffe25a");

        blinkTimeline = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(line.strokeProperty(), normal),
                        new KeyValue(arrow.fillProperty(), normal)
                ),
                new KeyFrame(
                        Duration.millis(500),
                        new KeyValue(line.strokeProperty(), yellow),
                        new KeyValue(arrow.fillProperty(), yellow)
                )
        );

        blinkTimeline.setAutoReverse(true);
        blinkTimeline.setCycleCount(Animation.INDEFINITE);
        blinkTimeline.play();
    }

    /**
     * Призупиняє «delete highlight», зберігаючи можливість подальшого відновлення.
     */
    public void pauseDeleteHighlight() {
        if (blinkTimeline != null
                && blinkTimeline.getStatus() == Animation.Status.RUNNING) {
            blinkTimeline.pause();
        }
    }

    /**
     * Відновлює «delete highlight» після призупинення.
     */
    public void resumeDeleteHighlight() {
        if (blinkTimeline != null
                && blinkTimeline.getStatus() == Animation.Status.PAUSED) {
            blinkTimeline.play();
        }
    }

    /**
     * Зупиняє delete highlight та очищає пов’язаний активний стан.
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
     * Розташовує наконечник стрілки за напрямком лінії зв’язку.
     *
     * @param start початкова точка зв’язку у координатах сцени.
     * @param end кінцева точка зв’язку у координатах сцени.
     */
    private void updateArrow(Point2D start, Point2D end) {
        Point2D direction = end.subtract(start);

        if (direction.magnitude() == 0) {
            arrow.getPoints().clear();
            return;
        }

        Point2D normalized = direction.normalize();
        Point2D perpendicular =
                GeometryUtils.perpendicularUnitVector(direction);

        double size = 11.0;
        double halfWidth = 5.5;

        Point2D tip = end;
        Point2D back = tip.subtract(normalized.multiply(size));
        Point2D left = back.add(perpendicular.multiply(halfWidth));
        Point2D right = back.subtract(perpendicular.multiply(halfWidth));

        arrow.getPoints().setAll(
                tip.getX(), tip.getY(),
                left.getX(), left.getY(),
                right.getX(), right.getY()
        );
    }

    /**
     * Повертає базовий колір зв’язку відповідно до кольору, заданого його поданням.
     *
     * @return базовий колір зв’язку відповідно до кольору, заданого його поданням.
     */
    private Color baseColor() {
        Neuron source = neuronLookup.apply(model.sourceId());

        if (source == null) {
            return Color.web("#8b929c");
        }

        return source.type() == NeuronType.EXCITATORY
                ? Color.web("#55bf78")
                : Color.web("#8b929c");
    }
}
