package com.example.neuronmap.view.connection;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.util.GeometryUtils;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.NeuronVisualGeometry;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.Function;

/** Відображає напрямлений зв'язок між двома нейронами та керує геометрією, стрілкою й підсвічуванням для видалення. */
public final class ConnectionView extends Pane {

    private static final Color HIT_TEST_STROKE_COLOR = Color.rgb(255, 255, 255, 0.001);
    private static final double DELETE_BLINK_MILLIS = 500.0;
    /** Невеликий проміжок між вістрям стрілки та зовнішнім краєм вхідного порта. */
    private static final double ARROW_PORT_GAP = 0.5;

    private Connection model;
    private final Function<String, NeuronView> viewLookup;
    private final Function<String, Neuron> neuronLookup;
    private final ConnectionStyle style;
    private final Line line = new Line();
    private final Line hitLine = new Line();
    private final Polygon arrow = new Polygon();
    private final ReadOnlyLongWrapper geometryRevision = new ReadOnlyLongWrapper(this, "geometryRevision");

    private Timeline blinkTimeline;
    private Point2D arrowTip;
    private boolean deleteHighlighted;

    /**
     * Створює зв'язок зі стандартним власним стилем компонента.
     *
     * @param model модель зв'язку, який потрібно відобразити.
     * @param viewLookup функція пошуку подання нейрона за ідентифікатором.
     * @param neuronLookup функція пошуку нейрона в моделі за ідентифікатором.
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
     * @param model модель зв'язку, який потрібно відобразити.
     * @param viewLookup функція пошуку подання нейрона за ідентифікатором.
     * @param neuronLookup функція пошуку нейрона в моделі за ідентифікатором.
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
        line.setStrokeLineCap(StrokeLineCap.BUTT);
        line.setMouseTransparent(true);
        hitLine.setStroke(HIT_TEST_STROKE_COLOR);
        hitLine.setStrokeWidth(style.hitStrokeWidth());
        hitLine.setStrokeLineCap(StrokeLineCap.BUTT);
        hitLine.setFill(null);
        hitLine.setMouseTransparent(false);
        arrow.setFill(baseArrowColor());
        arrow.setMouseTransparent(true);
        getChildren().addAll(line, hitLine, arrow);
        updateGeometry();
    }

    /** Повертає модель карти нейронів. @return модель зв'язку. */
    public Connection model() { return model; }

    /**
     * Повертає властивість, що змінюється після оновлення геометрії зв'язку.
     * Активні імпульси використовують її, щоб синхронно оновлювати траєкторію без окремого циклу опитування.
     *
     * @return властивість версії геометрії.
     */
    public ReadOnlyLongProperty geometryRevisionProperty() {
        return geometryRevision.getReadOnlyProperty();
    }

    /**
     * Оновлює посилання на модель після відновлення стану історії, не замінюючи сумісне графічне подання.
     *
     * @param restoredModel відновлена модель того самого напрямленого зв'язку.
     * @throws IllegalArgumentException якщо ідентифікатор або кінці зв'язку відрізняються.
     */
    public void updateModel(Connection restoredModel) {
        Objects.requireNonNull(restoredModel, "restoredModel");
        if (!model.id().equals(restoredModel.id())
                || !model.sourceId().equals(restoredModel.sourceId())
                || !model.targetId().equals(restoredModel.targetId())) {
            throw new IllegalArgumentException("A connection view can only be rebound to the same connection");
        }
        model = restoredModel;
        if (!deleteHighlighted) {
            line.setStroke(baseColor());
            arrow.setFill(baseArrowColor());
        }
    }

    /** Перевіряє стан підсвічування видалення. @return {@code true}, якщо анімація видалення активна. */
    public boolean isDeleteHighlighted() { return deleteHighlighted; }

