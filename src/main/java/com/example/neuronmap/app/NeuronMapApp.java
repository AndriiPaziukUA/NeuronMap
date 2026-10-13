package com.example.neuronmap.app;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.config.AppConfigLoader;
import com.example.neuronmap.controller.NeuronMapController;
import com.example.neuronmap.i18n.LanguagePreferenceStore;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.FileSystemProjectCatalogRepository;
import com.example.neuronmap.persistence.GlobalSettingsLanguagePreferenceStore;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import com.example.neuronmap.persistence.DefaultMapRepositoryFactory;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.ProjectStorageDirectoryResolver;
import com.example.neuronmap.persistence.WindowState;
import com.example.neuronmap.persistence.WindowStateStore;
import com.example.neuronmap.service.ProjectCatalogService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Є точкою входу JavaFX-застосунку: збирає залежності, готує початковий проєкт редактора та керує головним вікном.
 */
public final class NeuronMapApp extends Application {

    /**
     * Завантажує конфігурацію, готує сховище й залежності редактора, визначає початковий проєкт і показує головне вікно.
     * Під час закриття вікна зберігає його геометрію та завершує роботу контролера редактора.
     *
     * @param stage головне вікно JavaFX, яке потрібно налаштувати та показати.
     */
    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfigLoader.load();

        Path storageDirectory = ProjectStorageDirectoryResolver.resolve();
        GlobalSettingsStore globalSettings = new GlobalSettingsStore(
                storageDirectory.resolve("neuronmap-global.properties")
        );

        LanguagePreferenceStore languagePreferenceStore =
                new GlobalSettingsLanguagePreferenceStore(globalSettings);
        LocalizationService localization = new LocalizationService(languagePreferenceStore);
        ProjectCatalogService projectCatalog = new ProjectCatalogService(
                new FileSystemProjectCatalogRepository(storageDirectory, globalSettings)
        );
        ProjectDescriptor initialProject = resolveInitialProject(
                projectCatalog,
                localization.text("project.default_name")
        );

        MapRepositoryFactory mapRepositoryFactory = new DefaultMapRepositoryFactory();
        MapRepository repository = initialProject.isPersisted()
                ? mapRepositoryFactory.createSavedProjectRepository(initialProject)
                : mapRepositoryFactory.createTransientProjectRepository(initialProject);

        NeuronMapModel model = new NeuronMapModel();
        NeuronMapApplicationService application =
                new NeuronMapApplicationService(model, repository);
        NeuronMapController controller = new NeuronMapController(
                application,
                config,
                localization,
                projectCatalog,
                mapRepositoryFactory,
                stage::close
        );

        Scene scene = controller.createScene(
                config.window().defaultWidth(),
                config.window().defaultHeight()
        );

        stage.setTitle(config.window().title());
        stage.setScene(scene);

        WindowStateStore windowStateStore = new WindowStateStore(
                storageDirectory.resolve(config.window().stateFile())
        );
        WindowState savedState = windowStateStore.load().orElse(null);

        if (savedState != null) {
            stage.setWidth(savedState.width());
            stage.setHeight(savedState.height());
            stage.setX(savedState.x());
            stage.setY(savedState.y());
        }

        stage.setOnCloseRequest(event -> {
            windowStateStore.save(
                    new WindowState(
                            stage.getWidth(),
                            stage.getHeight(),
                            stage.getX(),
                            stage.getY()
                    )
            );
            controller.shutdown();
        });

        stage.show();

        if (savedState == null) {
            stage.centerOnScreen();
        }

        controller.centerInitialView();
    }

    /**
     * Визначає проєкт, який редактор має відкрити під час запуску.
     * Спочатку повертає останній відкритий доступний проєкт, потім — перший проєкт із каталогу,
     * а якщо збережених проєктів немає, створює новий тимчасовий проєкт.
     *
     * @param projectCatalog служба, що надає доступ до каталогу проєктів.
     * @param defaultProjectName локалізована типова назва нового проєкту.
     * @return останній відкритий проєкт, перший доступний збережений проєкт або новий тимчасовий проєкт.
     * @throws NullPointerException якщо службу каталогу не задано.
     * @throws IllegalArgumentException якщо типова назва проєкту порожня або не задана.
     */
    static ProjectDescriptor resolveInitialProject(
            ProjectCatalogService projectCatalog,
            String defaultProjectName
    ) {
        Objects.requireNonNull(projectCatalog, "projectCatalog");
        if (defaultProjectName == null || defaultProjectName.isBlank()) {
            throw new IllegalArgumentException("defaultProjectName must not be blank");
        }

        ProjectDescriptor lastOpenedProject = projectCatalog.lastOpenedProject();
        if (lastOpenedProject != null) {
            return lastOpenedProject;
        }

        return projectCatalog.listProjects()
                .stream()
                .findFirst()
                .orElseGet(() -> projectCatalog.createTransientProject(defaultProjectName));
    }

    /**
     * Є точкою входу застосунку та передає керування JavaFX через {@code launch(args)}.
     *
     * @param args аргументи командного рядка, передані під час запуску.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
