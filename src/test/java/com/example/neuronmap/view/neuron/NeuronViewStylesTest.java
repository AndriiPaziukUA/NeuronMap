package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє кольори, підсвічування вибраного нейрона, рамку наведення та її незмінну товщину. */
final class NeuronViewStylesTest {

    private static final double EPSILON = 0.001;
    private static final PseudoClass HOVER_PSEUDO_CLASS = PseudoClass.getPseudoClass("hover");

    /**
     * Переконується, що вибір додає світіння, наведення лише змінює колір рамки, а її товщина не змінюється.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void cssControlsNeuronTypeSelectionGlowHoverBorderAndStableThickness() throws Exception {
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
            assertNull(excitatoryBody.getEffect(), "Невибраний нейрон не повинен світитися");
            assertBodyRemainsAtLocalOrigin(excitatoryBody);

            excitatory.refreshVisuals(true);
            root.applyCss();
            assertTrue(excitatoryBody.getStyleClass().contains("neuron-card-selected"));
            assertEquals(Color.WHITE, excitatoryBody.getStroke());
            assertEquals(2.0, excitatoryBody.getStrokeWidth(), EPSILON);
            assertInstanceOf(DropShadow.class, excitatoryBody.getEffect(),
                    "Вибраний нейрон має залишатися підсвіченим, поки активний вибір");
            assertBodyRemainsAtLocalOrigin(excitatoryBody);

            // Повторне оновлення імітує такти симуляції й не повинно прибирати світіння або змінювати геометрію.
            excitatory.refreshVisuals(true);
            root.applyCss();
            assertInstanceOf(DropShadow.class, excitatoryBody.getEffect());
            assertEquals(2.0, excitatoryBody.getStrokeWidth(), EPSILON);
            assertBodyRemainsAtLocalOrigin(excitatoryBody);

            inhibitory.pseudoClassStateChanged(HOVER_PSEUDO_CLASS, true);
            root.applyCss();
            assertEquals(Color.WHITE, inhibitoryBody.getStroke(),
                    "Наведення має показувати білу рамку навколо невибраного нейрона");
            assertEquals(2.0, inhibitoryBody.getStrokeWidth(), EPSILON);
            assertNull(inhibitoryBody.getEffect(), "Наведення не повинно підсвічувати тіло нейрона");
            assertBodyRemainsAtLocalOrigin(inhibitoryBody);

            inhibitory.pseudoClassStateChanged(HOVER_PSEUDO_CLASS, false);
            root.applyCss();
            assertEquals(Color.web("#25292f"), inhibitoryBody.getStroke(),
                    "Після завершення наведення має відновитися звичайна рамка");
            assertEquals(2.0, inhibitoryBody.getStrokeWidth(), EPSILON);
            assertNull(inhibitoryBody.getEffect());

            excitatory.refreshVisuals(false);
            root.applyCss();
            assertEquals(Color.web("#25292f"), excitatoryBody.getStroke());
            assertEquals(2.0, excitatoryBody.getStrokeWidth(), EPSILON);
            assertNull(excitatoryBody.getEffect(), "Після зняття вибору світіння має зникнути");
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
