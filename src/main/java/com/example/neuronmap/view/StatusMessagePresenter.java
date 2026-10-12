package com.example.neuronmap.view;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;

import java.util.function.Consumer;

/**
 * Керує відображенням коротких повідомлень у сцені JavaFX та життєвим циклом їхнього подання.
 */
public final class StatusMessagePresenter {

    private final Consumer<String> fallback;
    private Scene scene;
    private StatusMessageLifecycle lifecycle;
    private Label lifecycleLabel;
    private long messageVersion;

    /**
     * Створює подання повідомлень із резервним обробником для звичайного рядка стану.
     *
     * @param fallback обробник, який оновлює звичайний рядок стану.
     */
    public StatusMessagePresenter(Consumer<String> fallback) {
        if (fallback == null) {
            throw new IllegalArgumentException("fallback must not be null");
        }
        this.fallback = fallback;
    }

    /**
     * Під’єднує подання повідомлень до сцени JavaFX.
     *
     * @param scene сцена, у якій потрібно знаходити мітку повідомлення.
     */
    public void attach(Scene scene) {
        messageVersion++;
        disposeLifecycle();
        this.scene = scene;
    }

    /**
     * Показує повідомлення у відповідній мітці та оновлює резервний рядок стану.
     *
     * @param message текст повідомлення, яке потрібно показати.
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
     * Зупиняє анімації, від’єднує обробники подій і звільняє ресурси подання.
     */
    public void dispose() {
        messageVersion++;
        disposeLifecycle();
        scene = null;
    }

    /**
     * Знаходить мітку рядка стану та під’єднує механізм показу повідомлень.
     *
     * @param message текст повідомлення, яке потрібно показати.
     * @param currentVersion версія повідомлення, що захищає від застарілих відкладених завдань.
     */
    private void attachToActualStatusLabel(String message, long currentVersion) {
        Runnable attach = () -> {
            if (currentVersion != messageVersion || lifecycle != null || scene == null) {
                return;
            }

            StatusMessageTargetLocator.findByText(scene, message)
                    .ifPresent(target -> {
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
     * Від’єднує механізм життєвого циклу, якщо він уже був створений.
     */
    private void disposeLifecycle() {
        if (lifecycle != null) {
            lifecycle.dispose();
            lifecycle = null;
        }
        lifecycleLabel = null;
    }
}
