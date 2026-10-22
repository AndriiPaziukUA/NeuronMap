package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.ConnectionControllerFactory;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.NeuronInteractionControllerFactory;
import com.example.neuronmap.controller.PulseAnimationController;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.editor.NeuronLayerOrderManager;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.menu.MainMenuView;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що координатор презентації одноосібно синхронізує подання нейронів і ручок обертання з моделлю. */
final class MapPresentationCoordinatorLifecycleTest {

    /** Запускає JavaFX перед тестами, що перевіряють реальне дерево графічних вузлів. */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /** Перевіряє створення подань і повне очищення вузла та ручки після видалення нейрона. */
    @Test
    void refreshAllSynchronizesNeuronViewsAndRotationHandles() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron first = fixture.neuronService.create(NeuronType.EXCITATORY, 80.0, 60.0);
                fixture.presentation.refreshAll();
                NeuronView firstView = fixture.neuronViews.get(first.id());
                RotationHandleView firstHandle = fixture.rotationHandles.get(first.id());
                assertNotNull(firstView);
                assertNotNull(firstHandle);
                assertSame(fixture.neuronService.presentation(first.id()).orElseThrow(), firstView.presentation());
                assertTrue(fixture.workspace.nodeLayer().getChildren().contains(firstView));
                assertTrue(fixture.workspace.overlayLayer().getChildren().contains(firstHandle));

