package com.example.neuronmap.view.neuron;

import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Відображає впорядкований список учасників структури; фон і контур малює окремий нижній графічний шар. */
public final class NeuronStructureDrawerView extends Pane {

    private static final String STYLE_CLASS = "neuron-structure-drawer";
    private static final double OUTLINE_STROKE_WIDTH = 2.0;
    private static final double CONTENT_VIEW_ORDER = 1.0;

    /** Геометричний шаблон контуру, який окреме подання переносить у шар поверхні шухлядки. */
    private final Path outline = createOutlinePath();
    private List<NeuronView> memberViews = List.of();
    private boolean expanded;

    /**
     * Створює приховану шухлядку сталої ширини з прозорим тлом власного контейнера.
     * Видиме непрозоре тло й контур відображаються окремо в нижньому шарі полотна, під зв'язками.
     */
    public NeuronStructureDrawerView() {
        getStyleClass().add(STYLE_CLASS);
        setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));
        setBorder(Border.EMPTY);
        setManaged(false);
        setPickOnBounds(true);
        setMinWidth(NeuronView.WIDTH);
        setPrefWidth(NeuronView.WIDTH);
        setMaxWidth(NeuronView.WIDTH);
        setMinHeight(0.0);
        setPrefHeight(0.0);
        setVisible(false);
        getChildren().add(outline);
        installMouseEventBoundary();
    }

    /**
     * Замінює відображений список учасників, зберігаючи порядок моделі та розміщуючи картки впритул.
     * Подання від'єднуються від попередніх контейнерів до додавання в цю шухлядку.
     *
     * @param members впорядковані подання нейронів структури.
     * @throws NullPointerException якщо список або один із його елементів не задано.
     * @throws IllegalArgumentException якщо той самий нейрон передано більше одного разу.
     */
    public void setMembers(List<NeuronView> members) {
        Objects.requireNonNull(members, "members");
        List<NeuronView> updatedMembers = List.copyOf(members);
        Set<String> memberIds = new LinkedHashSet<>();
        for (NeuronView member : updatedMembers) {
            if (!memberIds.add(member.model().id())) {
                throw new IllegalArgumentException("Structure drawer cannot display the same neuron twice");
            }
        }

        for (NeuronView previousMember : memberViews) {
            if (!updatedMembers.contains(previousMember)) {
                Parent parent = previousMember.getParent();
                if (parent == this || !(parent instanceof NeuronStructureDrawerView)) {
                    previousMember.setStructureMember(false);
                }
            }
        }
        for (NeuronView member : updatedMembers) {
            Parent parent = member.getParent();
            if (parent instanceof Pane pane && parent != this) {
                pane.getChildren().remove(member);
            }
        }
        getChildren().clear();
        getChildren().add(outline);
        memberViews = updatedMembers;
        for (NeuronView member : memberViews) {
            member.setStructureMember(true);
            getChildren().add(member);
        }
        refreshLayout();
        setVisible(expanded);
        refreshAncestorDrawers();
    }

    /** Перемикає стан шухлядки.
     * @return {@code true}, якщо шухлядка відкрита після перемикання.
     */
    public boolean toggleExpanded() {
        setExpanded(!expanded);
        return expanded;
    }

    /** Установлює стан відкриття без зміни списку учасників.
     * @param expanded {@code true}, щоб показати учасників; {@code false}, щоб сховати їх.
     */
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        setVisible(expanded);
        refreshLayout();
        refreshAncestorDrawers();
    }

    /** Повертає стан відкриття шухлядки.
     * @return {@code true}, якщо учасники відображаються.
     */
    public boolean isExpanded() {
        return expanded;
    }

    /** Повертає незмінний список подань у тому самому порядку, у якому вони показуються.
     * @return впорядкований незмінний список учасників.
     */
    public List<NeuronView> memberViews() {
        return memberViews;
    }

    /**
     * Повертає геометричні точки контуру в локальних координатах шухлядки.
     * Точки беруться з одного шаблону, щоб шар поверхні й розміщення вмісту використовували ту саму геометрію.
     *
     * @return незмінні точки контуру в порядку обходу.
     */
    List<Point2D> outlinePoints() {
        List<Point2D> points = new ArrayList<>(outline.getElements().size());
        for (PathElement element : outline.getElements()) {
            if (element instanceof MoveTo moveTo) {
                points.add(new Point2D(moveTo.getX(), moveTo.getY()));
            } else if (element instanceof LineTo lineTo) {
                points.add(new Point2D(lineTo.getX(), lineTo.getY()));
            }
        }
        return List.copyOf(points);
    }

    /** Перераховує висоту шухлядки й укладає рядки впритул з урахуванням відкритих вкладених структур. */
    public void refreshLayout() {
        setViewOrder(CONTENT_VIEW_ORDER);
        double height = 0.0;
        for (NeuronView member : memberViews) {
            height += member.displayedStructureRowHeight();
        }
        resizeRelocate(0.0, NeuronView.HEIGHT, NeuronView.WIDTH, height);
        setMinHeight(height);
        setPrefHeight(height);
        setMaxHeight(height);

        double rowY = 0.0;
        for (NeuronView member : memberViews) {
            member.resizeRelocate(0.0, rowY, NeuronView.WIDTH, NeuronView.HEIGHT);
            rowY += member.displayedStructureRowHeight();
        }
        outline.getElements().setAll(
                new MoveTo(0.0, 0.0),
                new LineTo(0.0, height),
                new LineTo(NeuronView.WIDTH, height),
                new LineTo(NeuronView.WIDTH, 0.0));
    }

    /** Відокремлює події миші шухлядки від нейрона-контейнера після обробки цільовим вузлом. */
    private void installMouseEventBoundary() {
        addEventHandler(MouseEvent.ANY, this::consumeBubbledMouseEvent);
    }

    /** Зупиняє спливання події миші до зовнішньої структури після обробки її цільовим вузлом. */
    private void consumeBubbledMouseEvent(MouseEvent event) {
        event.consume();
    }

    /** Створює невидимий у цьому шарі шаблон контуру для окремого відображення над шарами зв'язків. */
    private static Path createOutlinePath() {
        Path path = new Path();
        path.setFill(null);
        path.setStroke(Color.TRANSPARENT);
        path.setStrokeWidth(OUTLINE_STROKE_WIDTH);
        path.setMouseTransparent(true);
        return path;
    }

    /** Оновлює висоту всіх предків-шухлядок після зміни складу або відкритості вкладеної структури. */
    private void refreshAncestorDrawers() {
        Parent ancestor = getParent();
        while (ancestor != null) {
            if (ancestor instanceof NeuronStructureDrawerView parentDrawer) {
                parentDrawer.refreshLayout();
            }
            ancestor = ancestor.getParent();
        }
    }
}
