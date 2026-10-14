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
 * Перевіряє склад панелі інструментів, розташування поля швидкості та форматування його значення.
 */
final class ToolbarViewTest {

    /** Запускає JavaFX перед тестами, які створюють і перевіряють компоненти інтерфейсу. */
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

    /** Перевіряє обмеження введення чисел та вирівнювання текстового поля ліворуч. */
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

    /** Перевіряє, що поле швидкості розташовано перед динамічними елементами керування симуляцією. */
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

    /** Перевіряє відображення цілої, дробової та майже цілої тривалості такту в полі швидкості. */
    @Test
    void speedFieldUsesSharedCompactNumberFormatting() throws Exception {
        runOnFxThread(() -> {
            ToolbarView toolbar = toolbar();
            TextField field = speedField(toolbar);

            assertEquals("650", field.getText());

            toolbar.setSimulationSpeedMillis(125.5);
            assertEquals("125.5", field.getText());

            toolbar.setSimulationSpeedMillis(125.00005);
            assertEquals("125", field.getText());
        });
    }

    /** Створює панель інструментів із тестовими callback-ами, які не мають побічних ефектів. */
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

    /** Повертає текстове поле швидкості з кореневого вузла панелі інструментів. */
    private static TextField speedField(ToolbarView toolbar) {
        return (TextField) toolbar.node().getChildren().stream()
                .filter(TextField.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    /** Знаходить позицію текстового поля швидкості серед дочірніх вузлів панелі. */
    private static int indexOfSpeedField(ToolbarView toolbar) {
        return indexOf(
                toolbar.node().getChildren(),
                node -> node instanceof TextField
        );
    }

    /** Знаходить позицію кнопки з указаним текстом серед дочірніх вузлів панелі. */
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

    /** Повертає індекс першого вузла, що задовольняє предикат, або -1, якщо збігу немає. */
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

    /** Виконує дію в JavaFX Application Thread і передає помилку тесту в основний потік. */
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
            throw new AssertionError(
                    "JavaFX test failed",
                    failure[0]
            );
        }
    }
}
