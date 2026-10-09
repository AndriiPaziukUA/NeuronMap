package com.example.neuronmap.view;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє геометрію лінії анімації імпульсу за заданим знімком зв’язку.
 */
class PulseAnimationViewTest {

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

    @Test
    void pulseRefreshesGeometryFromCurrentSnapshot() throws Exception {
        runOnFxThread(() -> {
            ConnectionPulseSnapshot initial = snapshot(10, 20, 100, 120);
            AtomicReference<ConnectionPulseSnapshot> current =
                    new AtomicReference<>(initial);

            PulseAnimationView pulse = new PulseAnimationView(
                    initial,
                    current::get
            );
            Line line = (Line) pulse.getChildren().get(0);

            assertLineMatches(line, initial);

            ConnectionPulseSnapshot moved = snapshot(210, 220, 300, 320);
            current.set(moved);
            pulse.refreshGeometry();

            assertLineMatches(line, moved);
            pulse.stop();
        });
    }

    /**
     * Перевіряє сценарій «snapshot» і відповідність результату очікуваній поведінці.
     */
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

    /**
     * Перевіряє сценарій «assert line matches» і відповідність результату очікуваній поведінці.
     */
    private static void assertLineMatches(
            Line line,
            ConnectionPulseSnapshot snapshot
    ) {
        assertEquals(snapshot.start().getX(), line.getStartX());
        assertEquals(snapshot.start().getY(), line.getStartY());
        assertEquals(snapshot.end().getX(), line.getEndX());
        assertEquals(snapshot.end().getY(), line.getEndY());
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

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) {

            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
