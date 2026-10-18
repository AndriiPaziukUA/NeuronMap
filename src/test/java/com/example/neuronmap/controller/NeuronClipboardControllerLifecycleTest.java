package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє перенесення клавіатурних підписок буфера обміну й повне їх від'єднання. */
final class NeuronClipboardControllerLifecycleTest {

    /** Перевіряє перенесення підписок між сценами та очищення буфера після dispose. */
    @Test
    void sceneChangesMoveSubscriptionsAndDisposeClearsClipboard() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            NeuronService neuronService = new NeuronService(model);
            var neuron = neuronService.create(NeuronType.EXCITATORY, 20.0, 30.0);
            NeuronClipboardService clipboardService = new NeuronClipboardService(
                    neuronService,
                    new GroupService(model)
            );
            EditorState state = new EditorState(1.0, 0.0, 0.0);
            state.selectOnly(neuron.id());
            WorkspaceView workspace = new WorkspaceView();
            StackPane firstRoot = new StackPane(workspace.node());
            Scene firstScene = new Scene(firstRoot, 640.0, 480.0);
            AtomicInteger statusCalls = new AtomicInteger();
            NeuronClipboardController controller = new NeuronClipboardController(
                    clipboardService,
                    state,
                    workspace,
                    () -> { },
                    () -> { },
                    () -> { },
                    ignored -> statusCalls.incrementAndGet(),
                    new LocalizationService(Locale.ENGLISH)
            );

            fireCopy(firstScene);
            assertEquals(1, statusCalls.get());
            assertTrue(clipboardService.hasContent());
            controller.clearClipboard();
            fireCopy(firstScene);
            assertEquals(2, statusCalls.get(), "Очищення буфера не повинно від'єднувати обробники");
            assertTrue(clipboardService.hasContent());

            firstRoot.getChildren().remove(workspace.node());
            Scene secondScene = new Scene(new StackPane(workspace.node()), 640.0, 480.0);
            fireCopy(firstScene);
            assertEquals(2, statusCalls.get(), "Попередня сцена не повинна зберігати обробник");
            Event.fireEvent(firstScene, new WindowEvent(new Stage(), WindowEvent.WINDOW_HIDDEN));
            assertTrue(clipboardService.hasContent(), "Стара сцена не повинна скидати буфер після перенесення");
            fireCopy(secondScene);
            assertEquals(3, statusCalls.get(), "Нова сцена повинна отримати клавіатурний обробник");

            controller.dispose();
            controller.dispose();
            fireCopy(secondScene);
            assertEquals(3, statusCalls.get());
            assertFalse(clipboardService.hasContent());
        });
    }

    /** Передає Ctrl+C сцені, щоб перевірити фактичну реєстрацію клавіатурного фільтра. */
    private static void fireCopy(Scene scene) {
        Event.fireEvent(scene, new KeyEvent(
                KeyEvent.KEY_PRESSED, "c", "c", KeyCode.C,
                false, true, false, false));
    }
}
