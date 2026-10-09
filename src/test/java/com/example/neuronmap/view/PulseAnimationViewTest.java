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
 * Перевіряє рух імпульсу після зміни геометрії звʼязку.
 */
class PulseAnimationViewTest {

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
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Перевіряє очікувану поведінку: імпульс геометрія із поточний знімок.
     */
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
     * Повертає результат операції «знімок».
     *
     * @param startX значення, що визначає запуск для цієї операції.
     *
     * @param startY значення, що визначає запуск для цієї операції.
     *
     * @param endX значення, що визначає відповідну операцію для цієї операції.
     *
     * @param endY значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Виконує операцію «відповідну операцію».
     *
     * @param line значення, що визначає відповідну операцію для цієї операції.
     *
     * @param snapshot знімок стану.
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

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) {
            /**
             * Повертає результат операції «відповідну операцію».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
