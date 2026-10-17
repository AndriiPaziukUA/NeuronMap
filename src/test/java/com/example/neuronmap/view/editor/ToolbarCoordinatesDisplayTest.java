package com.example.neuronmap.view.editor;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє відображення координат вказівника та відсутність зайвого підтвердження швидкості. */
final class ToolbarCoordinatesDisplayTest {

    /** Запускає JavaFX лише за потреби й не завершує його, бо інші тести використовують той самий потік. */
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

    /** Перевіряє очищення координат поза полотном та їх повторне відображення після повернення. */
    @Test
    void clearsUnavailableCoordinatesAndRestoresThemWhenAvailable() throws Exception {
        runOnFxThread(() -> {
            ToolbarView toolbar = new ToolbarView(
                    ignored -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    ignored -> { },
                    650.0
            );
            Label coordinates = toolbar.node().getChildren().stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .filter(label -> label.getStyleClass().contains("toolbar-hint"))
                    .findFirst()
                    .orElseThrow();

            toolbar.setCameraCoordinates(125.0, 75.5);
            assertEquals("X: 125, Y: 75.5", coordinates.getText());

            toolbar.setCameraCoordinates(Double.NaN, Double.NaN);
            assertEquals("", coordinates.getText());

            toolbar.setCameraCoordinates(10.0, 20.0);
            assertEquals("X: 10, Y: 20", coordinates.getText());
        });
    }

    /** Перевіряє, що підтвердження незміненої швидкості не викликає callback запису. */
    @Test
    void committingUnchangedSpeedDoesNotInvokePersistenceCallback() throws Exception {
        AtomicInteger speedChanges = new AtomicInteger();
        runOnFxThread(() -> {
            ToolbarView toolbar = new ToolbarView(
                    ignored -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    ignored -> speedChanges.incrementAndGet(),
                    650.0
            );
            TextField speedField = toolbar.node().getChildren().stream()
                    .filter(TextField.class::isInstance)
                    .map(TextField.class::cast)
                    .filter(field -> field.getStyleClass().contains("toolbar-speed-field"))
                    .findFirst()
                    .orElseThrow();

            speedField.setText("650.0");
            speedField.fireEvent(new ActionEvent());
            assertEquals(0, speedChanges.get());

            // Нове значення проєкту стає базовим для порівняння після перемикання.
            toolbar.setSimulationSpeedMillis(725.0);
            speedField.setText("725.0");
            speedField.fireEvent(new ActionEvent());
            assertEquals(0, speedChanges.get());

            speedField.setText("700");
            speedField.fireEvent(new ActionEvent());
            assertEquals(1, speedChanges.get());
        });
    }

    /** Виконує перевірку на потоці JavaFX та передає помилку тестовому потоку. */
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
            throw new AssertionError("Тест JavaFX завершився помилкою", failure[0]);
        }
    }
}
