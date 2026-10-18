package com.example.neuronmap.controller;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє пошук контейнера внутрішньої накладки в ієрархії головної сцени. */
final class NeuronMenuControllerOverlayHostTest {

    /** Запускає JavaFX toolkit перед створенням графічних вузлів.
     *
     * @throws Exception якщо toolkit не запускається у встановлений час.
     */
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

    /**
     * Перевіряє, що обирається кореневий StackPane редактора, а не BorderPane-обгортка Scene.
     *
     * @throws Exception якщо перевірку не вдається виконати в JavaFX Application Thread.
     */
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
            assertSame(
                    editorRoot,
                    NeuronMenuController.resolveSettingsOverlayHost(workspaceViewport)
            );
        });
    }

    /**
     * Перевіряє явну помилку, якщо вузол не має предка, придатного для накладки.
     *
     * @throws Exception якщо перевірку не вдається виконати в JavaFX Application Thread.
     */
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
                    () -> NeuronMenuController.resolveSettingsOverlayHost(editorContent)
            );
        });
    }

    /**
     * Виконує UI-перевірку на JavaFX Application Thread і передає можливу помилку до JUnit.
     *
     * @param action дія, яку потрібно виконати у потоці JavaFX.
     * @throws Exception якщо виконання не завершується у встановлений час.
     */
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
