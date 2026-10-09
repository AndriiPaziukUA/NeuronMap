package com.example.neuronmap.view;

import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;

/**
 * Обчислює геометричні параметри графічного представлення нейрона.
 */
public final class NeuronVisualGeometry {

    public static final double WIDTH = 140.0;
    public static final double HEIGHT = 74.0;
    public static final double INPUT_PORT_RADIUS = 7.0;
    public static final double OUTPUT_PORT_SIZE = 14.0;

    /**
     * Повертає результат операції «нейрон графічний геометрія».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private NeuronVisualGeometry() {
    }

    /**
     * Виконує операцію «положення вхід».
     *
     * @param port значення, що визначає відповідну операцію для цієї операції.
     *
     * @param directionReversed значення, що визначає напрямок для цієї операції.
     */
    public static void positionInputPort(
            Circle port,
            boolean directionReversed
    ) {
        if (port == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("port must not be null");
        }

        port.setCenterX(inputPortX(directionReversed));
        port.setCenterY(HEIGHT / 2.0);
    }

    /**
     * Виконує операцію «положення вихід трикутник».
     *
     * @param triangle значення, що визначає трикутник для цієї операції.
     *
     * @param directionReversed значення, що визначає напрямок для цієї операції.
     */
    public static void positionOutputTriangle(
            Polygon triangle,
            boolean directionReversed
    ) {
        if (triangle == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
     * Повертає результат операції «вхід».
     *
     * @param directionReversed значення, що визначає напрямок для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    public static double inputPortX(boolean directionReversed) {
        return directionReversed ? WIDTH : 0.0;
    }

    /**
     * Повертає результат операції «вихід».
     *
     * @param directionReversed значення, що визначає напрямок для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    public static double outputTipX(boolean directionReversed) {
        double half = OUTPUT_PORT_SIZE / 2.0;
        return directionReversed
                ? -half
                : WIDTH + half;
    }
}
