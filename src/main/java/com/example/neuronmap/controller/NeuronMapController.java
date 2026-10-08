package com.example.neuronmap.controller;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.coordinator.MapEditorCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import javafx.scene.Scene;

import java.nio.file.Path;
import java.util.Objects;

/** Thin JavaFX entry-point. Project/menu orchestration remains in coordinators. */
public final class NeuronMapController {

    private final MapEditorCoordinator coordinator;

    public NeuronMapController(
            NeuronMapApplicationService application,
            AppConfig config
    ) {
        this(
                application,
                config,
                () -> { }
        );
    }

    public NeuronMapController(
            NeuronMapApplicationService application,
            AppConfig config,
            Runnable exitApplication
    ) {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(config, "config");

        Path globalSettingsPath = application.map()
                .databasePath()
                .resolveSibling("neuronmap-global.properties");
        LocalizationService localization = new LocalizationService(
                new GlobalSettingsStore(globalSettingsPath)
        );

        this.coordinator = new MapEditorCoordinator(
                application,
                config,
                localization,
                Objects.requireNonNull(exitApplication, "exitApplication")
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
