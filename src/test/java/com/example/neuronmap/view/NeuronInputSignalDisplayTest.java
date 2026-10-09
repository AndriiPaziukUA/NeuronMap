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
 * Перевіряє показ і очищення позначення вхідного сигналу на нейроні.
 */
final class NeuronInputSignalDisplayTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }

        if (!latch.await(5, TimeUnit.SECONDS)) {

            throw new IllegalStateException(
                    "JavaFX startup timed out"
            );
        }
    }

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

            throw new IllegalStateException(
                    "JavaFX test timed out"
            );
        }

        if (failure[0] != null) {

            throw new AssertionError(
                    "JavaFX test failed",
                    failure[0]
            );
        }
    }
}
