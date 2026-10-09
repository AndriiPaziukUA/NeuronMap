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
 * Готує сцену карти нейронів, встановлює початкове положення камери та завершує роботу контролерів під час закриття редактора.
 */
public final class NeuronMapController {

    private final MapEditorCoordinator coordinator;

    /**
     * Створює екземпляр NeuronMapController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param application прикладна служба, яка надає доступ до служб карти й збереження.
     * @param config завантажена конфігурація застосунку.
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
     * Створює екземпляр NeuronMapController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param application прикладна служба, яка надає доступ до служб карти й збереження.
     * @param config завантажена конфігурація застосунку.
     * @param exitApplication callback завершення роботи застосунку.
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
     * Створює сцену редактора заданого розміру та розміщує на ній компоненти карти.
     *
     * @param width ширина видимої області.
     * @param height висота видимої області.
     */
    public Scene createScene(double width, double height) {
        return coordinator.createScene(width, height);
    }

    /**
     * Розміщує камеру так, щоб початковий вигляд карти був центрований у вікні.
     */
    public void centerInitialView() {
        coordinator.centerInitialView();
    }

    /**
     * Завершує роботу контролерів і закриває ресурси редактора.
     */
    public void shutdown() {
        coordinator.shutdown();
    }
}