    /**
     * Оновлює геометрію за центрами портів і скорочує видимий зв'язок до основи наконечника.
     * Вістря розташовується перед зовнішнім краєм вхідного порта, а знімок імпульсу використовує ту саму кінцеву точку.
     * Зв'язки до прихованих учасників структури не відображаються.
     */
    public void updateGeometry() {
        NeuronView source = viewLookup.apply(model.sourceId());
        NeuronView target = viewLookup.apply(model.targetId());
        if (source == null || target == null) {
            return;
        }
        if (source.isHiddenInClosedStructureDrawer() || target.isHiddenInClosedStructureDrawer()) {
            arrowTip = null;
            setVisible(false);
            return;
        }

        setVisible(true);
        Point2D start = connectionPoint(source, true);
        Point2D targetCenter = connectionPoint(target, false);
        Point2D direction = targetCenter.subtract(start);
        double connectionLength = direction.magnitude();
        if (connectionLength == 0.0) {
            clearArrowGeometry(start);
            geometryRevision.set(geometryRevision.get() + 1L);
            return;
        }

        Point2D unitDirection = direction.normalize();
        double targetClearance = NeuronVisualGeometry.INPUT_PORT_OUTER_RADIUS + ARROW_PORT_GAP;
        double availableLength = connectionLength - targetClearance;
        if (availableLength <= 0.0) {
            clearArrowGeometry(start);
            geometryRevision.set(geometryRevision.get() + 1L);
            return;
        }

        arrowTip = targetCenter.subtract(unitDirection.multiply(targetClearance));
        double arrowLength = Math.min(style.arrowSize(), availableLength);
        Point2D arrowBase = updateArrow(arrowTip, unitDirection, arrowLength);
        updateLineGeometry(start, arrowBase, arrowTip);
        geometryRevision.set(geometryRevision.get() + 1L);
    }

    /**
     * Створює знімок геометрії та палітри зв'язку для анімації імпульсу.
     * Прихований зв'язок не створює імпульсу на застарілих координатах.
     *
     * @return знімок або {@code null}, якщо зв'язок прихований, джерело відсутнє чи між портами не вистачає місця для наконечника.
     */
    public ConnectionPulseSnapshot capturePulseSnapshot() {
        if (!isVisible()) {
            return null;
        }
        Neuron source = neuronLookup.apply(model.sourceId());
        if (source == null) {
            return null;
        }
        Point2D start = new Point2D(line.getStartX(), line.getStartY());
        if (arrowTip == null || start.distance(arrowTip) == 0.0) {
            return null;
        }
        Color pulseColor = style.pulseColor(source.type());
        return new ConnectionPulseSnapshot(start, arrowTip, pulseColor, style.sourceColor(source.type()));
    }

