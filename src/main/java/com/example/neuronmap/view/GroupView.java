package com.example.neuronmap.view;

import com.example.neuronmap.model.NeuronGroup;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.Map;

/**
 * Візуально об’єднує подання нейронів, що входять до однієї групи.
 */
public final class GroupView extends Pane {

    private static final double PADDING = 18.0;
    private static final double MIN_SIZE = 40.0;

    private final NeuronGroup group;
    private final Map<String, NeuronView> neuronViews;

    private final Rectangle border =
            new Rectangle();

    /**
     * Створює екземпляр GroupView та зберігає передані залежності, потрібні для його роботи.
     *
     * @param group значення «group», яке використовується в цьому методі.
     * @param neuronViews мапа візуальних подань нейронів за їхніми ідентифікаторами.
     */
    public GroupView(
            NeuronGroup group,
            Map<String, NeuronView> neuronViews
    ) {
        if (group == null) {

            throw new IllegalArgumentException(
                    "group must not be null"
            );
        }

        if (neuronViews == null) {

            throw new IllegalArgumentException(
                    "neuronViews must not be null"
            );
        }

        this.group = group;
        this.neuronViews = neuronViews;

        setManaged(false);
        setMouseTransparent(true);
        setPickOnBounds(false);

        border.setFill(Color.TRANSPARENT);
        border.setMouseTransparent(true);
        border.getStyleClass().add(
                "neuron-group-border"
        );

        getChildren().add(border);

        refresh();
    }

    /**
     * Повертає групу, яку відображає це подання.
     *
     * @return групу, яку відображає це подання.
     */
    public NeuronGroup group() {
        return group;
    }

/**
 * Перераховує межі групи за поточними позиціями нейронів-учасників.
 */
public void refresh() {
        if (group.memberIds().isEmpty()) {
            border.setWidth(MIN_SIZE);
            border.setHeight(MIN_SIZE);
            resizeRelocate(
                    0.0,
                    0.0,
                    MIN_SIZE,
                    MIN_SIZE
            );
            return;
        }

        boolean hasBounds = false;

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (String neuronId : group.memberIds()) {
            NeuronView neuronView =
                    neuronViews.get(neuronId);

            if (neuronView == null) {
                continue;
            }

            javafx.geometry.Bounds bounds =
                    neuronView.getBoundsInParent();

            minX = Math.min(
                    minX,
                    bounds.getMinX()
            );

            minY = Math.min(
                    minY,
                    bounds.getMinY()
            );

            maxX = Math.max(
                    maxX,
                    bounds.getMaxX()
            );

            maxY = Math.max(
                    maxY,
                    bounds.getMaxY()
            );

            hasBounds = true;
        }

        if (!hasBounds) {
            border.setWidth(MIN_SIZE);
            border.setHeight(MIN_SIZE);
            resizeRelocate(
                    0.0,
                    0.0,
                    MIN_SIZE,
                    MIN_SIZE
            );
            return;
        }

        double width = Math.max(
                MIN_SIZE,
                maxX - minX + PADDING * 2.0
        );

        double height = Math.max(
                MIN_SIZE,
                maxY - minY + PADDING * 2.0
        );

        resizeRelocate(
                minX - PADDING,
                minY - PADDING,
                width,
                height
        );

        border.setX(0.0);
        border.setY(0.0);
        border.setWidth(width);
        border.setHeight(height);
    }
}
