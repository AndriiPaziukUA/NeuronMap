package com.example.neuronmap.view.support;

import javafx.application.Platform;
import javafx.scene.control.Tooltip;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє, що фабрика встановлює однакові часові параметри підказок і нормалізує відсутній текст.
 */
final class TooltipFactoryTest {

    /**
     * Запускає JavaFX toolkit перед тестами, що створюють елементи керування.
     *
     * @throws InterruptedException якщо очікування запуску toolkit перервано.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Перевіряє текст і спільні часові параметри новоствореної підказки.
     *
     * @throws Exception якщо перевірку не вдалося виконати в JavaFX Application Thread.
     */
    @Test
    void createUsesSharedUiTimingValues() throws Exception {
        runOnFxThread(() -> {
            Tooltip tooltip = TooltipFactory.create("Підказка");

            assertEquals("Підказка", tooltip.getText());
            assertEquals(UiTiming.TOOLTIP_SHOW_DELAY.toMillis(), tooltip.getShowDelay().toMillis());
            assertEquals(UiTiming.TOOLTIP_VISIBLE_DURATION.toMillis(), tooltip.getShowDuration().toMillis());
            assertEquals(UiTiming.TOOLTIP_HIDE_DELAY.toMillis(), tooltip.getHideDelay().toMillis());
        });
    }

    /**
     * Перевіряє, що фабрика перетворює відсутній текст на порожній рядок.
     *
     * @throws Exception якщо перевірку не вдалося виконати в JavaFX Application Thread.
     */
    @Test
    void createNormalizesNullText() throws Exception {
        runOnFxThread(() -> assertEquals("", TooltipFactory.create(null).getText()));
    }

    /**
     * Перевіряє, що ті самі часові параметри можна застосувати до вже створеної підказки.
     *
     * @throws Exception якщо перевірку не вдалося виконати в JavaFX Application Thread.
     */
    @Test
    void configureResetsExistingTooltipTiming() throws Exception {
        runOnFxThread(() -> {
            Tooltip tooltip = new Tooltip("Наявна підказка");
            tooltip.setShowDelay(javafx.util.Duration.seconds(9));
            tooltip.setShowDuration(javafx.util.Duration.seconds(10));
            tooltip.setHideDelay(javafx.util.Duration.seconds(11));

            TooltipFactory.configure(tooltip);

            assertEquals(UiTiming.TOOLTIP_SHOW_DELAY.toMillis(), tooltip.getShowDelay().toMillis());
            assertEquals(UiTiming.TOOLTIP_VISIBLE_DURATION.toMillis(), tooltip.getShowDuration().toMillis());
            assertEquals(UiTiming.TOOLTIP_HIDE_DELAY.toMillis(), tooltip.getHideDelay().toMillis());
        });
    }

    /**
     * Виконує перевірку в JavaFX Application Thread та передає винятки назад до тестового потоку.
     *
     * @param action перевірка, яку потрібно виконати.
     * @throws Exception якщо JavaFX не виконав дію в межах установленого часу.
     */
    private static void runOnFxThread(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError("Перевірка JavaFX завершилася помилкою.", failure.get());
        }
    }
}
