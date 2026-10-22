package com.example.neuronmap.view.editor;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.application.Platform;
import javafx.css.StyleOrigin;
import javafx.css.StyleableProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TextField;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє склад панелі інструментів, введення швидкості, CSS-оформлення та значки симуляції.
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

    /** Перевіряє векторну геометрію значків і перемикання між паузою та продовженням симуляції. */
    @Test
    void simulationControlsUseTightlySpacedPauseBarsAndLargerResumeTriangle() throws Exception {
        runOnFxThread(() -> {
            ToolbarView toolbar = toolbar();
            Button button = simulationButton(toolbar, "simulation-icon-button");
            Node graphic = button.getGraphic();
            assertNotNull(graphic, "Кнопка паузи має містити векторний значок");
            assertTrue(graphic instanceof SVGPath, "Значок має бути масштабованим векторним контуром");
            SVGPath icon = (SVGPath) graphic;
            Scene scene = new Scene(toolbar.node(), 800.0, 100.0);
            scene.getStylesheets().add(ToolbarViewTest.class.getResource("/app.css").toExternalForm());
            scene.getRoot().applyCss();

            assertEquals(ContentDisplay.GRAPHIC_ONLY, button.getContentDisplay());
            assertTrue(button.getText().isBlank(), "Значок керування не повинен залишатися Unicode-текстом");
            assertEquals(button.getTooltip().getText(), button.getAccessibleText(),
                    "Доступна назва кнопки має відповідати поточній дії");
            assertEquals(StyleOrigin.AUTHOR, ((StyleableProperty<?>) icon.fillProperty()).getStyleOrigin());
            assertEquals("M 0 0 H 4 V 14 H 0 Z M 6 0 H 10 V 14 H 6 Z", icon.getContent());
            assertEquals(10.0, icon.getBoundsInLocal().getWidth());
            assertEquals(14.0, icon.getBoundsInLocal().getHeight());

            toolbar.setSimulationPaused(true);
            assertTrue(button.getText().isBlank(), "Під час продовження значок також має залишатися графічним");
            assertEquals(button.getTooltip().getText(), button.getAccessibleText(),
                    "Доступна назва має оновлюватися разом зі станом кнопки");
            assertEquals("M 0 0 L 13 7 L 0 14 Z", icon.getContent(),
                    "Трикутник продовження має мати збільшену векторну геометрію");
            assertEquals(13.0, icon.getBoundsInLocal().getWidth());
            assertEquals(14.0, icon.getBoundsInLocal().getHeight());

            toolbar.setSimulationPaused(false);
            assertEquals("M 0 0 H 4 V 14 H 0 Z M 6 0 H 10 V 14 H 6 Z", icon.getContent());
            toolbar.dispose();
        });
    }

    /** Перевіряє, що квадрат кнопки зупинки сигналів задано вектором, а не текстовим символом. */
    @Test
    void stopSignalsControlUsesVectorIconInsteadOfUnicodeText() throws Exception {
        runOnFxThread(() -> {
            AtomicInteger stopCalls = new AtomicInteger();
            ToolbarView toolbar = toolbar(stopCalls::incrementAndGet);
            Scene scene = new Scene(toolbar.node(), 800.0, 100.0);
            scene.getStylesheets().add(ToolbarViewTest.class.getResource("/app.css").toExternalForm());
            scene.getRoot().applyCss();
            Button button = simulationButton(toolbar, "simulation-icon-button-danger");
            assertTrue(button.getText().isBlank(), "Кнопка зупинки не повинна містити Unicode-символ");
            assertEquals(ContentDisplay.GRAPHIC_ONLY, button.getContentDisplay());
            assertEquals(button.getTooltip().getText(), button.getAccessibleText(),
                    "Кнопка зупинки повинна зберігати доступну назву після заміни символу на вектор");
            SVGPath icon = (SVGPath) button.getGraphic();
            assertEquals("M 1 1 H 12 V 12 H 1 Z", icon.getContent());
            assertEquals(11.0, icon.getBoundsInLocal().getWidth());
            assertEquals(11.0, icon.getBoundsInLocal().getHeight());
            button.fire();
            assertEquals(1, stopCalls.get(), "Векторний значок не повинен змінювати дію кнопки");
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
            int pauseIndex = indexOfButtonWithClass(toolbar, "simulation-icon-button");
            int stopIndex = indexOfButtonWithClass(toolbar, "simulation-icon-button-danger");
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
        return toolbar(() -> { });
    }

    /** Створює панель із переданою дією зупинки для перевірки збереження поведінки кнопки. */
    private static ToolbarView toolbar(Runnable stopSignals) {
        return new ToolbarView(
                ignored -> { },
                () -> { },
                () -> { },
                () -> { },
                stopSignals,
                ignored -> { },
                650.0,
                new LocalizationService(Locale.forLanguageTag("uk"))
        );
    }

    /** Повертає кнопку керування симуляцією за її CSS-класом. */
    private static Button simulationButton(ToolbarView toolbar, String styleClass) {
        return toolbar.node().getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getStyleClass().contains(styleClass))
                .findFirst()
                .orElseThrow();
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

    /** Знаходить індекс кнопки за CSS-класом, не залежачи від її графічного вмісту. */
    private static int indexOfButtonWithClass(ToolbarView toolbar, String styleClass) {
        return indexOf(toolbar.node().getChildren(), node -> node instanceof Button button
                && button.getStyleClass().contains(styleClass));
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
