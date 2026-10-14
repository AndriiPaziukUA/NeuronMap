package com.example.neuronmap.app;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.coordinator.MapEditorCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.service.ProjectCatalogService;
import javafx.scene.Scene;

import java.util.Objects;

/**
 * Фасад редактора на рівні застосунку, який приховує складання внутрішніх компонентів і надає контракт життєвого циклу.
 * Не обробляє UI-події та не містить сценаріїв взаємодії: побудову редактора й координацію підсистем делегує
 * {@link MapEditorCoordinator}, залишаючи точці входу стабільний зовнішній інтерфейс.
 */
public final class NeuronMapEditor {

    private final MapEditorCoordinator coordinator;

    /**
     * Створює фасад редактора та передає всі залежності координатору, який складає підсистеми карти.
     *
     * @param application прикладна служба, яка надає доступ до служб карти й збереження.
     * @param config завантажена конфігурація застосунку.
     * @param localization готова служба локалізації інтерфейсу.
     * @param projectCatalog готова служба каталогу проєктів.
     * @param mapRepositoryFactory фабрика репозиторіїв проєктів.
     * @param exitApplication дія завершення роботи застосунку.
     * @throws NullPointerException якщо будь-яка із залежностей не задана.
     */
    public NeuronMapEditor(
            NeuronMapApplicationService application,
            AppConfig config,
            LocalizationService localization,
            ProjectCatalogService projectCatalog,
            MapRepositoryFactory mapRepositoryFactory,
            Runnable exitApplication
    ) {
        this.coordinator = new MapEditorCoordinator(
                Objects.requireNonNull(application, "application"),
                Objects.requireNonNull(config, "config"),
                Objects.requireNonNull(localization, "localization"),
                Objects.requireNonNull(projectCatalog, "projectCatalog"),
                Objects.requireNonNull(mapRepositoryFactory, "mapRepositoryFactory"),
                Objects.requireNonNull(exitApplication, "exitApplication")
        );
    }

    /**
     * Створює сцену редактора заданого розміру та повертає її точці входу для показу у вікні.
     *
     * @param width ширина видимої області.
     * @param height висота видимої області.
     * @return створена сцена редактора.
     */
    public Scene createScene(double width, double height) {
        return coordinator.createScene(width, height);
    }

    /**
     * Центрує початковий вигляд карти після розміщення сцени у вікні.
     */
    public void centerInitialView() {
        coordinator.centerInitialView();
    }

    /**
     * Завершує життєвий цикл редактора, делегуючи очищення ресурсів координатору.
     */
    public void shutdown() {
        coordinator.shutdown();
    }
}
