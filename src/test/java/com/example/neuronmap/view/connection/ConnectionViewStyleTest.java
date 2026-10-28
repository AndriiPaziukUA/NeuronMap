package com.example.neuronmap.view.connection;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Перевіряє, що подання зв'язку застосовує колір лінії, наконечника та імпульсу відповідного типу джерела. */
final class ConnectionViewStyleTest {

    /** Запускає JavaFX toolkit перед створенням графічних вузлів. */
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

    /** Перевіряє передавання користувацького стилю лінії, зони вибору та знімку імпульсу. */
    @Test
    void appliesInjectedStyleToLineHitTargetAndPulseSnapshot() throws Exception {
        runOnFxThread(() -> {
            Neuron source = new Neuron("style-source", NeuronType.EXCITATORY, 0);
            Neuron target = new Neuron("style-target", NeuronType.INHIBITORY, 0);
            NeuronView sourceView = new NeuronView(new NeuronPresentation(source, 100.0, 100.0, 0.0));
            NeuronView targetView = new NeuronView(new NeuronPresentation(target, 360.0, 100.0, 0.0));
            Map<String, NeuronView> views = Map.of(source.id(), sourceView, target.id(), targetView);
            Map<String, Neuron> neurons = Map.of(source.id(), source, target.id(), target);
            Connection connection = new Connection("style-connection", source.id(), target.id());
            ConnectionStyle style = new ConnectionStyle(
                    Color.BLUE, Color.RED, Color.GRAY, Color.LIME, Color.PINK, Color.YELLOW,
                    4.0, 22.0, 15.0, 7.0);

            ConnectionView view = new ConnectionView(connection, views::get, neurons::get, style);
            Line visibleLine = (Line) view.getChildren().get(0);
            Line hitLine = (Line) view.getChildren().get(1);
            assertEquals(Color.BLUE, visibleLine.getStroke());
            assertEquals(4.0, visibleLine.getStrokeWidth(), 0.001);
            assertEquals(22.0, hitLine.getStrokeWidth(), 0.001);
            ConnectionPulseSnapshot snapshot = view.capturePulseSnapshot();
            assertNotNull(snapshot);
            assertEquals(Color.LIME, snapshot.pulseColor());
            assertEquals(Color.BLUE, snapshot.normalColor());
            sourceView.disposeParameterTooltip();
            targetView.disposeParameterTooltip();
        });
    }

    /** Перевіряє помаранчеві лінію й наконечник та блакитний сигнал структурного джерела. */
    @Test
    void usesOrangeStructureConnectionAndBlueSignal() throws Exception {
        runOnFxThread(() -> {
            Neuron source = new Neuron("structure-source", NeuronType.STRUCTURE, 0);
            Neuron target = new Neuron("structure-target", NeuronType.EXCITATORY, 0);
            NeuronView sourceView = new NeuronView(new NeuronPresentation(source, 100.0, 100.0, 0.0));
            NeuronView targetView = new NeuronView(new NeuronPresentation(target, 360.0, 100.0, 0.0));
            Map<String, NeuronView> views = Map.of(source.id(), sourceView, target.id(), targetView);
            Map<String, Neuron> neurons = Map.of(source.id(), source, target.id(), target);
            Connection connection = new Connection("structure-connection", source.id(), target.id());

            ConnectionView view = new ConnectionView(connection, views::get, neurons::get);
            Line visibleLine = (Line) view.getChildren().get(0);
            Polygon arrow = (Polygon) view.getChildren().get(2);
            ConnectionPulseSnapshot snapshot = view.capturePulseSnapshot();

            assertEquals(Color.web("#f59e0b"), visibleLine.getStroke());
            assertEquals(Color.web("#f59e0b"), arrow.getFill());
            assertNotNull(snapshot);
            assertEquals(Color.web("#38bdf8"), snapshot.pulseColor());
            assertEquals(Color.web("#f59e0b"), snapshot.normalColor());
            sourceView.disposeParameterTooltip();
            targetView.disposeParameterTooltip();
        });
    }

    /** Виконує графічну перевірку в JavaFX Application Thread і передає помилку до JUnit. */
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
            throw new AssertionError("Тест стилю зв'язку завершився помилкою", failure[0]);
        }
    }
}