                Neuron second = fixture.neuronService.create(NeuronType.INHIBITORY, 180.0, 90.0);
                fixture.presentation.refreshAll();
                assertEquals(2, fixture.neuronViews.size());
                assertNotNull(fixture.neuronViews.get(second.id()));
                assertNotNull(fixture.rotationHandles.get(second.id()));
                assertTrue(fixture.neuronService.remove(first.id()).isPresent());
                fixture.presentation.refreshAll();
                assertFalse(fixture.neuronViews.containsKey(first.id()));
                assertFalse(fixture.rotationHandles.containsKey(first.id()));
                assertFalse(fixture.workspace.nodeLayer().getChildren().contains(firstView));
                assertFalse(fixture.workspace.overlayLayer().getChildren().contains(firstHandle));
                assertEquals(1, fixture.neuronViews.size());
                assertTrue(fixture.neuronViews.containsKey(second.id()));
            } finally {
                fixture.dispose();
            }
        });
    }

    /**
     * Перевіряє, що координатор оновлює CSS-класи тіла нейрона під час вибору й скасування вибору.
     * Самі графічні властивості стилю перевіряються після застосування CSS у спеціалізованих UI-тестах.
     *
     * @throws Exception якщо перевірку неможливо виконати в JavaFX Application Thread.
     */
    @Test
    void presentationCoordinatorOwnsNeuronVisualRefresh() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron neuron = fixture.neuronService.create(NeuronType.EXCITATORY, 100.0, 80.0);
                fixture.presentation.refreshAll();
                NeuronView view = fixture.neuronViews.get(neuron.id());
                Rectangle body = (Rectangle) view.getChildren().get(0);

                assertTrue(body.getStyleClass().contains("neuron-card-excitatory"));
                assertFalse(body.getStyleClass().contains("neuron-card-selected"));

                fixture.state.selectOnly(neuron.id());
                fixture.neuronController.refreshVisuals();
                assertTrue(body.getStyleClass().contains("neuron-card-selected"));

                fixture.state.clearSelection();
                fixture.neuronController.refreshVisuals();
                assertTrue(body.getStyleClass().contains("neuron-card-excitatory"));
                assertFalse(body.getStyleClass().contains("neuron-card-selected"));
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє створення подання та ручки обертання при додаванні нейрона через координатор взаємодії. */
    @Test
    void addingNeuronCreatesViewAndRotationHandle() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            MapInteractionCoordinator interactions = new MapInteractionCoordinator(
                    fixture.neuronService, fixture.state, fixture.workspace, fixture.selectionController,
                    fixture.connectionController, fixture.neuronController,
                    fixture.presentation, () -> { }, ignored -> { }, fixture.localization);
            try {
                interactions.addNeuronAt(NeuronType.INHIBITORY, 150.0, 140.0);
                assertEquals(1, fixture.neuronViews.size());
                assertEquals(1, fixture.workspace.nodeLayer().getChildren().size());
                assertEquals(1, fixture.rotationHandles.size());
                assertEquals(1, fixture.workspace.overlayLayer().getChildren().size());
            } finally {
                interactions.dispose();
                fixture.dispose();
            }
        });
    }

    /** Перевіряє делегування очищення подань координатору після видалення вибраного нейрона. */
    @Test
    void deletingSelectedNeuronDelegatesViewCleanupToPresentationCoordinator() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron neuron = fixture.neuronService.create(NeuronType.EXCITATORY, 120.0, 120.0);
                fixture.presentation.refreshAll();
                NeuronView existingView = fixture.neuronViews.get(neuron.id());
                RotationHandleView existingHandle = fixture.rotationHandles.get(neuron.id());
                fixture.refreshCount.set(0);
                fixture.state.selectOnly(neuron.id());
                assertTrue(fixture.neuronController.deleteSelectedNeurons());
                assertTrue(fixture.neuronService.find(neuron.id()).isEmpty());
                assertFalse(fixture.neuronViews.containsKey(neuron.id()));
                assertFalse(fixture.rotationHandles.containsKey(neuron.id()));
                assertFalse(fixture.workspace.nodeLayer().getChildren().contains(existingView));
                assertFalse(fixture.workspace.overlayLayer().getChildren().contains(existingHandle));
                assertEquals(1, fixture.refreshCount.get());
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Виконує перевірку в JavaFX-потоці та передає винятки до потоку JUnit. */
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }

    /** Збирає справжні компоненти редактора для перевірки життєвого циклу подань. */
    private static final class Fixture {
        private final NeuronMapModel model = new NeuronMapModel();
        private final NeuronService neuronService = new NeuronService(model);
        private final GroupService groupService = new GroupService(model);
        private final ConnectionService connectionService = new ConnectionService(model);
        private final EditorState state = new EditorState(1.0, 0.0, 0.0);
        private final WorkspaceView workspace = new WorkspaceView();
        private final Map<String, NeuronView> neuronViews = new HashMap<>();
        private final Map<String, ConnectionView> connectionViews = new HashMap<>();
        private final Map<String, RotationHandleView> rotationHandles = new HashMap<>();
        private final LocalizationService localization = new LocalizationService(Locale.ENGLISH);
        private final StackPane settingsOverlayHost = new StackPane();
        private final MainMenuView mainMenu = new MainMenuView(localization, ignored -> { });
        private final NeuronSettingsOverlayCoordinator settingsOverlayCoordinator =
                new NeuronSettingsOverlayCoordinator(settingsOverlayHost, mainMenu, localization);
        private final AtomicInteger refreshCount = new AtomicInteger();
        private final AtomicReference<MapPresentationCoordinator> presentationReference = new AtomicReference<>();

        private final SelectionController selectionController = new SelectionController(
                groupService, state, () -> { }, () -> { }, ignored -> { }, localization);
        private final ConnectionController connectionController = ConnectionControllerFactory.create(
                new ConnectionControllerFactory.Dependencies(
                        connectionService, state, workspace, neuronViews, this::refreshPresentation,
                        () -> { }, ignored -> { }, localization));
        private final SimulationController simulationController = new SimulationController(
                neuronService, new SimulationService(),
                new SimulationStepPresenter(workspace, neuronViews, () -> { }, new PulseAnimationController(workspace)),
                () -> { }, ignored -> { }, ignored -> { }, ignored -> { },
                100.0, 10.0, 1_000.0, localization);
        private final NeuronInteractionController neuronController = NeuronInteractionControllerFactory.create(
                new NeuronInteractionControllerFactory.Dependencies(
                        neuronService, groupService, selectionController,
                        new NeuronClipboardService(neuronService, groupService), state, workspace,
                        neuronViews, rotationHandles, connectionController, simulationController,
                        () -> { }, this::refreshPresentation, ignored -> { },
                        new NeuronMenuView(new Pane(), state::selectedNeuronForMenu, ignored -> { }, localization),
                        localization, settingsOverlayCoordinator));
        private final MapPresentationCoordinator presentationCoordinator = new MapPresentationCoordinator(
                neuronService, workspace, neuronViews, connectionViews, rotationHandles,
                neuronController, connectionController,
                new NeuronLayerOrderManager(neuronViews, connectionViews, model::connections));
        private final MapPresentationCoordinator presentation = initializePresentationReference();

        /** Публікує створений координатор для callback-ів синхронізації подань. */
        private MapPresentationCoordinator initializePresentationReference() {
            presentationReference.set(presentationCoordinator);
            return presentationCoordinator;
        }

        /** Збільшує лічильник оновлень і передає запит реальному координатору презентації. */
        private void refreshPresentation() {
            refreshCount.incrementAndGet();
            MapPresentationCoordinator coordinator = presentationReference.get();
            if (coordinator != null) {
                coordinator.refreshAll();
            }
        }

        /** Завершує контролери та зупиняє анімації, щоб не залишати ресурси між тестами. */
        private void dispose() {
            neuronController.dispose();
            simulationController.shutdown();
            connectionController.dispose();
            mainMenu.dispose();
        }
    }
}
