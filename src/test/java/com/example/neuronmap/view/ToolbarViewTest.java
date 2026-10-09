package com.example.neuronmap.view;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє склад і порядок елементів панелі інструментів.
 */
final class ToolbarViewTest {

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

        if (!latch.await(5, TimeUnit.SECONDS)) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /**
     * Перевіряє очікувану поведінку: швидкість поле приймає лише вхід і зберігає вирівнювання.
     */
    @Test
    void speedFieldAcceptsOnlyNumericInputAndKeepsLeftAlignment()
            throws Exception {
        runOnFxThread(() -> {
            ToolbarView toolbar = toolbar();
            TextField field = speedField(toolbar);

            assertEquals(Pos.CENTER_LEFT, field.getAlignment());
            assertTrue(
                    field.getStyle().contains(
                            "-fx-alignment: CENTER-LEFT;"
                    )
            );

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

    /**
     * Перевіряє очікувану поведінку: швидкість поле перед.
     */
    @Test
    void speedFieldStaysBeforeDynamicSimulationControls()
            throws Exception {
        runOnFxThread(() -> {
            ToolbarView toolbar = toolbar();

            int speedIndex = indexOfSpeedField(toolbar);
            int pauseIndex = indexOfButton(toolbar, "❚❚");
            int stopIndex = indexOfButton(toolbar, "■");

            assertTrue(speedIndex >= 0);
            assertTrue(pauseIndex >= 0);
            assertTrue(stopIndex >= 0);

            assertTrue(speedIndex < pauseIndex);
            assertTrue(speedIndex < stopIndex);
        });
    }

    /**
     * Повертає результат операції «панель інструментів».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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

    /**
     * Повертає результат операції «швидкість поле».
     *
     * @param toolbar значення, що визначає панель інструментів для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static TextField speedField(ToolbarView toolbar) {
        return (TextField) toolbar.node().getChildren().stream()
                .filter(TextField.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Повертає результат операції «швидкість поле».
     *
     * @param toolbar значення, що визначає панель інструментів для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    private static int indexOfSpeedField(ToolbarView toolbar) {
        return indexOf(
                toolbar.node().getChildren(),
                node -> node instanceof TextField
        );
    }

    /**
     * Повертає результат операції «кнопка».
     *
     * @param toolbar значення, що визначає панель інструментів для цієї операції.
     *
     * @param text текст, який потрібно показати або обробити.
     *
     * @return числове значення, визначене методом.
     */
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

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param nodes значення, що визначає відповідну операцію для цієї операції.
     *
     * @param predicate значення, що визначає відповідну операцію для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    private static int indexOf(
            List<Node> nodes,
            Predicate<Node> predicate
    ) {
        for (int index = 0; index < nodes.size(); index++) {
            if (predicate.test(nodes.get(index))) {
                return index;
            }
        }

        return -1;
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

        if (!latch.await(5, TimeUnit.SECONDS)) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException("JavaFX test timed out");
        }

        if (failure[0] != null) {
            /**
             * Повертає результат операції «відповідну операцію».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new AssertionError(
                    "JavaFX test failed",
                    failure[0]
            );
        }
    }
}
