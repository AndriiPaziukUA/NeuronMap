package com.example.neuronmap.view.editor;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.application.Platform;
import javafx.css.StyleOrigin;
import javafx.css.StyleableProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє склад панелі інструментів, введення швидкості та застосування її CSS-оформлення.
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

    /** Перевіряє числове введення й підтверджує, що вирівнювання задається CSS, а не inline-стилем. */
    @Test
    void speedFieldAcceptsOnlyNumericInputAndKeepsCssLeftAlignment() throws Exception {
        runOnFxThread(() -> {
            ToolbarView toolbar = toolbar();
            Scene scene = new Scene(toolbar.node(), 800.0, 100.0);
            scene.getStylesheets().add(ToolbarViewTest.class.getResource("/app.css").toExternalForm());
            scene.getRoot().applyCss();
            TextField field = speedField(toolbar);

            assertEquals(Pos.CENTER_LEFT, field.getAlignment());
            assertEquals(
                    StyleOrigin.AUTHOR,
                    ((StyleableProperty<?>) field.alignmentProperty()).getStyleOrigin());
            assertTrue(field.getStyle().isBlank());

            field.clear();
            field.replaceText(0, 0, "12abc");
            assertEquals("", field.getText());

            field.replaceText(0, 0, "12.5");
            assertEquals("12.5", field.getText());

            field.replaceText(0, 4, "12.5.7");
            assertEquals("12.5", field.getText());

            field.replaceText(0, 4, "-50");
            assertEquals("12.5", field.getText());
            toolbar.dispose();
        });
    }

    /** Перевіряє, що застаріла кнопка виходу з режиму видалення зв'язків не додається до панелі. */
    @Test
    void doesNotIncludeObsoleteConnectionDeletionExitButton() throws Exception {
        runOnFxThread(() -> {
            ToolbarView toolbar = toolbar();
            boolean hasObsoleteButton = toolbar.node().getChildren().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .anyMatch(button -> button.getStyleClass().contains("tool-button-danger"));
            assertFalse(hasObsoleteButton);
            toolbar.dispose();
        });
    }

    /** Перевіряє, що поле швидкості розташовано перед динамічними елементами керування симуляцією. */
    @Test
    void speedFieldStaysBeforeDynamicSimulationControls() throws Exception {
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
            toolbar.dispose();
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
            toolbar.dispose();
        });
    }

    /** Створює панель інструментів із тестовими callback-ами та явно переданою локалізацією. */
    private static ToolbarView toolbar() {
        return new ToolbarView(
                ignored -> { },
                () -> { },
                () -> { },
                () -> { },
                () -> { },
                ignored -> { },
                650.0,
                new LocalizationService(Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Повертає текстове поле швидкості з кореневого вузла панелі.
     *
     * @param toolbar панель інструментів.
     * @return поле швидкості.
     */
    private static TextField speedField(ToolbarView toolbar) {
        return (TextField) toolbar.node().getChildren().stream()
                .filter(TextField.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    /** Знаходить індекс текстового поля швидкості серед дочірніх вузлів панелі. */
    private static int indexOfSpeedField(ToolbarView toolbar) {
        return indexOf(toolbar.node().getChildren(), node -> node instanceof TextField);
    }

    /** Знаходить індекс кнопки з указаним текстом. */
    private static int indexOfButton(ToolbarView toolbar, String text) {
        return indexOf(toolbar.node().getChildren(), node -> node instanceof Button button && text.equals(button.getText()));
    }

    /** Повертає індекс першого вузла, що задовольняє умову. */
    private static int indexOf(List<Node> nodes, Predicate<Node> predicate) {
        for (int index = 0; index < nodes.size(); index++) {
            if (predicate.test(nodes.get(index))) {
                return index;
            }
        }
        return -1;
    }

    /** Виконує дію в потоці JavaFX і передає помилки до потоку JUnit. */
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