    /**
     * Установлює або скасовує анімоване підсвічування зв'язку для режиму видалення.
     * Повторне встановлення активного підсвічування не перезапускає його часову шкалу.
     *
     * @param highlighted {@code true}, щоб запустити підсвічування; {@code false}, щоб прибрати його.
     */
    public void setDeleteHighlight(boolean highlighted) {
        if (highlighted && deleteHighlighted && blinkTimeline != null) {
            return;
        }
        deleteHighlighted = highlighted;
        stopDeleteHighlight();
        if (!highlighted) {
            return;
        }
        deleteHighlighted = true;
        Color normal = baseColor();
        Color normalArrow = baseArrowColor();
        Color highlight = style.deleteHighlightColor();
        blinkTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(line.strokeProperty(), normal),
                        new KeyValue(arrow.fillProperty(), normalArrow)),
                new KeyFrame(Duration.millis(DELETE_BLINK_MILLIS),
                        new KeyValue(line.strokeProperty(), highlight),
                        new KeyValue(arrow.fillProperty(), highlight))
        );
        blinkTimeline.setAutoReverse(true);
        blinkTimeline.setCycleCount(Animation.INDEFINITE);
        blinkTimeline.play();
    }

    /** Призупиняє підсвічування видалення, зберігаючи можливість відновлення. */
    public void pauseDeleteHighlight() {
        if (blinkTimeline != null && blinkTimeline.getStatus() == Animation.Status.RUNNING) {
            blinkTimeline.pause();
        }
    }

    /** Відновлює анімацію підсвічування видалення після паузи. */
    public void resumeDeleteHighlight() {
        if (blinkTimeline != null && blinkTimeline.getStatus() == Animation.Status.PAUSED) {
            blinkTimeline.play();
        }
    }

    /** Зупиняє анімацію підсвічування та повертає зв'язок до базових кольорів лінії й наконечника. */
    public void stopDeleteHighlight() {
        if (blinkTimeline != null) {
            blinkTimeline.stop();
            blinkTimeline = null;
        }
        deleteHighlighted = false;
        line.setStroke(baseColor());
        arrow.setFill(baseArrowColor());
    }

    /**
     * Обчислює кінцеву точку зв'язку в його локальних координатах з урахуванням вкладених подань.
     *
     * @param neuronView подання нейрона, до порту якого потрібно приєднатися.
     * @param output {@code true} для вихідного порту джерела; {@code false} для вхідного порту цілі.
     * @return координати порту в системі координат цього подання зв'язку.
     */
    private Point2D connectionPoint(NeuronView neuronView, boolean output) {
        if (getParent() == null) {
            return output ? neuronView.outputPoint() : neuronView.inputPoint();
        }
        Point2D scenePoint = output ? neuronView.outputPointInScene() : neuronView.inputPointInScene();
        return sceneToLocal(scenePoint);
    }

    /**
     * Узгоджує видимий стрижень, зону вибору та кінчик біля вхідного порта.
     * Стрижень закінчується на основі трикутника, а зона вибору — на його вістрі.
     *
     * @param start початкова точка зв'язку.
     * @param arrowBase центр основи наконечника.
     * @param tip вістря наконечника перед зовнішнім краєм порта.
     */
    private void updateLineGeometry(Point2D start, Point2D arrowBase, Point2D tip) {
        line.setStartX(start.getX());
        line.setStartY(start.getY());
        line.setEndX(arrowBase.getX());
        line.setEndY(arrowBase.getY());
        hitLine.setStartX(start.getX());
        hitLine.setStartY(start.getY());
        hitLine.setEndX(tip.getX());
        hitLine.setEndY(tip.getY());
    }

    /**
     * Будує трикутник від заданого вістря в напрямку до джерела та повертає центр його основи.
     *
     * @param tip вістря наконечника.
     * @param unitDirection нормалізований напрямок від джерела до цільового порта.
     * @param arrowLength фактична довжина наконечника з урахуванням коротких зв'язків.
     * @return центр основи наконечника, у якій має завершуватися видимий стрижень.
     */
    private Point2D updateArrow(Point2D tip, Point2D unitDirection, double arrowLength) {
        Point2D perpendicular = GeometryUtils.perpendicularUnitVector(unitDirection);
        Point2D back = tip.subtract(unitDirection.multiply(arrowLength));
        double halfWidth = Math.min(style.arrowHalfWidth(), arrowLength / 2.0);
        Point2D left = back.add(perpendicular.multiply(halfWidth));
        Point2D right = back.subtract(perpendicular.multiply(halfWidth));
        arrow.getPoints().setAll(tip.getX(), tip.getY(), left.getX(), left.getY(), right.getX(), right.getY());
        return back;
    }

    /**
     * Очищає наконечник і згортає стрижень до його початку, якщо між портами не вистачає місця для стрілки.
     *
     * @param start початкова точка зв'язку.
     */
    private void clearArrowGeometry(Point2D start) {
        arrowTip = null;
        arrow.getPoints().clear();
        updateLineGeometry(start, start, start);
    }

    /** Повертає базовий колір лінії зв'язку за типом джерела. */
    private Color baseColor() {
        Neuron source = neuronLookup.apply(model.sourceId());
        return style.sourceColor(source == null ? null : source.type());
    }

    /** Повертає базовий колір наконечника стрілки за типом джерела. */
    private Color baseArrowColor() {
        Neuron source = neuronLookup.apply(model.sourceId());
        return style.arrowColor(source == null ? null : source.type());
    }
}
