package com.example.neuronmap.view.neuron;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Малює фон і контур шухлядки в окремому шарі під зв'язками, залишаючи її нейрони у верхньому шарі. */
public final class NeuronStructureDrawerSurfaceView extends Path {

    private static final String STYLE_CLASS = "neuron-structure-drawer-surface";
    private static final double OUTLINE_STROKE_WIDTH = 2.0;

    /** Створює непрозору поверхню, яка не перехоплює події миші. */
    public NeuronStructureDrawerSurfaceView() {
        getStyleClass().add(STYLE_CLASS);
        setFill(Color.web("#242830"));
        setStroke(Color.web("#25292f"));
        setStrokeWidth(OUTLINE_STROKE_WIDTH);
        setMouseTransparent(true);
        setManaged(false);
    }

    /**
     * Оновлює геометрію за локальним контуром структури й перетворює точки до координат шару поверхні.
     * Поверхня приховується для закритих шухлядок, порожнього вмісту й структур усередині закритого предка.
     * Перетворення не залежить від наявності JavaFX Scene, тому працює і під час початкової синхронізації.
     *
     * @param structureView подання нейрона-власника шухлядки.
     * @param surfaceLayer графічний шар, у якому відображається поверхня.
     * @throws NullPointerException якщо подання або шар не задано.
     * @throws IllegalArgumentException якщо подання й шар не мають спільного графічного предка.
     */
    public void refreshGeometry(NeuronView structureView, Pane surfaceLayer) {
        Objects.requireNonNull(structureView, "structureView");
        Objects.requireNonNull(surfaceLayer, "surfaceLayer");

        NeuronStructureDrawerView drawer = findDrawer(structureView);
        if (drawer == null || !drawer.isExpanded() || drawer.getHeight() <= 0.0
                || structureView.isHiddenInClosedStructureDrawer()) {
            setVisible(false);
            getElements().clear();
            return;
        }

        List<Point2D> outlinePoints = drawer.outlinePoints();
        if (outlinePoints.size() < 3) {
            setVisible(false);
            getElements().clear();
            return;
        }

        Point2D first = toSurfaceLayer(structureView, surfaceLayer, outlinePoints.getFirst());
        getElements().clear();
        getElements().add(new MoveTo(first.getX(), first.getY()));
        for (int index = 1; index < outlinePoints.size(); index++) {
            Point2D point = toSurfaceLayer(structureView, surfaceLayer, outlinePoints.get(index));
            getElements().add(new LineTo(point.getX(), point.getY()));
        }
        getElements().add(new ClosePath());
        setVisible(true);
    }

    /** Знаходить єдину шухлядку, яку подання структури утримує серед своїх дочірніх вузлів. */
    private static NeuronStructureDrawerView findDrawer(NeuronView structureView) {
        return structureView.getChildren().stream()
                .filter(NeuronStructureDrawerView.class::isInstance)
                .map(NeuronStructureDrawerView.class::cast)
                .findFirst()
                .orElse(null);
    }

    /** Перетворює точку між різними гілками дерева через їхнього найближчого спільного предка. */
    private static Point2D toSurfaceLayer(
            NeuronView structureView,
            Pane surfaceLayer,
            Point2D drawerPoint
    ) {
        Point2D structurePoint = new Point2D(
                drawerPoint.getX(), NeuronView.HEIGHT + drawerPoint.getY());
        Parent commonAncestor = findCommonAncestor(structureView, surfaceLayer);
        Point2D pointInAncestor = transformToAncestor(structureView, commonAncestor, structurePoint);

        List<Node> targetPath = new ArrayList<>();
        Node current = surfaceLayer;
        while (current != commonAncestor) {
            targetPath.add(current);
            current = current.getParent();
        }
        for (int index = targetPath.size() - 1; index >= 0; index--) {
            pointInAncestor = targetPath.get(index).parentToLocal(pointInAncestor);
        }
        return pointInAncestor;
    }

    /** Знаходить найближчого спільного предка вузлів незалежно від наявності сцени JavaFX. */
    private static Parent findCommonAncestor(Node first, Node second) {
        Set<Node> firstAncestors = new HashSet<>();
        Node current = first;
        while (current != null) {
            firstAncestors.add(current);
            current = current.getParent();
        }

        current = second;
        while (current != null) {
            if (firstAncestors.contains(current)) {
                return (Parent) current;
            }
            current = current.getParent();
        }
        throw new IllegalArgumentException("Structure view and drawer layer must share a graph ancestor");
    }

    /** Переносить точку з локальної системи вузла до локальної системи заданого предка. */
    private static Point2D transformToAncestor(Node node, Parent ancestor, Point2D point) {
        Point2D transformed = point;
        Node current = node;
        while (current != ancestor) {
            transformed = current.localToParent(transformed);
            current = current.getParent();
        }
        return transformed;
    }
}
