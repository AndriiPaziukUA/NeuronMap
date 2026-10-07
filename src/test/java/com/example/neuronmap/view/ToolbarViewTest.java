package com.example.neuronmap.view;

import com.example.neuronmap.model.NeuronType;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ToolbarViewTest {

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

    @Test
    void speedFieldAcceptsOnlyNumericInputAndKeepsLeftAlignment()
            throws Exception {
        runOnFxThread(() -> {
            ToolbarView toolbar = toolbar();
            TextField field = speedField(toolbar);

            assertEquals(Pos.CENTER_LEFT, field.getAlignment());
            assertTrue(field.getStyle().contains("-fx-alignment: CENTER-LEFT;"));

            field.clear();
            field.replaceText(0, 0, "12abc");
            assertEquals("", field.getText());

            field.replaceText(0, 0, "12.5");
            assertEquals("12.5", field.getText());

            field.replaceText(0, 4, "12.5.7");
            assertEquals("12.5", field.getText());

            field.replaceText(0, 4, "-50");
            assertEquals("12.5", field.getText());
        });
    }

    @Test
    void speedFieldStaysBeforeDynamicSimulationControls() throws Exception {
        runOnFxThread(() -> {
            ToolbarView toolbar = toolbar();
            int speedIndex = indexOfSpeedField(toolbar);
            int pauseIndex = indexOfButton(toolbar, "❚❚");
            int stopIndex = indexOfButton(toolbar, "■");

            assertTrue(speedIndex < pauseIndex);
            assertTrue(speedIndex < stopIndex);
        });
    }

    private static ToolbarView toolbar() {
        return new ToolbarView(
                ignored -> { },
                () -> { },
                () -> { },
                () -> { },
                () -> { },
                () -> { },
                ignored -> { },
                650.0
        );
    }

    private static TextField speedField(ToolbarView toolbar) {
        return (TextField) toolbar.node().getChildren().stream()
                .filter(TextField.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private static int indexOfSpeedField(ToolbarView toolbar) {
        return indexOf(
                toolbar.node().getChildren(),
                node -> node instanceof TextField
        );
    }

    private static int indexOfButton(
            ToolbarView toolbar,
            String text
    ) {
        return indexOf(
                toolbar.node().getChildren(),
                node -> node instanceof Button button
                        && text.equals(button.getText())
        );
    }

    private static int indexOf(
            java.util.List<Node> nodes,
            java.util.function.Predicate<Node> predicate
    ) {
        for (int index = 0; index < nodes.size(); index++) {
            if (predicate.test(nodes.get(index))) {
                return index;
            }
        }
        return -1;
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

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }

        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
