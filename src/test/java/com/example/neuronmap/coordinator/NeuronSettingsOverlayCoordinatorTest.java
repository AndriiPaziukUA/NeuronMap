package com.example.neuronmap.coordinator;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.neuron.NeuronSettingsView;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє життєвий цикл накладки та розрізнення звичайного й програмного закриття. */
final class NeuronSettingsOverlayCoordinatorTest {

    /** Перевіряє, що відкриття головного меню закриває налаштування без виконання дії звичайного закриття. */
    @Test
    void openingMainMenuClosesSettingsOverlayWithoutRunningCloseAction() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
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
                    new NeuronSettingsOverlayCoordinator(root, mainMenu, localization);
            AtomicInteger closeActions = new AtomicInteger();

            coordinator.show(
                    new Neuron("overlay", NeuronType.EXCITATORY, 0, 5, 3),
                    ignored -> { },
                    closeActions::incrementAndGet
            );
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
            assertEquals(0, closeActions.get());

            coordinator.hide();
            coordinator.dispose();
            coordinator.dispose();
            mainMenu.hide();
            mainMenu.dispose();
        });
    }

    /** Перевіряє, що кнопка скасування викликає дію закриття рівно один раз. */
    @Test
    void runsCloseActionWhenUserCancelsSettings() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            WorkspaceView workspace = new WorkspaceView();
            MainMenuView mainMenu = new MainMenuView(localization, ignored -> { });
            StackPane root = new StackPane(workspace.node(), mainMenu);
            new Scene(root, 900.0, 600.0);
            NeuronSettingsOverlayCoordinator coordinator =
                    new NeuronSettingsOverlayCoordinator(root, mainMenu, localization);
            AtomicInteger closeActions = new AtomicInteger();

            coordinator.show(
                    new Neuron("cancel-settings", NeuronType.EXCITATORY, 0, 5, 3),
                    ignored -> { },
                    closeActions::incrementAndGet
            );
            NeuronSettingsView settingsView = root.getChildren().stream()
                    .filter(NeuronSettingsView.class::isInstance)
                    .map(NeuronSettingsView.class::cast)
                    .findFirst()
                    .orElseThrow();
            Button cancelButton = settingsView.lookupAll(".neuron-settings-button").stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> "Cancel".equals(button.getText()))
                    .findFirst()
                    .orElseThrow();

            cancelButton.fire();

            assertFalse(root.getChildren().contains(settingsView));
            assertEquals(1, closeActions.get());

            coordinator.hide();
            coordinator.dispose();
            mainMenu.dispose();
        });
    }

    /** Перевіряє, що програмне приховування накладки не викликає дію її звичайного закриття. */
    @Test
    void programmaticHideDoesNotRunCloseAction() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            WorkspaceView workspace = new WorkspaceView();
            MainMenuView mainMenu = new MainMenuView(localization, ignored -> { });
            StackPane root = new StackPane(workspace.node(), mainMenu);
            new Scene(root, 900.0, 600.0);
            NeuronSettingsOverlayCoordinator coordinator =
                    new NeuronSettingsOverlayCoordinator(root, mainMenu, localization);
            AtomicInteger closeActions = new AtomicInteger();

            coordinator.show(
                    new Neuron("programmatic-hide", NeuronType.EXCITATORY, 0, 5, 3),
                    ignored -> { },
                    closeActions::incrementAndGet
            );

            coordinator.hide();

            assertEquals(0, closeActions.get());
            coordinator.dispose();
            mainMenu.dispose();
        });
    }

    /** Перевіряє, що накладка не відкривається поверх головного меню, яке вже відображається. */
    @Test
    void doesNotShowSettingsWhileMainMenuIsAlreadyVisible() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
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
                    new NeuronSettingsOverlayCoordinator(root, mainMenu, localization);
            mainMenu.showMenu();

            coordinator.show(new Neuron("menu-visible", NeuronType.EXCITATORY, 0, 5, 3), ignored -> { });

            assertTrue(mainMenu.isMenuVisible());
            assertFalse(root.getChildren().stream().anyMatch(NeuronSettingsView.class::isInstance));
            coordinator.dispose();
            mainMenu.hide();
            mainMenu.dispose();
        });
    }

    /** Перевіряє, що після остаточного звільнення координатор не може створити нову накладку. */
    @Test
    void disposedCoordinatorRejectsFurtherShows() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            WorkspaceView workspace = new WorkspaceView();
            MainMenuView mainMenu = new MainMenuView(localization, ignored -> { });
            StackPane root = new StackPane(workspace.node(), mainMenu);
            new Scene(root, 900.0, 600.0);
            NeuronSettingsOverlayCoordinator coordinator =
                    new NeuronSettingsOverlayCoordinator(root, mainMenu, localization);
            coordinator.dispose();

            assertThrows(
                    IllegalStateException.class,
                    () -> coordinator.show(new Neuron("disposed", NeuronType.EXCITATORY, 0, 5, 3), ignored -> { })
            );
            mainMenu.dispose();
        });
    }
}
