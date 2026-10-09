package com.example.neuronmap.controller;

import com.example.neuronmap.view.StatusMessageLifecycle;
import com.example.neuronmap.view.StatusMessageTargetLocator;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;

import java.util.function.Consumer;

/**
 * Відображає повідомлення про стан роботи застосунку.
 */
public final class StatusMessagePresenter {

    private final Consumer<String> fallback;
    private Scene scene;
    private StatusMessageLifecycle lifecycle;
    private Label lifecycleLabel;
    private long messageVersion;

    /**
     * Повертає результат операції «стан повідомлення».
     *
     * @param fallback значення, що визначає резервний варіант для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public StatusMessagePresenter(Consumer<String> fallback) {
        if (fallback == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "fallback must not be null"
            );
        }
        this.fallback = fallback;
    }

    /**
     * Виконує операцію «відповідну операцію».
     *
     * @param scene значення, що визначає сцена для цієї операції.
     */
    public void attach(Scene scene) {
        disposeLifecycle();
        this.scene = scene;
    }

    /**
     * Відображає «потрібні дані» в інтерфейсі.
     *
     * @param message повідомлення для показу чи журналювання.
     */
    public void show(String message) {
        String normalized = message == null ? "" : message;
        fallback.accept(normalized);

        long currentVersion = ++messageVersion;
        if (lifecycle != null && lifecycleLabel != null) {
            lifecycle.show(normalized);
            return;
        }

        if (scene == null) {
            return;
        }

        attachToActualStatusLabel(normalized, currentVersion);
    }

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    public void dispose() {
        messageVersion++;
        disposeLifecycle();
        scene = null;
    }

    /**
     * Виконує операцію «до стан підпис».
     *
     * @param message повідомлення для показу чи журналювання.
     *
     * @param currentVersion значення, що визначає поточний для цієї операції.
     */
    private void attachToActualStatusLabel(
            String message,
            long currentVersion
    ) {
        Runnable attach = () -> {
            if (currentVersion != messageVersion || lifecycle != null) {
                return;
            }

            StatusMessageTargetLocator.findByText(
                    scene,
                    message
            ).ifPresent(target -> {
                lifecycleLabel = target.label();
                lifecycle = new StatusMessageLifecycle(
                        target.hoverRegion(),
                        target.label()
                );
                lifecycle.show(message);
            });
        };

        attach.run();
        if (lifecycle == null) {
            Platform.runLater(attach);
        }
    }

    /**
     * Завершує або скасовує дію, повʼязану з «відповідну операцію».
     */
    private void disposeLifecycle() {
        if (lifecycle != null) {
            lifecycle.dispose();
            lifecycle = null;
        }
        lifecycleLabel = null;
    }
}
