package com.example.neuronmap.app;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.coordinator.MapEditorCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.service.ProjectCatalogService;
import javafx.scene.Scene;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Фасад редактора, який приховує складання внутрішніх компонентів і надає контракт життєвого циклу.
 * Побудову редактора й координацію підсистем делегує {@link MapEditorCoordinator}.
 */
public final class NeuronMapEditor {

    private final MapEditorCoordinator coordinator;

    /** Створює фасад редактора зі службами проєкту та дією завершення застосунку. */
    public NeuronMapEditor(
            NeuronMapApplicationService application,
            AppConfig config,
            LocalizationService localization,
            ProjectCatalogService projectCatalog,
            MapRepositoryFactory mapRepositoryFactory,
            Runnable exitApplication
    ) {
        this(application, config, localization, projectCatalog, mapRepositoryFactory,
                exitApplication, null, null, null);
    }

    /**
     * Створює фасад редактора та передає координатору callback-и глобального налаштування повноекранного режиму.
     * Цей конструктор використовується точкою входу, яка володіє життєвим циклом Stage.
     *
     * @param application прикладна служба карти та збереження.
     * @param config перевірена конфігурація застосунку.
     * @param localization спільна служба локалізації.
     * @param projectCatalog служба каталогу проєктів.
     * @param mapRepositoryFactory фабрика сховищ проєктів.
     * @param exitApplication дія завершення застосунку.
     * @param currentFullScreen постачальник активного стану повноекранного режиму.
     * @param previewFullScreen дія тимчасового перемикання режиму.
     * @param persistFullScreen дія збереження підтвердженого стану.
     */
    NeuronMapEditor(
            NeuronMapApplicationService application,
            AppConfig config,
            LocalizationService localization,
            ProjectCatalogService projectCatalog,
            MapRepositoryFactory mapRepositoryFactory,
            Runnable exitApplication,
            Supplier<Boolean> currentFullScreen,
            Consumer<Boolean> previewFullScreen,
            Consumer<Boolean> persistFullScreen
    ) {
        coordinator = new MapEditorCoordinator(
                Objects.requireNonNull(application, "application"),
                Objects.requireNonNull(config, "config"),
                Objects.requireNonNull(localization, "localization"),
                Objects.requireNonNull(projectCatalog, "projectCatalog"),
                Objects.requireNonNull(mapRepositoryFactory, "mapRepositoryFactory"),
                Objects.requireNonNull(exitApplication, "exitApplication"),
                currentFullScreen,
                previewFullScreen,
                persistFullScreen
        );
    }

    /** Створює сцену редактора зазначеного початкового розміру. */
    public Scene createScene(double width, double height) {
        return coordinator.createScene(width, height);
    }

    /** Центрує початковий вигляд карти після розміщення сцени у вікні. */
    public void centerInitialView() {
        coordinator.centerInitialView();
    }

    /** Завершує редактор і звільняє його ресурси. */
    public void shutdown() {
        coordinator.shutdown();
    }
}
