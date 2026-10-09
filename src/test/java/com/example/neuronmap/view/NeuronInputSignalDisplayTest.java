package com.example.neuronmap.view;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Перевіряє відображення суми вхідних сигналів і її скидання.
 */
final class NeuronInputSignalDisplayTest {

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
     * Перевіряє очікувану поведінку: вхід сигнал повертає до коли тимчасовий стан.
     */
    @Test
    void inputSignalReturnsToZeroWhenTemporaryStateIsCleared()
            throws Exception {
        runOnFxThread(() -> {
            Neuron neuron = new Neuron(
                    "input-display-test",
                    NeuronType.EXCITATORY,
                    0
            );

            NeuronPresentation presentation =
                    new NeuronPresentation(
                            neuron,
                            100.0,
                            80.0,
                            0.0
                    );

            NeuronView view = new NeuronView(presentation);
            Label inputLabel = (Label) view.getChildren().get(1);

            assertEquals("0", inputLabel.getText());

            view.showInputSignal(7);
            assertEquals("7", inputLabel.getText());

            view.refreshVisuals(false);
            assertEquals("7", inputLabel.getText());

            view.hideInputSignal();
            assertEquals("0", inputLabel.getText());

            view.refreshVisuals(false);
            assertEquals("0", inputLabel.getText());
        });
    }

    /**
     * Перевіряє очікувану поведінку: очистити вхід сигнал значення.
     */
    @Test
    void clearDisplayedInputSignalAlsoLeavesActualZeroValue()
            throws Exception {
        runOnFxThread(() -> {
            Neuron neuron = new Neuron(
                    "input-display-clear-test",
                    NeuronType.EXCITATORY,
                    0
            );

            NeuronPresentation presentation =
                    new NeuronPresentation(
                            neuron,
                            100.0,
                            80.0,
                            0.0
                    );

            NeuronView view = new NeuronView(presentation);
            Label inputLabel = (Label) view.getChildren().get(1);

            view.showInputSignal(11);
            assertEquals("11", inputLabel.getText());

            view.clearDisplayedInputSignal();

            assertEquals("0", inputLabel.getText());
        });
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
