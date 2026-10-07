package com.example.neuronmap.view;

import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;

/** Shared geometry for the neuron body and its connection ports. */
public final class NeuronVisualGeometry {

    public static final double WIDTH = 140.0;
    public static final double HEIGHT = 74.0;
    public static final double INPUT_PORT_RADIUS = 7.0;
    public static final double OUTPUT_PORT_SIZE = 14.0;

    private NeuronVisualGeometry() {
    }

    public static void positionInputPort(
            Circle port,
            boolean directionReversed
    ) {
        if (port == null) {
            throw new IllegalArgumentException("port must not be null");
        }

        port.setCenterX(inputPortX(directionReversed));
        port.setCenterY(HEIGHT / 2.0);
    }

    public static void positionOutputTriangle(
            Polygon triangle,
            boolean directionReversed
    ) {
        if (triangle == null) {
            throw new IllegalArgumentException(
                    "triangle must not be null"
            );
        }

        double half = OUTPUT_PORT_SIZE / 2.0;
        double centerY = HEIGHT / 2.0;

        triangle.getPoints().clear();

        if (!directionReversed) {
            triangle.getPoints().addAll(
                    WIDTH + half, centerY,
                    WIDTH - half, centerY - half,
                    WIDTH - half, centerY + half
            );
        } else {
            triangle.getPoints().addAll(
                    -half, centerY,
                    half, centerY - half,
                    half, centerY + half
            );
        }
    }

    public static double inputPortX(boolean directionReversed) {
        return directionReversed ? WIDTH : 0.0;
    }

    public static double outputTipX(boolean directionReversed) {
        double half = OUTPUT_PORT_SIZE / 2.0;
        return directionReversed
                ? -half
                : WIDTH + half;
    }
}
