package com.example.neuronmap.view.connection;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.util.GeometryUtils;
import com.example.neuronmap.view.neuron.NeuronStructureDrawerView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.NeuronVisualGeometry;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.Function;

/** Відображає напрямлений зв'язок між двома нейронами та керує його геометрією, стрілкою й підсвічуванням. */
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
    private boolean deleteHighlightRequested;
    private ConnectionView visualRepresentative = this;
    private ChangeListener<Number> representativeGeometryListener;
    private ChangeListener<Boolean> representativeVisibilityListener;
    private ChangeListener<Parent> representativeParentListener;

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
        parentProperty().addListener((_, _, parent) -> {
            if (parent == null && visualRepresentative != this) {
                setVisualRepresentative(this);
            }
        });
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
    public boolean isDeleteHighlighted() { return visualRepresentative().deleteHighlighted; }

    /**
     * Оновлює геометрію з урахуванням видимих кінців та об'єднує зв'язки з однаковою видимою парою портів.
     * Коли кілька прихованих учасників ведуть до того самого видимого нейрона або контейнера, лише одне
     * подання малює лінію. Решта подань делегують йому геометрію, але зберігають власний ідентифікатор
     * і джерело сигналу для незалежної анімації кожного реального зв'язку.
     */
    public void updateGeometry() {
        VisibleEndpoints endpoints = resolveVisibleEndpoints();
        if (endpoints == null) {
            becomeStandalone();
            hideGeometry();
            return;
        }

        ConnectionView representative = findVisualRepresentative(endpoints);
        if (representative != this) {
            setVisualRepresentative(representative);
            return;
        }

        setVisualRepresentative(this);
        updateOwnGeometry(endpoints.source(), endpoints.target());
    }

    /**
     * Визначає пару видимих кінців після перенесення прихованих учасників на найвищі видимі контейнери.
     * Зв'язок між двома прихованими кінцями не має видимої проєкції.
     *
     * @return пара видимих подань або {@code null}, якщо будь-який кінець відсутній чи обидва приховані.
     */
    private VisibleEndpoints resolveVisibleEndpoints() {
        NeuronView source = viewLookup.apply(model.sourceId());
        NeuronView target = viewLookup.apply(model.targetId());
        if (source == null || target == null) {
            return null;
        }

        boolean sourceHidden = source.isHiddenInClosedStructureDrawer();
        boolean targetHidden = target.isHiddenInClosedStructureDrawer();
        if ((sourceHidden || targetHidden)
                && (!source.isStructureMember() || !target.isStructureMember())) {
            return null;
        }
        if (sourceHidden && targetHidden) {
            return null;
        }

        NeuronView visibleSource = sourceHidden ? resolveVisibleEndpoint(source) : source;
        NeuronView visibleTarget = targetHidden ? resolveVisibleEndpoint(target) : target;
        if (visibleSource.isHiddenInClosedStructureDrawer()
                || visibleTarget.isHiddenInClosedStructureDrawer()) {
            return null;
        }
        return new VisibleEndpoints(visibleSource, visibleTarget, sourceHidden || targetHidden);
    }

    /**
     * Знаходить єдине подання, яке має малювати зв'язки з такою самою парою видимих кінців.
     * Найменший ідентифікатор забезпечує стабільний вибір представника незалежно від порядку оновлення.
     *
     * @param endpoints видима пара портів цього зв'язку.
     * @return канонічне графічне подання групи.
     */
    private ConnectionView findVisualRepresentative(VisibleEndpoints endpoints) {
        if (getParent() == null || !endpoints.containsHiddenEndpoint()) {
            return this;
        }
        ConnectionView representative = this;
        if (!(getParent() instanceof Pane parent)) {
            return this;
        }
        for (Node node : parent.getChildrenUnmodifiable()) {
            if (!(node instanceof ConnectionView candidate) || candidate == this) {
                continue;
            }
            VisibleEndpoints candidateEndpoints = candidate.resolveVisibleEndpoints();
            if (candidateEndpoints != null && candidateEndpoints.containsHiddenEndpoint()
                    && endpoints.samePorts(candidateEndpoints)
                    && candidate.model.id().compareTo(representative.model.id()) < 0) {
                representative = candidate;
            }
        }
        if (representative != this && !representative.isVisualRepresentative()) {
            representative.updateGeometry();
            representative = representative.visualRepresentative();
        }
        return representative;
    }

    /** Перемикає це подання на власну геометрію після того, як попередній представник зник. */
    private void becomeStandalone() {
        if (visualRepresentative != this) {
            setVisualRepresentative(this);
        }
    }

    /**
     * Установлює власне або делеговане графічне подання та синхронізує властивості, потрібні імпульсам.
     * Делегат не малює власних фігур, однак його видимість і версія геометрії повторюють представника.
     *
     * @param representative канонічне подання; {@code this} означає власну геометрію.
     */
    private void setVisualRepresentative(ConnectionView representative) {
        if (visualRepresentative == representative) {
            return;
        }
        ConnectionView previousRepresentative = visualRepresentative;
        detachRepresentativeListeners();
        if (previousRepresentative == this && blinkTimeline != null) {
            blinkTimeline.stop();
            blinkTimeline = null;
            deleteHighlighted = false;
            line.setStroke(baseColor());
            arrow.setFill(baseArrowColor());
        }
        visualRepresentative = representative;
        if (representative == this) {
            line.setVisible(true);
            hitLine.setVisible(true);
            arrow.setVisible(true);
            setMouseTransparent(false);
            setVisible(true);
            previousRepresentative.visualRepresentative().refreshAggregateDeleteHighlight();
            refreshAggregateDeleteHighlight();
            return;
        }

        arrowTip = null;
        line.setVisible(false);
        hitLine.setVisible(false);
        arrow.setVisible(false);
        setMouseTransparent(true);
        representativeGeometryListener = (_, _, value) -> geometryRevision.set(value.longValue());
        representativeVisibilityListener = (_, _, visible) -> setVisible(visible);
        representativeParentListener = (_, _, parent) -> {
            if (parent == null && visualRepresentative != this) {
                updateGeometry();
            }
        };
        representative.geometryRevisionProperty().addListener(representativeGeometryListener);
        representative.visibleProperty().addListener(representativeVisibilityListener);
        representative.parentProperty().addListener(representativeParentListener);
        geometryRevision.set(representative.geometryRevisionProperty().get());
        setVisible(representative.isVisible());
        if (previousRepresentative != this) {
            previousRepresentative.visualRepresentative().refreshAggregateDeleteHighlight();
        }
        representative.refreshAggregateDeleteHighlight();
    }

    /** Від'єднує слухачі делегата перед зміною представника або звільненням власного подання. */
    private void detachRepresentativeListeners() {
        if (visualRepresentative == this) {
            return;
        }
        if (representativeGeometryListener != null) {
            visualRepresentative.geometryRevisionProperty().removeListener(representativeGeometryListener);
            representativeGeometryListener = null;
        }
        if (representativeVisibilityListener != null) {
            visualRepresentative.visibleProperty().removeListener(representativeVisibilityListener);
            representativeVisibilityListener = null;
        }
        if (representativeParentListener != null) {
            visualRepresentative.parentProperty().removeListener(representativeParentListener);
            representativeParentListener = null;
        }
    }

    /** Повертає видиме графічне подання, яким користуються всі учасники агрегованого зв'язку. */
    public ConnectionView visualRepresentative() {
        return visualRepresentative == this ? this : visualRepresentative.visualRepresentative();
    }

    /** Перевіряє, чи малює це подання власну лінію, а не делегує її іншому зв'язку. */
    public boolean isVisualRepresentative() {
        return visualRepresentative == this;
    }

    /** Перераховує власну геометрію за вже визначеними видимими поданнями початку та кінця. */
    private void updateOwnGeometry(NeuronView source, NeuronView target) {
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
     * Повертає найвищий видимий контейнер, який закриває подання внутрішнього нейрона, або сам нейрон.
     * Під час обходу всіх предків пізніший закритий контейнер замінює попередній: це враховує вкладені
     * структури, у яких внутрішня шухлядка може бути відкрита, але зовнішня — закрита.
     *
     * @param endpoint вихідне подання нейрона, яке може бути приховане шухлядкою.
     * @return саме подання нейрона, якщо воно видиме, або подання найближчої видимої структури-контейнера.
     */
    private static NeuronView resolveVisibleEndpoint(NeuronView endpoint) {
        NeuronView visibleContainer = endpoint;
        Node ancestor = endpoint.getParent();
        while (ancestor != null) {
            if (ancestor instanceof NeuronStructureDrawerView drawer
                    && !drawer.isExpanded()
                    && drawer.getParent() instanceof NeuronView structureView) {
                visibleContainer = structureView;
            }
            ancestor = ancestor.getParent();
        }
        return visibleContainer;
    }

    /** Приховує лінію та очищує її кінцеву точку, щоб імпульс не використовував застарілу геометрію. */
    private void hideGeometry() {
        arrowTip = null;
        setVisible(false);
    }

    /**
     * Створює знімок спільної видимої геометрії та палітри саме того джерела, яке передало сигнал.
     * Делеговані подання повертають координати представника, але зберігають колір свого реального джерела.
     *
     * @return знімок або {@code null}, якщо спільний зв'язок прихований чи геометрія недоступна.
     */
    public ConnectionPulseSnapshot capturePulseSnapshot() {
        return capturePulseSnapshotForSource(model.sourceId());
    }

    /**
     * Формує імпульс на спільній геометрії від заданого реального джерела сигналу.
     *
     * @param sourceNeuronId ідентифікатор нейрона, який фактично випустив сигнал.
     * @return знімок для цього джерела або {@code null}, якщо геометрія чи джерело недоступні.
     */
    private ConnectionPulseSnapshot capturePulseSnapshotForSource(String sourceNeuronId) {
        ConnectionView representative = visualRepresentative();
        if (representative != this) {
            return representative.capturePulseSnapshotForSource(sourceNeuronId);
        }
        if (!isVisible()) {
            return null;
        }
        Neuron source = neuronLookup.apply(sourceNeuronId);
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
     * Установлює або скасовує запит підсвічування для цього реального зв'язку.
     * Для агрегованої лінії представник підсвічується, якщо цього потребує хоча б один її учасник.
     *
     * @param highlighted {@code true}, щоб позначити зв'язок доступним для видалення.
     */
    public void setDeleteHighlight(boolean highlighted) {
        deleteHighlightRequested = highlighted;
        visualRepresentative().refreshAggregateDeleteHighlight();
    }

    /** Обчислює спільний стан підсвічування всіх логічних зв'язків, що використовують цю лінію. */
    private void refreshAggregateDeleteHighlight() {
        boolean highlighted = deleteHighlightRequested;
        if (getParent() instanceof Pane parent) {
            for (Node node : parent.getChildrenUnmodifiable()) {
                if (node instanceof ConnectionView candidate
                        && candidate != this
                        && candidate.deleteHighlightRequested
                        && candidate.visualRepresentative() == this) {
                    highlighted = true;
                    break;
                }
            }
        }
        applyDeleteHighlight(highlighted);
    }

    /** Застосовує вже агрегований стан підсвічування до графічних фігур представника. */
    private void applyDeleteHighlight(boolean highlighted) {
        if (highlighted && deleteHighlighted && blinkTimeline != null) {
            return;
        }
        if (blinkTimeline != null) {
            blinkTimeline.stop();
            blinkTimeline = null;
        }
        deleteHighlighted = highlighted;
        if (!highlighted) {
            line.setStroke(baseColor());
            arrow.setFill(baseArrowColor());
            return;
        }

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

    /** Призупиняє спільне підсвічування представника, зберігаючи можливість відновлення. */
    public void pauseDeleteHighlight() {
        ConnectionView representative = visualRepresentative();
        if (representative != this) {
            representative.pauseDeleteHighlight();
            return;
        }
        if (blinkTimeline != null && blinkTimeline.getStatus() == Animation.Status.RUNNING) {
            blinkTimeline.pause();
        }
    }

    /** Відновлює спільну анімацію підсвічування представника після паузи. */
    public void resumeDeleteHighlight() {
        ConnectionView representative = visualRepresentative();
        if (representative != this) {
            representative.resumeDeleteHighlight();
            return;
        }
        if (blinkTimeline != null && blinkTimeline.getStatus() == Animation.Status.PAUSED) {
            blinkTimeline.play();
        }
    }

    /** Знімає запит цього зв'язку на підсвічування й оновлює стан спільної лінії. */
    public void stopDeleteHighlight() {
        setDeleteHighlight(false);
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

    /** Повертає базовий колір лінії зв'язку за типом початкового нейрона, а не його графічного контейнера. */
    private Color baseColor() {
        Neuron source = neuronLookup.apply(model.sourceId());
        return style.sourceColor(source == null ? null : source.type());
    }

    /** Повертає колір наконечника за типом справжнього початкового нейрона. */
    private Color baseArrowColor() {
        Neuron source = neuronLookup.apply(model.sourceId());
        return style.arrowColor(source == null ? null : source.type());
    }

    /** Пара графічних кінців після згортання прихованих учасників на видимі структури-контейнери. */
    private record VisibleEndpoints(NeuronView source, NeuronView target, boolean hasProxyEndpoint) {
        /** Перевіряє, чи збігаються ідентифікатори видимих портів двох напрямлених зв'язків. */
        private boolean samePorts(VisibleEndpoints other) {
            return source.model().id().equals(other.source.model().id())
                    && target.model().id().equals(other.target.model().id());
        }

        /** Визначає, чи принаймні один кінець було перенесено з прихованого учасника на контейнер. */
        private boolean containsHiddenEndpoint() {
            return hasProxyEndpoint;
        }
    }

}
