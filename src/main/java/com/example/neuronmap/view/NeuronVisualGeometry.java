package com.example.neuronmap.view;

import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;

/**
 * Визначає координати видимих вхідного порту та вихідного трикутника нейрона з урахуванням розвороту напрямку.
 */
public final class NeuronVisualGeometry {

    public static final double WIDTH = 140.0;
    public static final double HEIGHT = 74.0;
    public static final double INPUT_PORT_RADIUS = 7.0;
    public static final double OUTPUT_PORT_SIZE = 14.0;

    private NeuronVisualGeometry() {
    }

    /**
     * Розташовує вхідний порт на лівому або правому краї нейрона залежно від напрямку.
     *
     * @param port значення «port», яке використовується в цьому методі.
     * @param directionReversed ознака розвернутого напрямку.
     */
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

    /**
     * Розташовує вихідний трикутник на відповідному краї нейрона залежно від розвороту.
     *
     * @param triangle значення «triangle», яке використовується в цьому методі.
     * @param directionReversed ознака розвернутого напрямку.
     */
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

    /**
     * Обчислює горизонтальну координату вхідного порту з урахуванням напрямку нейрона.
     *
     * @param directionReversed ознака розвернутого напрямку.
     */
    public static double inputPortX(boolean directionReversed) {
        return directionReversed ? WIDTH : 0.0;
    }

    /**
     * Обчислює горизонтальну координату вістря вихідного трикутника з урахуванням напрямку нейрона.
     *
     * @param directionReversed ознака розвернутого напрямку.
     */
    public static double outputTipX(boolean directionReversed) {
        double half = OUTPUT_PORT_SIZE / 2.0;
        return directionReversed
                ? -half
                : WIDTH + half;
    }
}
