package com.example.neuronmap.controller;

import com.example.neuronmap.view.StatusMessageLifecycle;
import com.example.neuronmap.view.StatusMessageTargetLocator;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;

import java.util.function.Consumer;

/**
 * Знаходить реальний елемент рядка стану в JavaFX-сцені та передає йому повідомлення для відображення.
 */
public final class StatusMessagePresenter {

    private final Consumer<String> fallback;
    private Scene scene;
    private StatusMessageLifecycle lifecycle;
    private Label lifecycleLabel;
    private long messageVersion;

    /**
     * Створює екземпляр StatusMessagePresenter та зберігає передані залежності, потрібні для його роботи.
     *
     * @param fallback резервне значення, яке використовують, якщо основний варіант недоступний.
     */
    public StatusMessagePresenter(Consumer<String> fallback) {
        if (fallback == null) {

            throw new IllegalArgumentException(
                    "fallback must not be null"
            );
        }
        this.fallback = fallback;
    }

    /**
     * Під’єднує подання повідомлень до сцени та знаходить фактичний елемент рядка стану.
     *
     * @param scene сцена JavaFX, до якої приєднують компонент.
     */
    public void attach(Scene scene) {
        disposeLifecycle();
        this.scene = scene;
    }

    /**
     * Показує повідомлення в рядку стану або передає його резервному обробнику, якщо ціль не знайдена.
     *
     * @param message значення «message», яке використовується в цьому методі.
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
     * Від’єднує подання повідомлень від сцени та звільняє ресурси.
     */
    public void dispose() {
        messageVersion++;
        disposeLifecycle();
        scene = null;
    }

    /**
     * Знаходить реальну мітку рядка стану в сцені та під’єднує до неї механізм показу повідомлень.
     *
     * @param message значення «message», яке використовується в цьому методі.
     * @param currentVersion номер поточної версії, з яким порівнюють збережені дані.
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
     * Від’єднує обробники подій і звільняє ресурси, якими керує компонент.
     */
    private void disposeLifecycle() {
        if (lifecycle != null) {
            lifecycle.dispose();
            lifecycle = null;
        }
        lifecycleLabel = null;
    }
}
