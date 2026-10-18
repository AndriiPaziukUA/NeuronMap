package com.example.neuronmap.controller;

import com.example.neuronmap.coordinator.NeuronSettingsOverlayCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.menu.MainMenuView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє вибір контейнера внутрішньої накладки у вкладеній ієрархії головної сцени. */
final class NeuronMenuControllerOverlayHostTest {

    /** Запускає JavaFX toolkit перед створенням графічних вузлів. */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX toolkit має запуститися");
    }

    /** Перевіряє, що контейнером накладки є StackPane з головним меню, а не зовнішня обгортка вікна. */
    @Test
    void prefersStackPaneThatDirectlyContainsMainMenu() throws Exception {
        runOnFxThread(() -> {
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

    /** Перевіряє сумісний вибір найзовнішнього StackPane, якщо головного меню немає. */
    @Test
    void resolvesEditorRootWhenSceneRootIsWindowChromeContainer() throws Exception {
        runOnFxThread(() -> {
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

    /** Перевіряє явну помилку, якщо вузол не має предка, придатного для накладки. */
    @Test
    void rejectsHierarchyWithoutStackPaneAncestor() throws Exception {
        runOnFxThread(() -> {
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

    /** Виконує UI-перевірку на JavaFX Application Thread і передає помилку до JUnit. */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX action має завершитися");
        if (failure[0] != null) {
            throw new AssertionError("Перевірка контейнера накладки завершилася помилкою", failure[0]);
        }
    }
}
