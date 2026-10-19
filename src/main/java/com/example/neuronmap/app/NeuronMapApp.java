package com.example.neuronmap.app;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.config.AppConfigLoader;
import com.example.neuronmap.i18n.LanguagePreferenceStore;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.ApplicationStorageLayout;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.project.FileSystemProjectCatalogRepository;
import com.example.neuronmap.persistence.settings.GlobalSettingsLanguagePreferenceStore;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.persistence.sqlite.DefaultMapRepositoryFactory;
import com.example.neuronmap.persistence.window.WindowStateStore;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.ProjectCatalogService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Є точкою входу JavaFX-застосунку: збирає залежності, готує початковий проєкт редактора та створює головне вікно.
 * Керування вікном і узгоджене завершення роботи делегує {@link MainWindowController}.
 */
public final class NeuronMapApp extends Application {

    private MainWindowController mainWindowController;

    /**
     * Завантажує конфігурацію, готує сховища та залежності редактора, визначає початковий проєкт і показує головне вікно.
     *
     * @param stage головне вікно JavaFX, яке потрібно налаштувати та показати.
     */
    @Override
    public void start(Stage stage) {
        ApplicationStorageLayout storageLayout = ApplicationStorageLayout.resolve();
        AppConfig config = AppConfigLoader.load(storageLayout);
        Path storageDirectory = storageLayout.rootDirectory();
        GlobalSettingsStore globalSettings = new GlobalSettingsStore(storageLayout.globalSettingsFile());

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

        MapRepositoryFactory mapRepositoryFactory = new DefaultMapRepositoryFactory(storageDirectory);
        MapRepository repository = initialProject.isPersisted()
                ? mapRepositoryFactory.createSavedProjectRepository(initialProject)
                : mapRepositoryFactory.createTransientProjectRepository(initialProject);

        WindowStateStore windowStateStore = new WindowStateStore(
                storageLayout.windowStateFile(config.window().stateFile())
        );
        mainWindowController = new MainWindowController(stage, windowStateStore, localization);

        NeuronMapModel model = new NeuronMapModel();
        NeuronMapApplicationService application =
                new NeuronMapApplicationService(model, repository);
        NeuronMapEditor editor = new NeuronMapEditor(
                application,
                config,
                localization,
                projectCatalog,
                mapRepositoryFactory,
                mainWindowController::close
        );

        Scene scene = editor.createScene(
                config.window().defaultWidth(),
                config.window().defaultHeight()
        );
        mainWindowController.configure(
                config.window(),
                scene,
                editor::shutdown
        );
        mainWindowController.show();
        editor.centerInitialView();
    }

    /**
     * Повторно запитує завершення життєвого циклу як резервний шлях під час зупинки JavaFX.
     * Координатор запобігає повторному збереженню та повторному звільненню ресурсів після закриття вікна.
     */
    @Override
    public void stop() {
        if (Objects.nonNull(mainWindowController)) {
            mainWindowController.shutdown();
        }
    }

    /**
     * Визначає проєкт, який редактор має відкрити під час запуску.
     * Спочатку вибирає останній відкритий доступний проєкт, потім — перший у каталозі,
     * а якщо збережених проєктів немає, створює новий тимчасовий проєкт.
     *
     * @param projectCatalog служба доступу до каталогу проєктів.
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

        return projectCatalog.lastOpenedProject()
                .or(() -> projectCatalog.listProjects().stream().findFirst())
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
