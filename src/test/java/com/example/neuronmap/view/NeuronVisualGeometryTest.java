package com.example.neuronmap.view;

import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NeuronVisualGeometryTest {

    private static final double EPSILON = 0.0001;

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
        assertEquals(
                0.0,
                NeuronVisualGeometry.inputPortX(false),
                EPSILON
        );
        assertEquals(
                NeuronVisualGeometry.WIDTH,
                NeuronVisualGeometry.inputPortX(true),
                EPSILON
        );
    }

    @Test
    void inputPortStaysCenteredOnTheCorrectNeuronEdge() {
        Circle normal = new Circle(
                NeuronVisualGeometry.INPUT_PORT_RADIUS
        );
        Circle reversed = new Circle(
                NeuronVisualGeometry.INPUT_PORT_RADIUS
        );

        NeuronVisualGeometry.positionInputPort(normal, false);
        NeuronVisualGeometry.positionInputPort(reversed, true);

        assertEquals(0.0, normal.getCenterX(), EPSILON);
        assertEquals(
                NeuronVisualGeometry.WIDTH,
                reversed.getCenterX(),
                EPSILON
        );
        assertEquals(
                NeuronVisualGeometry.HEIGHT / 2.0,
                normal.getCenterY(),
                EPSILON
        );
        assertEquals(
                NeuronVisualGeometry.HEIGHT / 2.0,
                reversed.getCenterY(),
                EPSILON
        );

        assertTrue(normal.getCenterX() != reversed.getCenterX());
    }

    private static double minX(Polygon polygon) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < polygon.getPoints().size(); i += 2) {
            min = Math.min(min, polygon.getPoints().get(i));
        }
        return min;
    }

    private static double maxX(Polygon polygon) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < polygon.getPoints().size(); i += 2) {
            max = Math.max(max, polygon.getPoints().get(i));
        }
        return max;
    }
}
