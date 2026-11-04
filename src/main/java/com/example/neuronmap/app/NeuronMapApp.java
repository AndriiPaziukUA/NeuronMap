package com.example.neuronmap.app;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.config.AppConfigLoader;
import com.example.neuronmap.i18n.LanguagePreferenceStore;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.observability.ActionTraceEventDispatcher;
import com.example.neuronmap.observability.TraceAction;
import com.example.neuronmap.persistence.ApplicationStorageLayout;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.project.FileSystemProjectCatalogRepository;
import com.example.neuronmap.persistence.settings.GlobalSettingsFullScreenPreferenceStore;
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

/** Є точкою входу JavaFX-застосунку та збирає залежності головного вікна, редактора й трасування дій. */
public final class NeuronMapApp extends Application {

    static {
        configureJulBridge();
    }

    private MainWindowController mainWindowController;
    private ActionTraceEventDispatcher actionTraceEventDispatcher;

    /** Завантажує конфігурацію й сховища, створює редактор, реєструє трасування UI-подій і показує вікно. */
    @TraceAction("Запуск застосунку")
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
                new FileSystemProjectCatalogRepository(storageDirectory, globalSettings));
        ProjectDescriptor initialProject = resolveInitialProject(
                projectCatalog, localization.text("project.default_name"));

        MapRepositoryFactory mapRepositoryFactory = new DefaultMapRepositoryFactory(storageDirectory);
        MapRepository repository = initialProject.isPersisted()
                ? mapRepositoryFactory.createSavedProjectRepository(initialProject)
                : mapRepositoryFactory.createTransientProjectRepository(initialProject);

        WindowStateStore windowStateStore = new WindowStateStore(
                storageLayout.windowStateFile(config.window().stateFile()));
        GlobalSettingsFullScreenPreferenceStore fullScreenPreferences =
                new GlobalSettingsFullScreenPreferenceStore(globalSettings);
        mainWindowController = new MainWindowController(
                stage, windowStateStore, localization, fullScreenPreferences);

        NeuronMapModel model = new NeuronMapModel();
        NeuronMapApplicationService application = new NeuronMapApplicationService(model, repository);
        NeuronMapEditor editor = new NeuronMapEditor(
                application,
                config,
                localization,
                projectCatalog,
                mapRepositoryFactory,
                mainWindowController::close,
                mainWindowController::isFullScreen,
                mainWindowController::previewFullScreen,
                mainWindowController::persistFullScreen,
                mainWindowController::setCurrentProject
        );

        Scene scene = editor.createScene(config.window().defaultWidth(), config.window().defaultHeight());
        actionTraceEventDispatcher = ActionTraceEventDispatcher.install(scene);
        mainWindowController.configure(config.window(), scene, editor::shutdown);
        mainWindowController.show();
        editor.centerInitialView();
    }

    /** Закриває трасування UI-подій та виконує резервне завершення життєвого циклу вікна. */
    @TraceAction("Завершення застосунку")
    @Override
    public void stop() {
        if (actionTraceEventDispatcher != null) {
            actionTraceEventDispatcher.close();
            actionTraceEventDispatcher = null;
        }
        if (mainWindowController != null) {
            mainWindowController.shutdown();
        }
    }

    /**
     * Спрямовує наявні JUL-діагностики сховищ і конфігурації до спільного Log4j2 backend.
     * Налаштування задається до завантаження компонентів, що створюють JUL-логери.
     */
    private static void configureJulBridge() {
        if (System.getProperty("java.util.logging.manager") == null) {
            System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        }
    }

    /**
     * Визначає початковий проєкт: останній відкритий, перший доступний або новий тимчасовий.
     *
     * @param projectCatalog служба доступу до каталогу проєктів.
     * @param defaultProjectName локалізована типова назва проєкту.
     * @return початковий проєкт редактора.
     * @throws NullPointerException якщо службу каталогу не задано.
     * @throws IllegalArgumentException якщо типова назва порожня або не задана.
     */
    static ProjectDescriptor resolveInitialProject(ProjectCatalogService projectCatalog, String defaultProjectName) {
        Objects.requireNonNull(projectCatalog, "projectCatalog");
        if (defaultProjectName == null || defaultProjectName.isBlank()) {
            throw new IllegalArgumentException("defaultProjectName must not be blank");
        }
        return projectCatalog.lastOpenedProject()
                .or(() -> projectCatalog.listProjects().stream().findFirst())
                .orElseGet(() -> projectCatalog.createTransientProject(defaultProjectName));
    }

    /** Запускає JavaFX-застосунок; метод викликається Java launcher, а не кодом застосунку. */
    @SuppressWarnings("unused")
    static void main(String[] args) {
        launch(args);
    }
}
