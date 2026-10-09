package com.example.neuronmap.controller;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.coordinator.MapEditorCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import com.example.neuronmap.persistence.ProjectStorageDirectoryResolver;
import javafx.scene.Scene;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Є головним контролером редактора та повʼязує події інтерфейсу з потрібними компонентами.
 */
public final class NeuronMapController {

    private final MapEditorCoordinator coordinator;

    /**
     * Повертає результат операції «нейрон карта».
     *
     * @param application значення, що визначає відповідну операцію для цієї операції.
     *
     * @param config значення, що визначає конфігурація для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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

    /**
     * Повертає результат операції «нейрон карта».
     *
     * @param application значення, що визначає відповідну операцію для цієї операції.
     *
     * @param config значення, що визначає конфігурація для цієї операції.
     *
     * @param exitApplication значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronMapController(
            NeuronMapApplicationService application,
            AppConfig config,
            Runnable exitApplication
    ) {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(config, "config");

        Path storageDirectory = ProjectStorageDirectoryResolver.resolve();
        LocalizationService localization = new LocalizationService(
                new GlobalSettingsStore(
                        storageDirectory.resolve("neuronmap-global.properties")
                )
        );

        this.coordinator = new MapEditorCoordinator(
                application,
                config,
                localization,
                Objects.requireNonNull(exitApplication, "exitApplication")
        );
    }

    /**
     * Створює обʼєкт із переданих даних «сцена».
     *
     * @param width ширина області.
     *
     * @param height висота області.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Scene createScene(double width, double height) {
        return coordinator.createScene(width, height);
    }

    /**
     * Виконує операцію «центр відображення».
     */
    public void centerInitialView() {
        coordinator.centerInitialView();
    }

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    public void shutdown() {
        coordinator.shutdown();
    }
}
