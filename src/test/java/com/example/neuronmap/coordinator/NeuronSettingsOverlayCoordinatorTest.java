package com.example.neuronmap.coordinator;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.neuron.NeuronSettingsView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє узгоджений життєвий цикл модальної накладки з головним меню. */
final class NeuronSettingsOverlayCoordinatorTest {

    /** Запускає JavaFX toolkit перед тестуванням подань і координатора накладок. */
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

    /** Перевіряє, що відкриття головного меню прибирає накладку та завершує її тимчасову підписку. */
    @Test
    void openingMainMenuClosesSettingsOverlay() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            WorkspaceView workspace = new WorkspaceView();
            MainMenuView mainMenu = new MainMenuView(localization, ignored -> { });
            mainMenu.setActions(new MainMenuView.Actions(
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    ignored -> { },
                    (project, name) -> project,
                    ignored -> { },
                    () -> { }
            ));
            StackPane root = new StackPane(workspace.node(), mainMenu);
            new Scene(root, 900.0, 600.0);
            NeuronSettingsOverlayCoordinator coordinator =
                    new NeuronSettingsOverlayCoordinator(workspace, localization);

            coordinator.show(new Neuron("overlay", NeuronType.EXCITATORY, 0, 5, 3), ignored -> { });
            NeuronSettingsView settingsView = root.getChildren().stream()
                    .filter(NeuronSettingsView.class::isInstance)
                    .map(NeuronSettingsView.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertTrue(settingsView.isVisible());

            mainMenu.showMenu();

            assertTrue(mainMenu.isMenuVisible());
            assertFalse(root.getChildren().contains(settingsView));
            assertFalse(settingsView.blocksEditorShortcuts());

            coordinator.hide();
            mainMenu.hide();
            mainMenu.dispose();
        });
    }

    /** Виконує UI-перевірку в потоці JavaFX і передає помилку до JUnit-потоку. */
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
            throw new AssertionError("Перевірка життєвого циклу накладки завершилася помилкою", failure[0]);
        }
    }
}
