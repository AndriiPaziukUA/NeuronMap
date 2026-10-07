package com.example.neuronmap.controller;

import com.example.neuronmap.view.StatusMessageLifecycle;
import com.example.neuronmap.view.StatusMessageTargetLocator;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;

import java.util.function.Consumer;

/** Connects application status updates to the actual status label lifecycle. */
public final class StatusMessagePresenter {

    private final Consumer<String> fallback;
    private Scene scene;
    private StatusMessageLifecycle lifecycle;
    private Label lifecycleLabel;
    private long messageVersion;

    public StatusMessagePresenter(Consumer<String> fallback) {
        if (fallback == null) {
            throw new IllegalArgumentException(
                    "fallback must not be null"
            );
        }
        this.fallback = fallback;
    }

    public void attach(Scene scene) {
        disposeLifecycle();
        this.scene = scene;
    }

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

    public void dispose() {
        messageVersion++;
        disposeLifecycle();
        scene = null;
    }

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

    private void disposeLifecycle() {
        if (lifecycle != null) {
            lifecycle.dispose();
            lifecycle = null;
        }
        lifecycleLabel = null;
    }
}
