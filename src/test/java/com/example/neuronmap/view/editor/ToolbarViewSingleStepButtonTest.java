package com.example.neuronmap.view.editor;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

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

    /** Перевіряє графічну піктограму кнопки та початкову видимість лінії-накладки. */
    @Test
    void rendersNumberedContinueTriangleInsideShelfWithOverlayUnderline() throws Exception {
        runOnFxThread(() -> {
            ToolbarView toolbar = toolbar();
            try {
                Button button = toolbar.stepOnceButton();
                assertFalse(button.isVisible());
                assertFalse(button.isManaged());
                assertTrue(button.getStyleClass().contains("simulation-step-button"));
                StackPane shelf = (StackPane) button.getParent();
                Region underline = regionByClass(toolbar.node(), "simulation-step-group-underline");
                Button continueButton = buttonByClass(toolbar.node(), "simulation-icon-button");
                assertEquals(toolbar.node(), continueButton.getParent(),
                        "Кнопка продовження має залишатися прямим дочірнім вузлом панелі");
                assertEquals(toolbar.node(), shelf.getParent(),
                        "Висувна область має безпосередньо брати участь у компонуванні панелі");
                assertFalse(underline.isVisible());
                assertFalse(underline.isManaged(), "Лінія-накладка не повинна впливати на компонування");
                assertEquals(8.0, toolbar.node().getSpacing(), 0.01);
                assertTrue(toolbar.node().getChildren().indexOf(continueButton)
                        < toolbar.node().getChildren().indexOf(shelf));
                assertTrue(toolbar.node().getChildren().indexOf(shelf)
                        < toolbar.node().getChildren().indexOf(buttonByClass(toolbar.node(), "simulation-icon-button-danger")));

                StackPane graphic = (StackPane) button.getGraphic();
                SVGPath triangle = graphic.getChildren().stream()
                        .filter(SVGPath.class::isInstance).map(SVGPath.class::cast).findFirst().orElseThrow();
                Label number = graphic.getChildren().stream()
                        .filter(Label.class::isInstance).map(Label.class::cast).findFirst().orElseThrow();
                assertEquals("M 0 0 L 13 7 L 0 14 Z", triangle.getContent());
                assertEquals("1", number.getText());

                Scene scene = new Scene(toolbar.node(), 900.0, 100.0);
                scene.getStylesheets().add(ToolbarViewSingleStepButtonTest.class
                        .getResource("/app.css").toExternalForm());
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                assertEquals("Пропустити такт", button.getTooltip().getText());
                toolbar.setSimulationControlsVisible(true);
                toolbar.setSimulationPaused(true);
                assertTrue(button.isVisible());
                assertTrue(button.isManaged());
                assertFalse(underline.isVisible(), "Лінія має з'являтися лише разом із розгортанням шухлядки");
                toolbar.setSimulationControlsVisible(false);
                assertFalse(button.isVisible());
                assertFalse(underline.isVisible());
            } finally {
                toolbar.dispose();
            }
        });
    }

    /** Перевіряє відступ, геометрію лінії та переміщення кнопки «Стоп» під час висування. */
    @Test
    void underlineTracksExpandedShelfAndMovesStopButton() throws Exception {
        AtomicReference<ToolbarView> toolbarRef = new AtomicReference<>();
        AtomicReference<StackPane> shelfRef = new AtomicReference<>();
        AtomicReference<Region> underlineRef = new AtomicReference<>();
        AtomicReference<Button> continueRef = new AtomicReference<>();
        AtomicReference<Button> stopRef = new AtomicReference<>();
        AtomicReference<Double> stopXBefore = new AtomicReference<>();
        try {
            runOnFxThread(() -> {
                ToolbarView toolbar = toolbar();
                toolbarRef.set(toolbar);
                Scene scene = new Scene(toolbar.node(), 900.0, 100.0);
                scene.getStylesheets().add(ToolbarViewSingleStepButtonTest.class
                        .getResource("/app.css").toExternalForm());
                scene.getRoot().applyCss();
                toolbar.setSimulationControlsVisible(true);
                toolbar.setSimulationPaused(false);
                toolbar.node().layout();
                Button continueButton = buttonByClass(toolbar.node(), "simulation-icon-button");
                Button stopButton = buttonByClass(toolbar.node(), "simulation-icon-button-danger");
                StackPane shelf = (StackPane) toolbar.stepOnceButton().getParent();
                Region underline = regionByClass(toolbar.node(), "simulation-step-group-underline");
                continueRef.set(continueButton);
                shelfRef.set(shelf);
                underlineRef.set(underline);
                stopRef.set(stopButton);
                stopXBefore.set(stopButton.getBoundsInParent().getMinX());
                assertFalse(underline.isVisible());
                assertFalse(underline.isManaged());
                toolbar.setSimulationPaused(true);
                fireMouseEvent(continueButton, MouseEvent.MOUSE_ENTERED);
            });
            awaitCondition(() -> shelfRef.get().getPrefWidth() >= 30.0,
                    "Шухлядка не завершила висування");
            runOnFxThread(() -> {
                ToolbarView toolbar = toolbarRef.get();
                toolbar.node().layout();
                Region underline = underlineRef.get();
                StackPane shelf = shelfRef.get();
                Button continueButton = continueRef.get();
                assertEquals(31.0, toolbar.stepOnceButton().getWidth(), 1.0);
                assertEquals(31.0, shelf.getMinWidth(), 1.0,
                        "Мінімальна ширина шухлядки резервує місце перед кнопкою «Стоп»");
                assertEquals(8.0, toolbar.node().getSpacing(), 0.01);
                assertTrue(underline.isVisible());
                assertFalse(underline.isManaged());
                assertEquals(1.0, underline.getHeight(), 0.5);
                assertEquals(continueButton.getLayoutX(), underline.getLayoutX(), 0.5);
                assertEquals(shelf.getLayoutX() + shelf.getWidth() - continueButton.getLayoutX(),
                        underline.getWidth(), 1.0);
                assertEquals(continueButton.getLayoutY() + continueButton.getHeight() + 5.0,
                        underline.getLayoutY(), 1.0);
                assertTrue(stopRef.get().getBoundsInParent().getMinX() > stopXBefore.get(),
                        "Шухлядка візуально відсуває кнопку «Стоп», а не накладається поверх неї");
                fireMouseEvent(continueButton, MouseEvent.MOUSE_EXITED);
            });
            awaitCondition(() -> !shelfRef.get().isManaged() && !underlineRef.get().isVisible(),
                    "Шухлядка або лінія не зникли після згортання");
            runOnFxThread(() -> {
                assertEquals(0.0, underlineRef.get().getOpacity(), 0.01);
                toolbarRef.get().setSimulationPaused(false);
                toolbarRef.get().setSimulationControlsVisible(false);
            });
        } finally {
            if (toolbarRef.get() != null) runOnFxThread(toolbarRef.get()::dispose);
        }
    }

    /** Перевіряє, що однотактний режим утримує шухлядку до зняття звичайної паузи. */
    @Test
    void keepsShelfAndUnderlineOpenUntilRegularResume() throws Exception {
        AtomicReference<ToolbarView> toolbarRef = new AtomicReference<>();
        AtomicReference<StackPane> shelfRef = new AtomicReference<>();
        AtomicReference<Region> underlineRef = new AtomicReference<>();
        try {
            runOnFxThread(() -> {
                ToolbarView toolbar = toolbar();
                toolbarRef.set(toolbar);
                Scene scene = new Scene(toolbar.node(), 900.0, 100.0);
                scene.getStylesheets().add(ToolbarViewSingleStepButtonTest.class
                        .getResource("/app.css").toExternalForm());
                scene.getRoot().applyCss();
                toolbar.setSimulationControlsVisible(true);
                toolbar.setSimulationPaused(true);
                StackPane shelf = (StackPane) toolbar.stepOnceButton().getParent();
                shelfRef.set(shelf);
                underlineRef.set(regionByClass(toolbar.node(), "simulation-step-group-underline"));
                toolbar.setStepSimulationOnceAction(() -> { });
                toolbar.stepOnceButton().fire();
                fireMouseEvent(shelf, MouseEvent.MOUSE_EXITED);
                toolbar.node().layout();
            });
            awaitCondition(() -> shelfRef.get().getPrefWidth() >= 30.0,
                    "Однотактний режим не залишив шухлядку відкритою");
            Thread.sleep(220);
            runOnFxThread(() -> {
                assertTrue(shelfRef.get().isManaged());
                assertTrue(underlineRef.get().isVisible());
                assertFalse(underlineRef.get().isManaged());
                assertEquals(1.0, underlineRef.get().getOpacity(), 0.05);
                toolbarRef.get().setSimulationPaused(false);
            });
            awaitCondition(() -> !shelfRef.get().isManaged() && !underlineRef.get().isVisible(),
                    "Шухлядка та лінія не зникли після зняття звичайної паузи");
            runOnFxThread(() -> toolbarRef.get().setSimulationControlsVisible(false));
        } finally {
            if (toolbarRef.get() != null) runOnFxThread(toolbarRef.get()::dispose);
        }
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

    /** Знаходить кнопку за CSS-класом серед нащадків вузла панелі. */
    private static Button buttonByClass(Node node, String styleClass) {
        Button found = findButton(node, styleClass);
        if (found == null) throw new AssertionError("Кнопку з CSS-класом не знайдено: " + styleClass);
        return found;
    }

    /** Рекурсивно знаходить першу кнопку з потрібним CSS-класом. */
    private static Button findButton(Node node, String styleClass) {
        if (node instanceof Button button && button.getStyleClass().contains(styleClass)) return button;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Button found = findButton(child, styleClass);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Знаходить регіон за CSS-класом серед нащадків вузла панелі. */
    private static Region regionByClass(Node node, String styleClass) {
        Region found = findRegion(node, styleClass);
        if (found == null) throw new AssertionError("Регіон із CSS-класом не знайдено: " + styleClass);
        return found;
    }

    /** Рекурсивно знаходить перший регіон із потрібним CSS-класом. */
    private static Region findRegion(Node node, String styleClass) {
        if (node instanceof Region region && region.getStyleClass().contains(styleClass)) return region;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Region found = findRegion(child, styleClass);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Надсилає синтетичну подію миші вузлу для перевірки керування наведенням. */
    private static void fireMouseEvent(Node node, EventType<MouseEvent> eventType) {
        MouseEvent event = new MouseEvent(eventType, 0.0, 0.0, 0.0, 0.0, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, true, false, false, null);
        Event.fireEvent(node, event);
    }

    /** Очікує зміни стану анімації без блокування потоку JavaFX. */
    private static void awaitCondition(BooleanSupplier condition, String failureMessage) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            boolean[] matches = new boolean[1];
            runOnFxThread(() -> matches[0] = condition.getAsBoolean());
            if (matches[0]) return;
            Thread.sleep(20);
        }
        throw new AssertionError(failureMessage);
    }

    /** Створює панель інструментів із тестовими callback-ами та українською локалізацією. */
    private static ToolbarView toolbar() {
        return new ToolbarView(ignored -> { }, () -> { }, () -> { }, () -> { }, () -> { },
                ignored -> { }, 650.0, new LocalizationService(Locale.forLanguageTag("uk")));
    }

    /** Виконує перевірки в потоці JavaFX і передає помилки до потоку JUnit. */
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
