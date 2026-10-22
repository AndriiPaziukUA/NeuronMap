package com.example.neuronmap.view.editor;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє вигляд, розташування, видимість і дію кнопки однотактного продовження. */
final class ToolbarViewSingleStepButtonTest {

    /** Запускає JavaFX перед створенням елементів панелі інструментів. */
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

    /** Перевіряє трикутник із цифрою та розташування кнопки між продовженням і зупинкою. */
    @Test
    void rendersNumberedContinueTriangleBetweenPauseAndStop() throws Exception {
        runOnFxThread(() -> {
            ToolbarView toolbar = toolbar();
            try {
                Button button = toolbar.stepOnceButton();
                assertFalse(button.isVisible());
                assertFalse(button.isManaged());
                assertEquals("simulation-step-button", button.getStyleClass().stream()
                        .filter("simulation-step-button"::equals).findFirst().orElseThrow());
                assertEquals(indexOfButton(toolbar, "simulation-icon-button") + 1,
                        indexOfButton(toolbar, "simulation-step-button"));
                assertEquals(indexOfButton(toolbar, "simulation-step-button") + 1,
                        indexOfButton(toolbar, "simulation-icon-button-danger"));

                assertTrue(button.getGraphic() instanceof StackPane);
                StackPane graphic = (StackPane) button.getGraphic();
                SVGPath triangle = graphic.getChildren().stream()
                        .filter(SVGPath.class::isInstance).map(SVGPath.class::cast).findFirst().orElseThrow();
                Label number = graphic.getChildren().stream()
                        .filter(Label.class::isInstance).map(Label.class::cast).findFirst().orElseThrow();
                assertEquals("M 0 0 L 13 7 L 0 14 Z", triangle.getContent());
                assertEquals("1", number.getText());
                assertTrue(button.getText() == null || button.getText().isEmpty(),
                        "Кнопка містить лише піктограму без напису");

                Scene scene = new Scene(toolbar.node(), 900.0, 100.0);
                scene.getStylesheets().add(ToolbarViewSingleStepButtonTest.class
                        .getResource("/app.css").toExternalForm());
                scene.getRoot().applyCss();
                assertEquals(button.getTooltip().getText(), button.getAccessibleText());
                assertEquals("Пропустити такт", button.getTooltip().getText());

                toolbar.setSimulationControlsVisible(true);
                toolbar.setSimulationPaused(true);
                assertTrue(button.isVisible());
                assertTrue(button.isManaged());
                Button pauseButton = (Button) toolbar.node().getChildren().get(
                        indexOfButton(toolbar, "simulation-icon-button"));
                String pauseIconBeforeStep = ((SVGPath) pauseButton.getGraphic()).getContent();

                toolbar.setStepSimulationOnceAction(() -> { });
                button.fire();
                assertEquals(pauseIconBeforeStep, ((SVGPath) pauseButton.getGraphic()).getContent(),
                        "Однотактне продовження не змінює значок стандартної кнопки паузи");
                assertTrue(button.isVisible(), "Кнопка залишається видимою, поки виконується однотактний режим");
                assertTrue(button.isManaged(), "Кнопка зберігає місце в панелі під час однотактного режиму");
                toolbar.setSimulationPaused(false);
                assertFalse(button.isVisible(), "Кнопка не показується під час звичайного виконання");
                toolbar.setSimulationPaused(true);
                toolbar.setSimulationControlsVisible(false);
                assertFalse(button.isVisible(), "Кнопка прихована разом з іншими контролами неактивної симуляції");
            } finally {
                toolbar.dispose();
            }
        });
    }

    /** Перевіряє, що натискання нової кнопки викликає передану дію рівно один раз. */
    @Test
    void delegatesStepButtonAction() throws Exception {
        runOnFxThread(() -> {
            ToolbarView toolbar = toolbar();
            AtomicInteger calls = new AtomicInteger();
            try {
                toolbar.setStepSimulationOnceAction(calls::incrementAndGet);
                toolbar.stepOnceButton().fire();
                assertEquals(1, calls.get());
            } finally {
                toolbar.dispose();
            }
        });
    }

    /** Створює панель інструментів із тестовими callback-ами та українською локалізацією. */
    private static ToolbarView toolbar() {
        return new ToolbarView(ignored -> { }, () -> { }, () -> { }, () -> { }, () -> { },
                ignored -> { }, 650.0, new LocalizationService(Locale.forLanguageTag("uk")));
    }

    /**
     * Повертає індекс кнопки за CSS-класом.
     *
     * @param toolbar панель, у якій шукається кнопка.
     * @param styleClass CSS-клас кнопки.
     * @return позиція кнопки в дереві або {@code -1}, якщо її не знайдено.
     */
    private static int indexOfButton(ToolbarView toolbar, String styleClass) {
        for (int index = 0; index < toolbar.node().getChildren().size(); index++) {
            Node node = toolbar.node().getChildren().get(index);
            if (node instanceof Button button && button.getStyleClass().contains(styleClass)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Виконує дії тесту в потоці JavaFX і передає винятки потоку JUnit.
     *
     * @param action перевірка, яку потрібно виконати в JavaFX Application Thread.
     * @throws Exception якщо очікування JavaFX-потоку перервано або перевищено час очікування.
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
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure[0] != null) {
            throw new AssertionError("Тест JavaFX завершився помилкою", failure[0]);
        }
    }
}
