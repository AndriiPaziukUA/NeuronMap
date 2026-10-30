package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.coordinator.NeuronSettingsOverlayCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronSettingsView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє зміну напрямку нейрона, узгодження подань і збереження змін через контролер меню. */
final class NeuronMenuControllerDirectionTest {

    /** Перевіряє оновлення залежних подань, збереження історії та повідомлення після зміни напрямку. */
    @Test
    void togglingDirectionRefreshesViewsAndSavesChanges() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture();
            NeuronPresentation presentation = fixture.neuronService()
                    .presentation(fixture.neuron().id()).orElseThrow();

            assertFalse(presentation.directionReversed());
            fixture.controller().toggleDirection(fixture.neuron().id());
            assertTrue(presentation.directionReversed());
            assertEquals(1, fixture.visualRefreshes().get());
            assertEquals(1, fixture.connectionRefreshes().get());
            assertEquals(1, fixture.overlayRefreshes().get());
            assertEquals(1, fixture.saves().get());
            assertEquals(fixture.localization().text("status.direction_changed"), fixture.status().get());
            assertTrue(fixture.historyService().canUndo());
            assertTrue(fixture.historyService().undo());
            assertFalse(fixture.neuronService().presentation(fixture.neuron().id())
                    .orElseThrow().directionReversed());
            assertTrue(fixture.historyService().redo());
            assertTrue(fixture.neuronService().presentation(fixture.neuron().id())
                    .orElseThrow().directionReversed());
            dispose(fixture);
        });
    }

    /** Перевіряє, що команда для відсутнього нейрона не викликає оновлення, збереження або показ статусу. */
    @Test
    void missingNeuronDoesNotTriggerRefreshSaveOrStatus() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture();
            fixture.controller().toggleDirection("missing-neuron");
            assertEquals(0, fixture.visualRefreshes().get());
            assertEquals(0, fixture.connectionRefreshes().get());
            assertEquals(0, fixture.overlayRefreshes().get());
            assertEquals(0, fixture.saves().get());
            assertNull(fixture.status().get());
            dispose(fixture);
        });
    }

    /** Перевіряє, що контролер приховує три обмежені кнопки лише для внутрішнього нейрона структури. */
    @Test
    void hidesDirectionTypeAndSettingsForStructureMembers() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture();
            fixture.neuronView().setStructureMember(true);
            fixture.workspace().nodeLayer().getChildren().add(fixture.neuronView());
            new Scene(fixture.overlayHost(), 900.0, 600.0);

            fixture.controller().show(fixture.neuronView());
            HBox buttons = (HBox) fixture.controller().node().getChildren().getFirst();

            for (String iconStyleClass : new String[]{
                    "neuron-menu-icon-direction",
                    "neuron-menu-icon-toggle-type",
                    "neuron-menu-icon-settings"
            }) {
                Button restrictedButton = buttons.getChildren().stream()
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .filter(button -> button.getGraphic() != null
                                && button.getGraphic().getStyleClass().contains(iconStyleClass))
                        .findFirst()
                        .orElseThrow();
                assertFalse(restrictedButton.isVisible(), iconStyleClass + " не має відображатися");
                assertFalse(restrictedButton.isManaged(), iconStyleClass + " не має займати місце в компонуванні");
            }
            assertEquals(3, buttons.getChildren().stream()
                    .map(Button.class::cast)
                    .filter(Button::isVisible)
                    .count(), "Імпульс, додавання зв’язку та видалення мають залишатися видимими");

            dispose(fixture);
        });
    }

    /** Перевіряє, що відкрите меню нейрона змінює переклади після зміни мови в головному меню. */
    @Test
    void refreshesOpenNeuronMenuWhenLanguagePreviewChanges() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture();
            fixture.workspace().nodeLayer().getChildren().add(fixture.neuronView());
            new Scene(fixture.overlayHost(), 900.0, 600.0);
            fixture.controller().show(fixture.neuronView());
            VBox ukrainianMenu = fixture.controller().node();
            assertEquals("Імпульс", fireButtonTooltip(ukrainianMenu));

            fixture.mainMenu().showMenu();
            fixture.localization().previewLanguage(SupportedLanguage.ENGLISH);

            VBox englishMenu = fixture.controller().node();
            assertTrue(fixture.controller().isVisible());
            assertNotSame(ukrainianMenu, englishMenu,
                    "Відкрите контекстне меню має бути перебудоване з перекладами нової локалі");
            assertEquals("Pulse", fireButtonTooltip(englishMenu));

            fixture.localization().discardLanguagePreview();
            assertEquals("Імпульс", fireButtonTooltip(fixture.controller().node()));
            dispose(fixture);
        });
    }

    /** Перевіряє, що після скасування налаштувань меню повторно з'являється над тим самим нейроном. */
    @Test
    void restoresContextMenuAfterSettingsAreCancelled() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture();
            fixture.workspace().nodeLayer().getChildren().add(fixture.neuronView());
            new Scene(fixture.overlayHost(), 900.0, 600.0);
            fixture.controller().show(fixture.neuronView());
            VBox originalMenu = fixture.controller().node();
            HBox buttons = (HBox) originalMenu.getChildren().getFirst();
            Button settingsButton = buttons.getChildren().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> button.getGraphic() != null
                            && button.getGraphic().getStyleClass().contains("neuron-menu-icon-settings"))
                    .findFirst()
                    .orElseThrow();

            settingsButton.fire();

            assertFalse(fixture.controller().isVisible());
            NeuronSettingsView settingsView = fixture.overlayHost().getChildren().stream()
                    .filter(NeuronSettingsView.class::isInstance)
                    .map(NeuronSettingsView.class::cast)
                    .findFirst()
                    .orElseThrow();
            Button cancelButton = settingsView.lookupAll(".neuron-settings-button").stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> !button.getText().equals(
                            fixture.localization().text("neuron.settings.save")))
                    .findFirst()
                    .orElseThrow();

            cancelButton.fire();

            assertTrue(fixture.controller().isVisible());
            assertTrue(fixture.workspace().overlayLayer().getChildren().contains(fixture.controller().node()));
            assertEquals(fixture.neuron().id(), fixture.state().selectedNeuronForMenu());
            dispose(fixture);
        });
    }

    /** Перевіряє перемикання шухлядки з контекстного меню та збереження її дії після перемальовування локалі. */
    @Test
    void structureMenuButtonTogglesDrawerAndKeepsItsActionAfterLocalizationRefresh() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture();
            Neuron structure = fixture.neuronService().create(NeuronType.STRUCTURE, 220.0, 160.0);
            NeuronView structureView = new NeuronView(
                    fixture.neuronService().presentation(structure.id()).orElseThrow());
            fixture.workspace().nodeLayer().getChildren().add(structureView);
            new Scene(fixture.overlayHost(), 900.0, 600.0);
            AtomicInteger drawerToggles = new AtomicInteger();
            fixture.controller().show(structureView, () -> {
                structureView.toggleStructureDrawer();
                drawerToggles.incrementAndGet();
            });

            Button drawerButton = structureDrawerButton(fixture.controller().node());
            assertFalse(structureView.isStructureDrawerExpanded());
            assertEquals("Відкрити", drawerButton.getTooltip().getText());
            assertTrue(drawerButton.getGraphic().getStyleClass()
                    .contains("neuron-menu-icon-structure-drawer-collapsed"));

            drawerButton.fire();
            assertTrue(structureView.isStructureDrawerExpanded());
            assertEquals(1, drawerToggles.get());
            assertEquals("Закрити", drawerButton.getTooltip().getText());
            assertTrue(drawerButton.getGraphic().getStyleClass()
                    .contains("neuron-menu-icon-structure-drawer-expanded"));

            fixture.mainMenu().showMenu();
            fixture.localization().previewLanguage(SupportedLanguage.ENGLISH);
            Button englishDrawerButton = structureDrawerButton(fixture.controller().node());
            assertEquals("Close", englishDrawerButton.getTooltip().getText());
            englishDrawerButton.fire();

            assertFalse(structureView.isStructureDrawerExpanded());
            assertEquals(2, drawerToggles.get());
            assertTrue(fixture.state().isIdle(), "Кнопка структури не повинна запускати імпульс симуляції");
            fixture.localization().discardLanguagePreview();
            fixture.mainMenu().hide();

            Button settingsButton = ((HBox) fixture.controller().node().getChildren().getFirst())
                    .getChildren().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> button.getGraphic() != null
                            && button.getGraphic().getStyleClass().contains("neuron-menu-icon-settings"))
                    .findFirst()
                    .orElseThrow();
            settingsButton.fire();
            NeuronSettingsView settingsView = fixture.overlayHost().getChildren().stream()
                    .filter(NeuronSettingsView.class::isInstance)
                    .map(NeuronSettingsView.class::cast)
                    .findFirst()
                    .orElseThrow();
            Button cancelButton = settingsView.lookupAll(".neuron-settings-button").stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> !button.getText().equals(
                            fixture.localization().text("neuron.settings.save")))
                    .findFirst()
                    .orElseThrow();
            cancelButton.fire();

            Button restoredDrawerButton = structureDrawerButton(fixture.controller().node());
            restoredDrawerButton.fire();
            assertTrue(structureView.isStructureDrawerExpanded(),
                    "Після закриття налаштувань кнопка шухлядки має зберігати свою дію");
            assertEquals(3, drawerToggles.get());
            dispose(fixture);
        });
    }

    /**
     * Повертає кнопку перемикання шухлядки в першому ряду контекстного меню структури.
     *
     * @param menu контекстне меню структури.
     * @return кнопка, що змінює стан шухлядки.
     */
    private static Button structureDrawerButton(VBox menu) {
        HBox buttons = (HBox) menu.getChildren().getFirst();
        return buttons.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getStyleClass().contains("neuron-menu-structure-drawer"))
                .findFirst()
                .orElseThrow();
    }

    /** Повертає текст підказки кнопки активації, щоб перевірити локалізацію меню. */
    private static String fireButtonTooltip(VBox menu) {
        HBox buttons = (HBox) menu.getChildren().getFirst();
        return buttons.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getGraphic() != null
                        && button.getGraphic().getStyleClass().contains("neuron-menu-icon-fire"))
                .map(button -> button.getTooltip().getText())
                .findFirst()
                .orElseThrow();
    }

    /** Створює контролер із явним контейнером накладок і лічильниками побічних ефектів. */
    private static Fixture createFixture() {
        LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neuronService = new NeuronService(model);
        Neuron neuron = neuronService.create(NeuronType.EXCITATORY, 100.0, 80.0);
        HistoryService historyService = new HistoryService(model);
        historyService.initialize();
        NeuronView neuronView = new NeuronView(neuronService.presentation(neuron.id()).orElseThrow());
        Map<String, NeuronView> neuronViews = new HashMap<>();
        neuronViews.put(neuron.id(), neuronView);

        EditorState state = new EditorState(1.0, 0.0, 0.0);
        WorkspaceView workspace = new WorkspaceView();
        AtomicInteger visualRefreshes = new AtomicInteger();
        AtomicInteger connectionRefreshes = new AtomicInteger();
        AtomicInteger overlayRefreshes = new AtomicInteger();
        AtomicInteger saves = new AtomicInteger();
        AtomicReference<String> status = new AtomicReference<>();

        ConnectionController connections = ConnectionControllerFactory.create(
                new ConnectionControllerFactory.Dependencies(
                        new ConnectionService(model), state, workspace, neuronViews,
                        () -> { }, () -> { }, _ -> { }, localization));
        SimulationStepPresenter stepPresenter = new SimulationStepPresenter(
                workspace, neuronViews, () -> { }, new PulseAnimationController(workspace));
        SimulationController simulation = new SimulationController(
                neuronService, new SimulationService(), stepPresenter,
                () -> { }, _ -> { }, _ -> { }, _ -> { },
                100.0, 10.0, 1000.0, localization);
        NeuronDeletionController deletion = new NeuronDeletionController(
                neuronService, state, workspace, () -> { }, () -> { }, () -> { },
                _ -> { }, localization);
        NeuronMenuView menuView = new NeuronMenuView(
                new VBox(), state::selectedNeuronForMenu, ignored -> { }, localization);
        MainMenuView mainMenu = new MainMenuView(localization, ignored -> { });
        mainMenu.setActions(new MainMenuView.Actions(
                () -> { },
                () -> { },
                () -> { },
                () -> { },
                () -> { },
                ignored -> { },
                (project, _) -> project,
                ignored -> { },
                () -> { }
        ));
        StackPane overlayHost = new StackPane(workspace.node(), mainMenu);
        NeuronSettingsOverlayCoordinator settingsCoordinator =
                new NeuronSettingsOverlayCoordinator(overlayHost, mainMenu, localization);
        NeuronMenuController controller = new NeuronMenuController(
                neuronService, state, workspace, connections, simulation, deletion, menuView,
                visualRefreshes::incrementAndGet,
                connectionRefreshes::incrementAndGet,
                overlayRefreshes::incrementAndGet,
                () -> {
                    saves.incrementAndGet();
                    historyService.commitSavedState();
                },
                status::set,
                localization,
                settingsCoordinator);

        return new Fixture(controller, neuronService, historyService, neuron, visualRefreshes,
                connectionRefreshes, overlayRefreshes, saves, status, localization, mainMenu, simulation,
                connections, workspace, neuronView, overlayHost, state);
    }

    /** Звільняє ресурси контролера накладки, симуляції, зв'язків і подання головного меню. */
    private static void dispose(Fixture fixture) {
        fixture.controller().dispose();
        fixture.simulation().shutdown();
        fixture.connections().dispose();
        fixture.mainMenu().dispose();
    }

    /** Зберігає залежності тестового сценарію та спостережувані побічні ефекти. */
    private record Fixture(
            NeuronMenuController controller,
            NeuronService neuronService,
            HistoryService historyService,
            Neuron neuron,
            AtomicInteger visualRefreshes,
            AtomicInteger connectionRefreshes,
            AtomicInteger overlayRefreshes,
            AtomicInteger saves,
            AtomicReference<String> status,
            LocalizationService localization,
            MainMenuView mainMenu,
            SimulationController simulation,
            ConnectionController connections,
            WorkspaceView workspace,
            NeuronView neuronView,
            StackPane overlayHost,
            EditorState state
    ) { }
}
