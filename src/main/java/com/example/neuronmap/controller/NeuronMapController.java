package com.example.neuronmap.controller;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.coordinator.MapEditorCoordinator;
import javafx.scene.Scene;

import java.util.Objects;

/**
 * Thin JavaFX entry-point. Application orchestration lives in
 * {@link MapEditorCoordinator}; this class only exposes the lifecycle expected
 * by the JavaFX application bootstrap.
 */
public final class NeuronMapController {

    private final MapEditorCoordinator coordinator;

    public NeuronMapController(
            NeuronMapApplicationService application,
            AppConfig config
    ) {
        this.coordinator = new MapEditorCoordinator(
                Objects.requireNonNull(application, "application"),
                Objects.requireNonNull(config, "config")
        );
    }

    public Scene createScene(double width, double height) {
        return coordinator.createScene(width, height);
    }

    public void centerInitialView() {
        coordinator.centerInitialView();
    }

    public void shutdown() {
        coordinator.shutdown();
    }
}
