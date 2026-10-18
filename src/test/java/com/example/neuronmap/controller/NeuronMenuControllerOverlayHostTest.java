package com.example.neuronmap.controller;

import com.example.neuronmap.coordinator.NeuronSettingsOverlayCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.menu.MainMenuView;

import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє вибір контейнера внутрішньої накладки у вкладеній ієрархії головної сцени. */
final class NeuronMenuControllerOverlayHostTest {

    /** Перевіряє, що вибирається StackPane з головним меню, а не зовнішня обгортка вікна. */
    @Test
    void prefersStackPaneThatDirectlyContainsMainMenu() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane workspaceViewport = new StackPane();
            Pane editorContent = new Pane(workspaceViewport);
            StackPane editorRoot = new StackPane(editorContent);
            MainMenuView mainMenu = new MainMenuView(new LocalizationService(Locale.ENGLISH), ignored -> { });
            editorRoot.getChildren().add(mainMenu);
            BorderPane windowChromeRoot = new BorderPane();
            windowChromeRoot.setCenter(editorRoot);
            StackPane outerWrapper = new StackPane(windowChromeRoot);
            Scene scene = new Scene(outerWrapper, 800.0, 600.0);

            assertSame(outerWrapper, scene.getRoot());
            assertSame(editorRoot, NeuronSettingsOverlayCoordinator.resolveOverlayHost(workspaceViewport));
        });
    }

    /** Перевіряє резервний вибір контейнера, коли головне меню відсутнє. */
    @Test
    void resolvesEditorRootWhenSceneRootIsWindowChromeContainer() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane workspaceViewport = new StackPane();
            Pane editorContent = new Pane(workspaceViewport);
            StackPane editorRoot = new StackPane(editorContent);
            BorderPane windowChromeRoot = new BorderPane();
            windowChromeRoot.setCenter(editorRoot);
            Scene scene = new Scene(windowChromeRoot, 800.0, 600.0);

            assertSame(windowChromeRoot, scene.getRoot());
            assertSame(editorRoot, NeuronSettingsOverlayCoordinator.resolveOverlayHost(workspaceViewport));
        });
    }

    /** Перевіряє явну помилку, якщо в ієрархії немає контейнера, придатного для накладки. */
    @Test
    void rejectsHierarchyWithoutStackPaneAncestor() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Pane root = new Pane();
            Pane editorContent = new Pane();
            root.getChildren().add(editorContent);
            Scene scene = new Scene(root, 800.0, 600.0);

            assertSame(root, scene.getRoot());
            assertThrows(
                    IllegalStateException.class,
                    () -> NeuronSettingsOverlayCoordinator.resolveOverlayHost(editorContent)
            );
        });
    }
}
