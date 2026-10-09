package com.example.neuronmap.view;

import com.example.neuronmap.model.NeuronGroup;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.Map;

/**
 * Відображає групу нейронів і її межі на карті.
 */
public final class GroupView extends Pane {

    private static final double PADDING = 18.0;
    private static final double MIN_SIZE = 40.0;

    private final NeuronGroup group;
    private final Map<String, NeuronView> neuronViews;

    private final Rectangle border =
            new Rectangle();

    /**
     * Повертає результат операції «група відображення».
     *
     * @param group група нейронів.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public GroupView(
            NeuronGroup group,
            Map<String, NeuronView> neuronViews
    ) {
        if (group == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "group must not be null"
            );
        }

        if (neuronViews == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
     * Повертає результат операції «група».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronGroup group() {
        return group;
    }

/**
 * Обробляє «потрібні дані».
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

        /*
         * GroupView is deliberately positioned in the same parent as the
         * neuron views. Its own origin is moved to the group bounds.
         */
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
