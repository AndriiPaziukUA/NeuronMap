package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Scale;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє раннє застосування стану камери до показу вікна без передчасного оновлення накладок. */
final class CameraControllerInitialTransformTest {

    /** Перевіряє, що масштаб і панорамування можна застосувати до першого компонування сцени. */
    @Test
    void appliesRestoredTransformBeforeViewportLayout() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            EditorState state = new EditorState(2.0, 37.0, -21.0);
            WorkspaceView workspace = new WorkspaceView();
            AtomicInteger overlayRefreshCount = new AtomicInteger();
            CameraController controller = new CameraController(
                    state,
                    workspace,
                    overlayRefreshCount::incrementAndGet,
                    () -> { },
                    () -> false,
                    ignored -> false,
                    ignored -> { },
                    ignored -> { },
                    1.25,
                    new LocalizationService(Locale.ENGLISH)
            );

            try {
                Pane world = (Pane) workspace.node().getChildren().get(1);
                Scale scale = (Scale) world.getTransforms().get(0);

                assertEquals(0.0, workspace.node().getWidth());
                controller.applyTransform();

                assertEquals(2.0, scale.getX());
                assertEquals(2.0, scale.getY());
                assertEquals(37.0, world.getTranslateX());
                assertEquals(-21.0, world.getTranslateY());
                assertEquals(0, overlayRefreshCount.get(),
                        "До компонування можна застосувати камеру, не обчислюючи геометрію накладок");

                controller.apply();
                assertEquals(1, overlayRefreshCount.get());
            } finally {
                controller.dispose();
                workspace.dispose();
            }
        });
    }
}
