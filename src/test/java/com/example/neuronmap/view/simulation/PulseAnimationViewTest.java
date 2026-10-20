package com.example.neuronmap.view.simulation;

import com.example.neuronmap.view.connection.ConnectionPulseSnapshot;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє геометрію імпульсу та залежність його тривалості від такту симуляції. */
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

    /** Перевіряє, що імпульс оновлює свою геометрію за актуальним знімком, не малюючи повну лінію зв'язку. */
    @Test
    void pulseRefreshesGeometryFromCurrentSnapshot() throws Exception {
        runOnFxThread(() -> {
            ConnectionPulseSnapshot initial = snapshot(10, 20, 210, 20);
            AtomicReference<ConnectionPulseSnapshot> current = new AtomicReference<>(initial);
            PulseAnimationView pulse = new PulseAnimationView(initial, current::get);
            Line line = (Line) pulse.getChildren().get(0);

            assertEquals(10.0, line.getStartX());
            assertEquals(42.0, line.getEndX());
            assertEquals(20.0, line.getStartY());
            assertEquals(20.0, line.getEndY());

            ConnectionPulseSnapshot moved = snapshot(210, 220, 410, 220);
            current.set(moved);
            pulse.refreshGeometry();

            assertEquals(210.0, line.getStartX());
            assertEquals(242.0, line.getEndX());
            assertEquals(220.0, line.getStartY());
            assertEquals(220.0, line.getEndY());
            pulse.stop();
        });
    }

    /** Перевіряє рух короткого імпульсу від початку до кінця зв'язку та обмеження прогресу. */
    @Test
    void pulseMovesAlongConnectionWithoutDrawingItsFullLength() throws Exception {
        runOnFxThread(() -> {
            ConnectionPulseSnapshot initial = snapshot(10, 20, 210, 20);
            PulseAnimationView pulse = new PulseAnimationView(initial, () -> initial);
            Line line = (Line) pulse.getChildren().get(0);

            pulse.updatePulsePosition(0.5);
            assertEquals(94.0, line.getStartX());
            assertEquals(126.0, line.getEndX());
            assertEquals(20.0, line.getStartY());
            assertEquals(20.0, line.getEndY());
            assertTrue(line.getEndX() - line.getStartX() < initial.end().distance(initial.start()));

            pulse.updatePulsePosition(1.0);
            assertEquals(178.0, line.getStartX());
            assertEquals(210.0, line.getEndX());

            pulse.updatePulsePosition(-1.0);
            assertEquals(10.0, line.getStartX());
            assertEquals(42.0, line.getEndX());

            pulse.updatePulsePosition(2.0);
            assertEquals(178.0, line.getStartX());
            assertEquals(210.0, line.getEndX());
            pulse.stop();
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
