package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє показ, очищення та колір індикатора вхідного сигналу на нейроні. */
final class NeuronInputSignalDisplayTest {

    /** Запускає JavaFX перед виконанням тестів, які створюють графічні вузли. */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /** Перевіряє, що тимчасово показаний вхідний сигнал знову відображається як нуль після очищення стану. */
    @Test
    void inputSignalReturnsToZeroWhenTemporaryStateIsCleared() throws Exception {
        runOnFxThread(() -> {
            Neuron neuron = new Neuron("input-display-test", NeuronType.EXCITATORY, 0);
            NeuronPresentation presentation = new NeuronPresentation(neuron, 100.0, 80.0, 0.0);
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

    /** Переконується, що метод очищення індикатора також прибирає тимчасове значення сигналу. */
    @Test
    void clearDisplayedInputSignalAlsoLeavesActualZeroValue() throws Exception {
        runOnFxThread(() -> {
            Neuron neuron = new Neuron("input-display-clear-test", NeuronType.EXCITATORY, 0);
            NeuronPresentation presentation = new NeuronPresentation(neuron, 100.0, 80.0, 0.0);
            NeuronView view = new NeuronView(presentation);
            Label inputLabel = (Label) view.getChildren().get(1);

            view.showInputSignal(11);
            assertEquals("11", inputLabel.getText());

            view.clearDisplayedInputSignal();

            assertEquals("0", inputLabel.getText());
        });
    }

    /**
     * Перевіряє червоний колір для недостатньої суми, жовтий для достатньої та скидання кольору.
     *
     * @throws Exception якщо JavaFX-потік не виконає перевірку вчасно.
     */
    @Test
    void inputPortColorTracksDisplayedSumAgainstActivationThreshold() throws Exception {
        runOnFxThread(() -> {
            Neuron neuron = new Neuron("input-port-color-test", NeuronType.EXCITATORY, 0, 1, 8);
            NeuronView view = new NeuronView(
                    new NeuronPresentation(neuron, 100.0, 80.0, 0.0));
            HBox root = new HBox(view);
            Scene scene = new Scene(root, 400.0, 200.0);
            scene.getStylesheets().add(
                    NeuronInputSignalDisplayTest.class.getResource("/app.css").toExternalForm());
            root.applyCss();

            Circle inputPort = (Circle) view.getChildren().get(3);
            assertEquals(Color.web("#d6d9de"), inputPort.getFill());

            view.showInputSignal(7);
            root.applyCss();
            assertEquals("7", ((Label) view.getChildren().get(1)).getText());
            assertEquals(Color.web("#ef4444"), inputPort.getFill(),
                    "Сума, менша за поріг активації, має підсвічувати порт червоним");
            assertTrue(inputPort.getStyleClass().contains("port-input-insufficient"));
            assertFalse(inputPort.getStyleClass().contains("port-input-sufficient"));

            view.showInputSignal(8);
            root.applyCss();
            assertEquals(Color.web("#facc15"), inputPort.getFill(),
                    "Сума, рівна порогу, уже достатня для активації");
            assertTrue(inputPort.getStyleClass().contains("port-input-sufficient"));
            assertFalse(inputPort.getStyleClass().contains("port-input-insufficient"));

            view.showInputSignal(-3);
            root.applyCss();
            assertEquals(Color.web("#ef4444"), inputPort.getFill(),
                    "Від’ємна сума не досягає додатного порога активації");

            view.hideInputSignal();
            root.applyCss();
            assertEquals(Color.web("#d6d9de"), inputPort.getFill(),
                    "Після приховування сигналу порт має повернутися до нейтрального кольору");
            assertFalse(inputPort.getStyleClass().contains("port-input-insufficient"));
            assertFalse(inputPort.getStyleClass().contains("port-input-sufficient"));
        });
    }

    /** Виконує дію в JavaFX-потоці та передає винятки до потоку JUnit. */
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
            throw new IllegalStateException("JavaFX test timed out");
        }

        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
