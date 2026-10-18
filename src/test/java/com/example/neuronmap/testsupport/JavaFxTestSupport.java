package com.example.neuronmap.testsupport;

import javafx.application.Platform;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Забезпечує одноразовий запуск JavaFX та синхронне виконання перевірок у потоці інтерфейсу. */
public final class JavaFxTestSupport {

    private static final long STARTUP_TIMEOUT_SECONDS = 5L;
    private static final Object STARTUP_LOCK = new Object();
    private static final CountDownLatch STARTUP_LATCH = new CountDownLatch(1);
    private static boolean startupRequested;

    /** Забороняє створення екземплярів допоміжного класу тестування. */
    private JavaFxTestSupport() {
    }

    /**
     * Виконує дію на JavaFX Application Thread та очікує на її завершення.
     * Якщо дія завершується помилкою, передає її виклику як причину помилки тесту.
     *
     * @param action дія, яка має виконатися в потоці JavaFX.
     * @throws Exception якщо запуск toolkit або очікування дії завершується невдало.
     * @throws NullPointerException якщо дію не задано.
     */
    public static void runAndWait(Runnable action) throws Exception {
        Objects.requireNonNull(action, "action");
        startToolkit();
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        CountDownLatch completion = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                completion.countDown();
            }
        });
        if (!completion.await(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test action timed out");
        }
        if (failure.get() != null) {
            throw new AssertionError("JavaFX test action failed", failure.get());
        }
    }

    /**
     * Запускає JavaFX toolkit один раз для всіх тестів, що використовують цей помічник.
     *
     * @throws InterruptedException якщо очікування запуску перервано.
     */
    private static void startToolkit() throws InterruptedException {
        synchronized (STARTUP_LOCK) {
            if (!startupRequested) {
                startupRequested = true;
                try {
                    Platform.startup(STARTUP_LATCH::countDown);
                } catch (IllegalStateException alreadyStarted) {
                    STARTUP_LATCH.countDown();
                }
            }
        }
        if (!STARTUP_LATCH.await(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX toolkit startup timed out");
        }
    }
}
