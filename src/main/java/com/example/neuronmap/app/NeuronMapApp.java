package com.example.neuronmap.app;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.config.AppConfigLoader;
import com.example.neuronmap.controller.NeuronMapController;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.DatabasePathResolver;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.SqliteMapRepository;
import com.example.neuronmap.persistence.WindowState;
import com.example.neuronmap.persistence.WindowStateStore;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;

/** JavaFX application bootstrap and window lifecycle. */
public final class NeuronMapApp extends Application {

    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfigLoader.load();

        Path databasePath = DatabasePathResolver.resolve();
        WindowStateStore windowStateStore =
                new WindowStateStore(
                        databasePath.resolveSibling(
                                config.window().stateFile()
                        )
                );

        MapRepository repository =
                new SqliteMapRepository(databasePath);
        NeuronMapModel model = new NeuronMapModel();
        NeuronMapApplicationService application =
                new NeuronMapApplicationService(model, repository);
        NeuronMapController controller =
                new NeuronMapController(
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

        WindowState savedState =
                windowStateStore.load().orElse(null);

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

    public static void main(String[] args) {
        launch(args);
    }
}
