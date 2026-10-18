package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє, що кольори й стан вибору нейрона застосовуються його незалежною таблицею стилів.
 */
final class NeuronViewStylesTest {

    /**
     * Запускає JavaFX toolkit перед перевірками CSS-властивостей графічних вузлів.
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
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /**
     * Переконується, що тип нейрона та вибір змінюють лише CSS-стан його власного графічного тіла.
     */
    @Test
    void cssControlsNeuronTypeAndSelectionColors() throws Exception {
        runOnFxThread(() -> {
            NeuronView excitatory = createView("style-excitatory", NeuronType.EXCITATORY);
            NeuronView inhibitory = createView("style-inhibitory", NeuronType.INHIBITORY);
            HBox root = new HBox(excitatory, inhibitory);
            Scene scene = new Scene(root, 400.0, 200.0);
            scene.getStylesheets().add(
                    NeuronViewStylesTest.class.getResource("/app.css").toExternalForm());
            root.applyCss();

            Rectangle excitatoryBody = (Rectangle) excitatory.getChildren().getFirst();
            Rectangle inhibitoryBody = (Rectangle) inhibitory.getChildren().getFirst();
            assertTrue(excitatoryBody.getStyleClass().contains("neuron-card-excitatory"));
            assertTrue(inhibitoryBody.getStyleClass().contains("neuron-card-inhibitory"));
            assertEquals(Color.web("#328b55"), excitatoryBody.getFill());
            assertEquals(Color.web("#646971"), inhibitoryBody.getFill());
            assertEquals(Color.web("#25292f"), excitatoryBody.getStroke());
            assertEquals(2.0, excitatoryBody.getStrokeWidth(), 0.001);

            excitatory.refreshVisuals(true);
            root.applyCss();
            assertTrue(excitatoryBody.getStyleClass().contains("neuron-card-selected"));
            assertEquals(Color.WHITE, excitatoryBody.getStroke());
            assertEquals(2.5, excitatoryBody.getStrokeWidth(), 0.001);
        });
    }

    /**
     * Створює нейронний вузол заданого типу для перевірки таблиці стилів.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @return графічне подання нейрона.
     */
    private static NeuronView createView(String id, NeuronType type) {
        Neuron neuron = new Neuron(id, type, 0);
        return new NeuronView(new NeuronPresentation(neuron, 40.0, 50.0, 0.0));
    }

    /**
     * Виконує дію в потоці JavaFX і передає винятки до потоку JUnit.
     *
     * @param action дія для JavaFX.
     * @throws Exception якщо очікування JavaFX завершується помилкою або тайм-аутом.
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
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure[0] != null) {
            throw new AssertionError("Тест CSS подання нейрона завершився помилкою", failure[0]);
        }
    }
}
