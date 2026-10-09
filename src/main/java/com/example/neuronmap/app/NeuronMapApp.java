package com.example.neuronmap.app;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.config.AppConfigLoader;
import com.example.neuronmap.controller.NeuronMapController;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.DatabasePathResolver;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import com.example.neuronmap.persistence.LegacyProjectStorageMigrator;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.ProjectStorageDirectoryResolver;
import com.example.neuronmap.persistence.SqliteMapRepository;
import com.example.neuronmap.persistence.TransientProjectRepository;
import com.example.neuronmap.persistence.WindowState;
import com.example.neuronmap.persistence.WindowStateStore;
import com.example.neuronmap.service.ProjectCatalogService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * Запускає застосунок, завантажує конфігурацію та створює основні компоненти програми.
 */
public final class NeuronMapApp extends Application {

    /**
     * Запускає або планує дію, повʼязану з «потрібні дані».
     *
     * @param stage значення, що визначає відповідну операцію для цієї операції.
     */
    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfigLoader.load();

        Path storageDirectory = ProjectStorageDirectoryResolver.resolve();
        GlobalSettingsStore globalSettings = new GlobalSettingsStore(
                storageDirectory.resolve("neuronmap-global.properties")
        );

        Path legacyDatabasePath = DatabasePathResolver.resolve();
        LegacyProjectStorageMigrator.migrateIfNeeded(
                storageDirectory,
                legacyDatabasePath,
                legacyDatabasePath.resolveSibling("neuronmap-projects"),
                legacyDatabasePath.resolveSibling("neuronmap-projects.properties"),
                "NeuronMap",
                globalSettings
        );

        ProjectCatalogService projectCatalog = new ProjectCatalogService(
                storageDirectory,
                globalSettings
        );
        ProjectDescriptor initialProject = projectCatalog.lastOpenedProject();
        if (initialProject == null) {
            initialProject = projectCatalog.listProjects()
                    .stream()
                    .findFirst()
                    .orElseGet(() -> projectCatalog.createTransientProject("Project"));
        }

        MapRepository repository = initialProject.isPersisted()
                ? new SqliteMapRepository(initialProject.databasePath())
                : new TransientProjectRepository(initialProject.databasePath());

        NeuronMapModel model = new NeuronMapModel();
        NeuronMapApplicationService application =
                new NeuronMapApplicationService(model, repository);
        NeuronMapController controller = new NeuronMapController(
                application,
                config,
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
     * Виконує операцію «відповідну операцію».
     *
     * @param args значення, що визначає відповідну операцію для цієї операції.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
