package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що інтерактивні області нейрона й круглої ручки відповідають видимій геометрії без візуальних ефектів. */
final class VisualHitAreaTest {

    /**
     * Перевіряє, що округлені кути контейнера не запускають взаємодію, а видимі форми нейрона залишаються інтерактивними.
     *
     * @throws Exception якщо перевірка не виконається в потоці JavaFX вчасно.
     */
    @Test
    void neuronHitAreaMatchesRoundedBodyAndVisiblePorts() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView view = createNeuronView();
            Rectangle body = view.getChildren().stream()
                    .filter(Rectangle.class::isInstance)
                    .map(Rectangle.class::cast)
                    .findFirst()
                    .orElseThrow();
            Circle inputPort = view.getChildren().stream()
                    .filter(Circle.class::isInstance)
                    .map(Circle.class::cast)
                    .findFirst()
                    .orElseThrow();
            Polygon outputPort = view.getChildren().stream()
                    .filter(Polygon.class::isInstance)
                    .map(Polygon.class::cast)
                    .findFirst()
                    .orElseThrow();

            assertFalse(view.isPickOnBounds(), "Контейнер не повинен приймати натискання за прямокутними межами");
            assertFalse(view.contains(0.5, 0.5), "Порожній округлений кут не є областю натискання");
            assertTrue(view.contains(NeuronView.WIDTH / 2.0, NeuronView.HEIGHT / 2.0),
                    "Видиме тіло нейрона має залишатися інтерактивним");
            assertFalse(body.isMouseTransparent(), "Видима форма тіла має брати участь у hit-testing");
            assertFalse(inputPort.isMouseTransparent(), "Видима форма вхідного порту має брати участь у hit-testing");
            assertFalse(outputPort.isMouseTransparent(), "Видима форма вихідного порту має брати участь у hit-testing");
            assertTrue(view.contains(-3.0, NeuronView.HEIGHT / 2.0),
                    "Видима частина вхідного порту за краєм тіла також має реагувати на натискання");
            assertTrue(view.contains(NeuronView.WIDTH + 3.0, NeuronView.HEIGHT / 2.0),
                    "Видима частина вихідного порту за краєм тіла також має реагувати на натискання");
        });
    }

    /**
     * Переконується, що світіння вибраного нейрона не розширює фактичну область натискання.
     *
     * @throws Exception якщо перевірка не виконається в потоці JavaFX вчасно.
     */
    @Test
    void selectedNeuronGlowDoesNotExpandHitArea() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView view = createNeuronView();
            StackPane root = new StackPane(view);
            Scene scene = new Scene(root, NeuronView.WIDTH + 40.0, NeuronView.HEIGHT + 40.0);
            scene.getStylesheets().add(
                    VisualHitAreaTest.class.getResource("/app.css").toExternalForm());
            view.refreshVisuals(true);
            root.applyCss();

            Rectangle body = (Rectangle) view.getChildren().getFirst();
            assertInstanceOf(DropShadow.class, body.getEffect(), "Вибраний нейрон має зберігати світіння");
            assertFalse(view.contains(NeuronView.WIDTH / 2.0, -5.0),
                    "Світіння за верхнім краєм не повинно ставати областю натискання");
            assertFalse(view.contains(NeuronView.WIDTH / 2.0, NeuronView.HEIGHT + 5.0),
                    "Світіння за нижнім краєм не повинно ставати областю натискання");
        });
    }

    /**
     * Перевіряє, що кругла ручка приймає натискання в колі, але не в прозорих кутах її квадратних меж.
     *
     * @throws Exception якщо перевірка не виконається в потоці JavaFX вчасно.
     */
    @Test
    void rotationHandleHitAreaMatchesVisibleCircle() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            RotationHandleView handle = new RotationHandleView();

            assertFalse(handle.isPickOnBounds(), "Кнопка не повинна використовувати прямокутну область попадання");
            assertFalse(handle.contains(0.0, 0.0), "Прозорий кут круглої кнопки не повинен реагувати на натискання");
            assertFalse(handle.contains(1.0, 1.0), "Область поза видимим колом не повинна реагувати на натискання");
            assertTrue(handle.contains(RotationHandleView.SIZE / 2.0, RotationHandleView.SIZE / 2.0),
                    "Центр видимого кола має залишатися інтерактивним");
            handle.dispose();
        });
    }

    /** Створює нейрон із фіксованою геометрією для незалежних перевірок hit-testing. */
    private static NeuronView createNeuronView() {
        Neuron neuron = new Neuron("hit-area-neuron", NeuronType.EXCITATORY, 0);
        NeuronPresentation presentation = new NeuronPresentation(neuron, 0.0, 0.0, 0.0);
        return new NeuronView(presentation);
    }
}
