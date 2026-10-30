package com.example.neuronmap.view.simulation;

import com.example.neuronmap.view.connection.ConnectionPulseSnapshot;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє геометрію окремого наконечника, його видимість і тривалість анімації. */
class PulseAnimationViewTest {

    /** Запускає JavaFX перед тестами, що працюють із графічними вузлами. */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /** Перевіряє оновлення геометрії наконечника за актуальним знімком без створення лінії. */
    @Test
    void pulseRefreshesGeometryFromCurrentSnapshot() throws Exception {
        runOnFxThread(() -> {
            ConnectionPulseSnapshot initial = snapshot(10, 20, 210, 20);
            AtomicReference<ConnectionPulseSnapshot> current = new AtomicReference<>(initial);
            PulseAnimationView pulse = new PulseAnimationView(initial, current::get);
            Polygon arrow = (Polygon) pulse.getChildren().getFirst();

            assertEquals(1, pulse.getChildren().size());
            assertEquals(21.0, arrow.getPoints().getFirst());
            assertEquals(20.0, arrow.getPoints().get(1));

            ConnectionPulseSnapshot moved = snapshot(210, 220, 410, 220);
            current.set(moved);
            pulse.refreshGeometry();

            assertEquals(221.0, arrow.getPoints().getFirst());
            assertEquals(220.0, arrow.getPoints().get(1));
            assertEquals(Color.RED, arrow.getFill());
            pulse.stop();
        });
    }

    /** Перевіряє, що основа трикутника стартує на початку зв'язку, а його вершина доходить до цілі. */
    @Test
    void arrowStartsAtConnectionBeginningAndMovesToItsEnd() throws Exception {
        runOnFxThread(() -> {
            ConnectionPulseSnapshot initial = snapshot(10, 20, 210, 20);
            PulseAnimationView pulse = new PulseAnimationView(initial, () -> initial);
            Polygon arrow = (Polygon) pulse.getChildren().getFirst();

            assertEquals(21.0, arrow.getPoints().getFirst());
            assertEquals(20.0, arrow.getPoints().get(1));
            assertEquals(10.0, arrow.getPoints().get(2));
            assertEquals(25.5, arrow.getPoints().get(3));
            assertEquals(10.0, arrow.getPoints().get(4));
            assertEquals(14.5, arrow.getPoints().get(5));

            pulse.updatePulsePosition(0.5);
            assertEquals(115.5, arrow.getPoints().getFirst());
            assertEquals(20.0, arrow.getPoints().get(1));
            assertEquals(104.5, arrow.getPoints().get(2));
            assertEquals(25.5, arrow.getPoints().get(3));
            assertEquals(104.5, arrow.getPoints().get(4));
            assertEquals(14.5, arrow.getPoints().get(5));
            assertEquals(1, pulse.getChildren().size());

            pulse.updatePulsePosition(1.0);
            assertEquals(210.0, arrow.getPoints().getFirst());

            pulse.updatePulsePosition(-1.0);
            assertEquals(21.0, arrow.getPoints().getFirst());

            pulse.updatePulsePosition(2.0);
            assertEquals(210.0, arrow.getPoints().getFirst());
            pulse.stop();
        });
    }

    /** Перевіряє, що наконечник не згасає під час руху й вилучається після завершення анімації. */
    @Test
    void arrowRemainsOpaqueUntilItDisappearsAtAnimationEnd() throws Exception {
        CountDownLatch sampled = new CountDownLatch(1);
        CountDownLatch animationFinished = new CountDownLatch(1);
        AtomicReference<Pane> layerReference = new AtomicReference<>();
        AtomicReference<PulseAnimationView> pulseReference = new AtomicReference<>();
        AtomicReference<Double> sampledOpacity = new AtomicReference<>();
        AtomicReference<Color> sampledFill = new AtomicReference<>();

        runOnFxThread(() -> {
            ConnectionPulseSnapshot initial = snapshot(10, 20, 210, 20);
            Pane layer = new Pane();
            PulseAnimationView pulse = new PulseAnimationView(
                    initial,
                    () -> initial,
                    350.0,
                    animationFinished::countDown
            );
            Polygon arrow = (Polygon) pulse.getChildren().getFirst();
            layer.getChildren().add(pulse);
            layerReference.set(layer);
            pulseReference.set(pulse);
            pulse.play();

            PauseTransition sample = new PauseTransition(Duration.millis(290.0));
            sample.setOnFinished(_ -> {
                sampledOpacity.set(arrow.getOpacity());
                sampledFill.set((Color) arrow.getFill());
                sampled.countDown();
            });
            sample.play();
        });

        assertTrue(sampled.await(5, TimeUnit.SECONDS));
        assertEquals(1.0, sampledOpacity.get().doubleValue());
        assertEquals(Color.RED, sampledFill.get());
        assertTrue(animationFinished.await(5, TimeUnit.SECONDS));

        runOnFxThread(() -> {
            assertTrue(pulseReference.get().isFinished());
            assertTrue(layerReference.get().getChildren().isEmpty());
            assertEquals(1, pulseReference.get().getChildren().size());
        });
    }

    /** Перевіряє зміну тривалості імпульсу та відхилення некоректних часових значень. */
    @Test
    void tickDurationControlsAnimationDurationAndRejectsInvalidValues() throws Exception {
        runOnFxThread(() -> {
            ConnectionPulseSnapshot snapshot = snapshot(10, 20, 210, 20);
            PulseAnimationView pulse = new PulseAnimationView(snapshot, () -> snapshot, 125.0, () -> { });
            try {
                assertEquals(125.0, pulse.getTickDurationMillis());

                pulse.setTickDurationMillis(250.0);
                assertEquals(250.0, pulse.getTickDurationMillis());

                assertThrows(IllegalArgumentException.class, () -> pulse.setTickDurationMillis(0.0));
                assertThrows(IllegalArgumentException.class, () -> pulse.setTickDurationMillis(Double.NaN));
                assertThrows(IllegalArgumentException.class,
                        () -> pulse.setTickDurationMillis(Double.POSITIVE_INFINITY));
                assertThrows(IllegalArgumentException.class,
                        () -> pulse.setTickDurationMillis(Double.MIN_VALUE));
                assertEquals(250.0, pulse.getTickDurationMillis());
            } finally {
                pulse.stop();
            }
        });
    }

    /** Створює знімок геометрії зв'язку з указаними кінцевими точками та тестовими кольорами. */
    private static ConnectionPulseSnapshot snapshot(
            double startX,
            double startY,
            double endX,
            double endY
    ) {
        return new ConnectionPulseSnapshot(
                new Point2D(startX, startY),
                new Point2D(endX, endY),
                Color.RED,
                Color.BLACK
        );
    }

    /** Виконує перевірку в JavaFX-потоці та передає помилки до потоку JUnit. */
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

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
