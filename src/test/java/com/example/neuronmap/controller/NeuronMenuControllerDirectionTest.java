package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.coordinator.NeuronSettingsOverlayCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
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
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
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
                        () -> { }, () -> { }, message -> { }, localization));
        SimulationStepPresenter stepPresenter = new SimulationStepPresenter(
                workspace, neuronViews, () -> { }, new PulseAnimationController(workspace));
        SimulationController simulation = new SimulationController(
                neuronService, new SimulationService(), stepPresenter,
                () -> { }, message -> { }, paused -> { }, active -> { },
                100.0, 10.0, 1000.0, localization);
        NeuronDeletionController deletion = new NeuronDeletionController(
                neuronService, state, workspace, () -> { }, () -> { }, () -> { },
                message -> { }, localization);
        NeuronMenuView menuView = new NeuronMenuView(
                new VBox(), state::selectedNeuronForMenu, null, localization);
        MainMenuView mainMenu = new MainMenuView(localization, ignored -> { });
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

        return new Fixture(controller, neuronService, historyService, neuron,
                visualRefreshes, connectionRefreshes, overlayRefreshes, saves, status, localization, mainMenu,
                simulation, connections);
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
            ConnectionController connections
    ) { }
}
