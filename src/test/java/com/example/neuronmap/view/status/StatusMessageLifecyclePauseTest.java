package com.example.neuronmap.view.status;

import javafx.application.Platform;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що повідомлення рядка стану не втрачає час видимості під час призупинення редактора. */
final class StatusMessageLifecyclePauseTest {

    /** Запускає JavaFX перед перевірками анімацій рядка стану. */
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

    /** Перевіряє, що пауза зберігає видиме повідомлення й відновлює таймер після закриття меню. */
    @Test
    void messageLifecycleCanBePausedAndResumedWithoutClearingText() throws Exception {
        runOnFxThread(() -> {
            StatusBarView statusBar = new StatusBarView();
            StatusMessagePresenter presenter = new StatusMessagePresenter(statusBar);
            try {
                presenter.show("Повідомлення для перевірки");
                presenter.pause();

                assertTrue(presenter.isPaused());
                assertEquals("Повідомлення для перевірки", statusBar.messageLabel().getText());
                assertEquals(1.0, statusBar.messageLabel().getOpacity(), 0.0001);

                presenter.resume();

                assertFalse(presenter.isPaused());
                assertEquals("Повідомлення для перевірки", statusBar.messageLabel().getText());
                assertEquals(1.0, statusBar.messageLabel().getOpacity(), 0.0001);
            } finally {
                presenter.dispose();
            }
        });
    }

    /** Перевіряє, що відкриття меню зберігає залишок часу повідомлення, а не запускає відлік заново. */
    @Test
    void visibilityCountdownRetainsElapsedTimeDuringPause() throws Exception {
        AtomicReference<StatusBarView> statusBarReference = new AtomicReference<>();
        AtomicReference<StatusMessageLifecycle> lifecycleReference = new AtomicReference<>();

        runOnFxThread(() -> {
            StatusBarView statusBar = new StatusBarView();
            statusBar.setText("Повідомлення для вимірювання часу");
            StatusMessageLifecycle lifecycle = new StatusMessageLifecycle(
                    statusBar.node(),
                    statusBar.messageLabel(),
                    Duration.millis(350.0),
                    Duration.millis(70.0)
            );
            lifecycle.show("Повідомлення для вимірювання часу");
            statusBarReference.set(statusBar);
            lifecycleReference.set(lifecycle);
        });

        Thread.sleep(260L);
        runOnFxThread(lifecycleReference.get()::pause);
        Thread.sleep(500L);

        runOnFxThread(() -> {
            assertTrue(lifecycleReference.get().isSuspended());
            assertEquals(1.0, statusBarReference.get().messageLabel().getOpacity(), 0.0001);
        });

        runOnFxThread(lifecycleReference.get()::resume);
        Thread.sleep(250L);

        runOnFxThread(() -> {
            assertEquals(0.0, statusBarReference.get().messageLabel().getOpacity(), 0.05);
            lifecycleReference.get().dispose();
        });
    }

    /** Перевіряє, що повідомлення, створене під час паузи, залишається видимим до відновлення інтерфейсу. */
    @Test
    void messageShownWhilePausedWaitsForResume() throws Exception {
        runOnFxThread(() -> {
            StatusBarView statusBar = new StatusBarView();
            StatusMessagePresenter presenter = new StatusMessagePresenter(statusBar);
            try {
                presenter.pause();
                presenter.show("Повідомлення меню");

                assertTrue(presenter.isPaused());
                assertEquals("Повідомлення меню", statusBar.messageLabel().getText());
                assertEquals(1.0, statusBar.messageLabel().getOpacity(), 0.0001);

                presenter.resume();

                assertFalse(presenter.isPaused());
                assertEquals("Повідомлення меню", statusBar.messageLabel().getText());
            } finally {
                presenter.dispose();
            }
        });
    }

    /** Виконує перевірку на потоці JavaFX і передає виняток у потік тесту. */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure.get() != null) {
            throw new AssertionError("JavaFX test failed", failure.get());
        }
    }
}
