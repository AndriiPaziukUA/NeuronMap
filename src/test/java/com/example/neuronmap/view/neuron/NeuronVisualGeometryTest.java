package com.example.neuronmap.view.neuron;

import javafx.geometry.Point2D;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє геометрію портів нейрона, вихідного трикутника та перетворення координат центра.
 */
final class NeuronVisualGeometryTest {

    private static final double EPSILON = 0.0001;

    /**
     * Перевіряє перетворення довільної точки центра на верхній лівий кут у тій самій системі координат.
     */
    @Test
    void convertsCenterToTopLeftWithoutChangingCoordinateSpace() {
        Point2D topLeft = NeuronVisualGeometry.topLeftFromCenter(
                new Point2D(100.0, 200.0)
        );

        assertEquals(30.0, topLeft.getX(), EPSILON);
        assertEquals(163.0, topLeft.getY(), EPSILON);
    }

    /**
     * Перевіряє, що перетворення центра коректно працює для координат, які виходять за початок площини.
     */
    @Test
    void convertsNegativeCenterCoordinates() {
        Point2D topLeft = NeuronVisualGeometry.topLeftFromCenter(
                new Point2D(-20.0, -10.0)
        );

        assertEquals(-90.0, topLeft.getX(), EPSILON);
        assertEquals(-47.0, topLeft.getY(), EPSILON);
    }

    /**
     * Перевіряє, що перетворювач відхиляє відсутню точку центра замість приховування помилки.
     */
    @Test
    void rejectsNullCenterPoint() {
        assertThrows(
                NullPointerException.class,
                () -> NeuronVisualGeometry.topLeftFromCenter(null)
        );
    }

    /**
     * Перевіряє, що вихідний трикутник центрований на правому краї нейрона й частково виходить за його межі.
     */
    @Test
    void outputTriangleIsCenteredOnRightEdgeWithHalfOutside() {
        Polygon triangle = new Polygon();
        NeuronVisualGeometry.positionOutputTriangle(triangle, false);

        assertEquals(
                NeuronVisualGeometry.WIDTH
                        - NeuronVisualGeometry.OUTPUT_PORT_SIZE / 2.0,
                minX(triangle),
                EPSILON
        );
        assertEquals(
                NeuronVisualGeometry.WIDTH
                        + NeuronVisualGeometry.OUTPUT_PORT_SIZE / 2.0,
                maxX(triangle),
                EPSILON
        );
        assertEquals(
                NeuronVisualGeometry.WIDTH
                        + NeuronVisualGeometry.OUTPUT_PORT_SIZE / 2.0,
                NeuronVisualGeometry.outputTipX(false),
                EPSILON
        );
    }

    /**
     * Перевіряє симетричне розташування вихідного трикутника на лівому краї розвернутого нейрона.
     */
    @Test
    void reversedOutputTriangleIsCenteredOnLeftEdgeWithHalfOutside() {
        Polygon triangle = new Polygon();
        NeuronVisualGeometry.positionOutputTriangle(triangle, true);

        assertEquals(
                -NeuronVisualGeometry.OUTPUT_PORT_SIZE / 2.0,
                minX(triangle),
                EPSILON
        );
        assertEquals(
                NeuronVisualGeometry.OUTPUT_PORT_SIZE / 2.0,
                maxX(triangle),
                EPSILON
        );
        assertEquals(
                -NeuronVisualGeometry.OUTPUT_PORT_SIZE / 2.0,
                NeuronVisualGeometry.outputTipX(true),
                EPSILON
        );
    }

    /**
     * Перевіряє збіг точок приєднання зв’язків із видимою геометрією портів.
     */
    @Test
    void connectionAnchorsMatchVisiblePortGeometry() {
        assertEquals(
                NeuronVisualGeometry.WIDTH
                        + NeuronVisualGeometry.OUTPUT_PORT_SIZE / 2.0,
                NeuronVisualGeometry.outputTipX(false),
                EPSILON
        );
        assertEquals(
                -NeuronVisualGeometry.OUTPUT_PORT_SIZE / 2.0,
                NeuronVisualGeometry.outputTipX(true),
                EPSILON
        );
        assertEquals(0.0, NeuronVisualGeometry.inputPortX(false), EPSILON);
        assertEquals(
                NeuronVisualGeometry.WIDTH,
                NeuronVisualGeometry.inputPortX(true),
                EPSILON
        );
    }

    /**
     * Перевіряє центрування вхідного порту на потрібному краї нейрона.
     */
    @Test
    void inputPortStaysCenteredOnTheCorrectNeuronEdge() {
        Circle normal = new Circle(NeuronVisualGeometry.INPUT_PORT_RADIUS);
        Circle reversed = new Circle(NeuronVisualGeometry.INPUT_PORT_RADIUS);

        NeuronVisualGeometry.positionInputPort(normal, false);
        NeuronVisualGeometry.positionInputPort(reversed, true);

        assertEquals(0.0, normal.getCenterX(), EPSILON);
        assertEquals(NeuronVisualGeometry.WIDTH, reversed.getCenterX(), EPSILON);
        assertEquals(NeuronVisualGeometry.HEIGHT / 2.0, normal.getCenterY(), EPSILON);
        assertEquals(NeuronVisualGeometry.HEIGHT / 2.0, reversed.getCenterY(), EPSILON);
        assertTrue(normal.getCenterX() != reversed.getCenterX());
    }

    /**
     * Повертає найменшу горизонтальну координату серед вершин трикутника.
     *
     * @param polygon трикутник, координати якого аналізуються.
     * @return найменша горизонтальна координата.
     */
    private static double minX(Polygon polygon) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < polygon.getPoints().size(); i += 2) {
            min = Math.min(min, polygon.getPoints().get(i));
        }
        return min;
    }

    /**
     * Повертає найбільшу горизонтальну координату серед вершин трикутника.
     *
     * @param polygon трикутник, координати якого аналізуються.
     * @return найбільша горизонтальна координата.
     */
    private static double maxX(Polygon polygon) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < polygon.getPoints().size(); i += 2) {
            max = Math.max(max, polygon.getPoints().get(i));
        }
        return max;
    }
}
