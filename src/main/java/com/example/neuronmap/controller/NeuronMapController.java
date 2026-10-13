package com.example.neuronmap.controller;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.coordinator.MapEditorCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.service.ProjectCatalogService;
import javafx.scene.Scene;

import java.util.Objects;

/**
 * Готує сцену карти нейронів, встановлює початкове положення камери та завершує роботу контролерів під час закриття редактора.
 */
public final class NeuronMapController {

    private final MapEditorCoordinator coordinator;

    /**
     * Створює контролер карти з готовими службами та callback завершення роботи.
     *
     * @param application прикладна служба, яка надає доступ до служб карти й збереження.
     * @param config завантажена конфігурація застосунку.
     * @param localization готова служба локалізації інтерфейсу.
     * @param projectCatalog готова служба каталогу проєктів.
     * @param mapRepositoryFactory фабрика репозиторіїв проєктів.
     * @param exitApplication callback завершення роботи застосунку.
     */
    public NeuronMapController(
            NeuronMapApplicationService application,
            AppConfig config,
            LocalizationService localization,
            ProjectCatalogService projectCatalog,
            MapRepositoryFactory mapRepositoryFactory,
            Runnable exitApplication
    ) {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(localization, "localization");
        Objects.requireNonNull(projectCatalog, "projectCatalog");
        Objects.requireNonNull(mapRepositoryFactory, "mapRepositoryFactory");

        this.coordinator = new MapEditorCoordinator(
                application,
                config,
                localization,
                projectCatalog,
                mapRepositoryFactory,
                Objects.requireNonNull(exitApplication, "exitApplication")
        );
    }

    /**
     * Створює сцену редактора заданого розміру та розміщує на ній компоненти карти.
     *
     * @param width ширина видимої області.
     * @param height висота видимої області.
     * @return створена сцена редактора.
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
