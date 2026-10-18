package com.example.neuronmap.controller;

import com.example.neuronmap.coordinator.NeuronSettingsOverlayCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.neuron.NeuronSettingsView;

import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє використання явно переданого контейнера модальної накладки без обходу дерева JavaFX. */
final class NeuronMenuControllerOverlayHostTest {

    /** Перевіряє, що накладка додається лише до переданого контейнера навіть за наявності зовнішніх обгорток. */
    @Test
    void usesExplicitOverlayHostInsteadOfSearchingAncestorTree() throws Exception {
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
            StackPane explicitHost = new StackPane(workspace.node());
            StackPane editorRoot = new StackPane(explicitHost, mainMenu);
            BorderPane windowChromeRoot = new BorderPane(editorRoot);
            StackPane outerWrapper = new StackPane(windowChromeRoot);
            Scene scene = new Scene(outerWrapper, 800.0, 600.0);
            NeuronSettingsOverlayCoordinator coordinator =
                    new NeuronSettingsOverlayCoordinator(explicitHost, mainMenu, localization);

            coordinator.show(new Neuron("explicit-host", NeuronType.EXCITATORY, 0, 5, 3), ignored -> { });
            NeuronSettingsView settingsView = explicitHost.getChildren().stream()
                    .filter(NeuronSettingsView.class::isInstance)
                    .map(NeuronSettingsView.class::cast)
                    .findFirst()
                    .orElseThrow();

            assertSame(outerWrapper, scene.getRoot());
            assertTrue(explicitHost.getChildren().contains(settingsView));
            assertFalse(editorRoot.getChildren().contains(settingsView),
                    "Накладка має додаватися до ін'єктованого host, навіть коли меню є його сусіднім вузлом");

            mainMenu.showMenu();
            assertFalse(explicitHost.getChildren().contains(settingsView),
                    "Координатор має стежити за явно переданим меню без пошуку його в дочірніх вузлах host");

            coordinator.dispose();
            mainMenu.hide();
            mainMenu.dispose();
        });
    }

    /** Перевіряє, що відсутність сцени не призводить до пошуку довільного контейнера чи винятку. */
    @Test
    void doesNotShowOverlayBeforeExplicitHostIsAttachedToScene() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            WorkspaceView workspace = new WorkspaceView();
            MainMenuView mainMenu = new MainMenuView(localization, ignored -> { });
            StackPane explicitHost = new StackPane(workspace.node(), mainMenu);
            NeuronSettingsOverlayCoordinator coordinator =
                    new NeuronSettingsOverlayCoordinator(explicitHost, mainMenu, localization);

            coordinator.show(new Neuron("unattached", NeuronType.EXCITATORY, 0, 5, 3), ignored -> { });

            assertFalse(explicitHost.getChildren().stream().anyMatch(NeuronSettingsView.class::isInstance));
            coordinator.dispose();
            mainMenu.dispose();
        });
    }
}
