package com.example.neuronmap.view;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.shape.Polygon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Перевіряє вирівнювання трикутника вихідного сигналу.
 */
final class NeuronOutputTriangleAlignmentTest {

    private static final double EPSILON = 0.0001;

    /**
     * Запускає або планує дію, повʼязану з «відповідну операцію».
     */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }

        if (!latch.await(5, TimeUnit.SECONDS)) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException(
                    "JavaFX startup timed out"
            );
        }
    }

    /**
     * Перевіряє очікувану поведінку: вихід трикутник вибраний для напрямок.
     */
    @Test
    void outputTriangleIsCenteredOnSelectedStrokeForNormalDirection()
            throws Exception {
        runOnFxThread(() -> {
            NeuronView view = neuronView(false);
            Polygon triangle = outputTriangle(view);

            assertEquals(
                    NeuronView.WIDTH
                            + NeuronView.SELECTED_STROKE_WIDTH / 2.0,
                    triangleCenterX(triangle),
                    EPSILON
            );
        });
    }

    /**
     * Перевіряє очікувану поведінку: вихід трикутник вибраний для напрямок.
     */
    @Test
    void outputTriangleIsCenteredOnSelectedStrokeForReversedDirection()
            throws Exception {
        runOnFxThread(() -> {
            NeuronView view = neuronView(true);
            Polygon triangle = outputTriangle(view);

            assertEquals(
                    -NeuronView.SELECTED_STROKE_WIDTH / 2.0,
                    triangleCenterX(triangle),
                    EPSILON
            );
        });
    }

    /**
     * Повертає результат операції «нейрон відображення».
     *
     * @param reversed значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static NeuronView neuronView(boolean reversed) {
        Neuron neuron = new Neuron(
                "triangle-alignment-test",
                NeuronType.EXCITATORY,
                0
        );

        NeuronPresentation presentation = new NeuronPresentation(
                neuron,
                100.0,
                80.0,
                0.0
        );

        if (reversed) {
            presentation.toggleDirection();
        }

        NeuronView view = new NeuronView(presentation);
        new Scene(view, 400.0, 300.0);
        view.layout();
        return view;
    }

    /**
     * Повертає результат операції «вихід трикутник».
     *
     * @param view значення, що визначає відображення для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static Polygon outputTriangle(NeuronView view) {
        for (Node child : view.getChildrenUnmodifiable()) {
            if (child instanceof Polygon polygon) {
                return polygon;
            }
        }

        /**
         * Повертає результат операції «відповідну операцію».
         *
         * @return значення або обʼєкт, визначений описаною операцією.
         */
        throw new AssertionError("Output triangle was not found");
    }

    /**
     * Повертає результат операції «трикутник центр».
     *
     * @param triangle значення, що визначає трикутник для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    private static double triangleCenterX(Polygon triangle) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < triangle.getPoints().size(); i += 2) {
            double x = triangle.getPoints().get(i);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
        }

        return (minX + maxX) / 2.0 + triangle.getTranslateX();
    }

    /**
     * Запускає або планує дію, повʼязану з «відповідну операцію».
     *
     * @param action дія, яку потрібно виконати.
     */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(5, TimeUnit.SECONDS)) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException(
                    "JavaFX test timed out"
            );
        }

        if (failure[0] != null) {
            /**
             * Повертає результат операції «відповідну операцію».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new AssertionError(
                    "JavaFX test failed",
                    failure[0]
            );
        }
    }
}
