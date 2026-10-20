package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє застосування CSS-стилів нейрона та стабільність його рамки під час зміни вибору.
 */
final class NeuronViewStylesTest {

    private static final double EPSILON = 0.001;

    /**
     * Переконується, що тип нейрона та вибір змінюють лише CSS-стан його власного графічного тіла.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void cssControlsNeuronTypeAndSelectionColors() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
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
            assertEquals(2.0, excitatoryBody.getStrokeWidth(), EPSILON);
            assertBodyRemainsAtLocalOrigin(excitatoryBody);

            excitatory.refreshVisuals(true);
            root.applyCss();
            assertTrue(excitatoryBody.getStyleClass().contains("neuron-card-selected"));
            assertEquals(Color.WHITE, excitatoryBody.getStroke());
            assertEquals(2.5, excitatoryBody.getStrokeWidth(), EPSILON);
            assertBodyRemainsAtLocalOrigin(excitatoryBody);

            // Повторюване оновлення імітує оновлення подань під час активної симуляції.
            excitatory.refreshVisuals(true);
            root.applyCss();
            assertBodyRemainsAtLocalOrigin(excitatoryBody);

            excitatory.refreshVisuals(false);
            root.applyCss();
            assertBodyRemainsAtLocalOrigin(excitatoryBody);
        });
    }

    /**
     * Перевіряє, що CSS-рамка не зсуває прямокутник від незмінного локального початку координат.
     *
     * @param body графічне тіло нейрона.
     */
    private static void assertBodyRemainsAtLocalOrigin(Rectangle body) {
        assertEquals(0.0, body.getLayoutX(), EPSILON,
                "CSS-рамка не повинна змінювати горизонтальне розташування тіла");
        assertEquals(0.0, body.getLayoutY(), EPSILON,
                "CSS-рамка не повинна змінювати вертикальне розташування тіла");
        assertEquals(0.0, body.getX(), EPSILON,
                "Ліва межа тіла має залишатися в локальній координаті X = 0");
        assertEquals(0.0, body.getY(), EPSILON,
                "Верхня межа тіла має залишатися в локальній координаті Y = 0");
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
}
