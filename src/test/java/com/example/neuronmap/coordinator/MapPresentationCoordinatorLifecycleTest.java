package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.NeuronLayerOrderController;
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
import com.example.neuronmap.view.NeuronMenuView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.RotationHandleView;
import com.example.neuronmap.view.SimulationStepPresenter;
import com.example.neuronmap.view.WorkspaceView;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє, що координатор презентації одноосібно синхронізує подання нейронів і ручок обертання з моделлю.
 */
final class MapPresentationCoordinatorLifecycleTest {

    /**
     * Запускає JavaFX перед тестами, що перевіряють реальне дерево графічних вузлів.
     *
     * @throws Exception якщо запуск JavaFX не завершився вчасно.
     */
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

    /**
     * Перевіряє створення відсутніх подань і повне очищення вузла та ручки після вилучення нейрона з моделі.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void refreshAllSynchronizesNeuronViewsAndRotationHandles() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron first = fixture.neuronService.create(
                        NeuronType.EXCITATORY,
                        80.0,
                        60.0
                );

                fixture.presentation.refreshAll();

                NeuronView firstView = fixture.neuronViews.get(first.id());
                RotationHandleView firstHandle = fixture.rotationHandles.get(first.id());
                assertNotNull(firstView);
                assertNotNull(firstHandle);
                assertSame(
                        fixture.neuronService.presentation(first.id()),
                        firstView.presentation()
                );
                assertTrue(fixture.workspace.nodeLayer().getChildren().contains(firstView));
                assertTrue(fixture.workspace.overlayLayer().getChildren().contains(firstHandle));

                Neuron second = fixture.neuronService.create(
                        NeuronType.INHIBITORY,
                        180.0,
                        90.0
                );
                fixture.presentation.refreshAll();

                assertEquals(2, fixture.neuronViews.size());
                assertNotNull(fixture.neuronViews.get(second.id()));
                assertNotNull(fixture.rotationHandles.get(second.id()));

                assertNotNull(fixture.neuronService.remove(first.id()));
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
     * Перевіряє, що додавання нейрона через координатор взаємодії створює подання та ручку обертання.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void addingNeuronCreatesViewAndRotationHandle() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            MapInteractionCoordinator interactions = new MapInteractionCoordinator(
                    fixture.neuronService,
                    fixture.state,
                    fixture.workspace,
                    fixture.selectionController,
                    fixture.connectionController,
                    fixture.neuronController,
                    fixture.simulationController,
                    fixture.presentation,
                    () -> { },
                    ignored -> { },
                    fixture.localization
            );
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

    /**
     * Перевіряє, що видалення через контролер запускає єдине оновлення презентації без прямого очищення UI-карт.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void deletingSelectedNeuronDelegatesViewCleanupToPresentationCoordinator() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron neuron = fixture.neuronService.create(
                        NeuronType.EXCITATORY,
                        120.0,
                        120.0
                );
                fixture.presentation.refreshAll();
                NeuronView existingView = fixture.neuronViews.get(neuron.id());
                RotationHandleView existingHandle = fixture.rotationHandles.get(neuron.id());

                fixture.refreshCount.set(0);
                fixture.state.selectOnly(neuron.id());

                assertTrue(fixture.neuronController.deleteSelectedNeurons());

                assertNull(fixture.neuronService.find(neuron.id()));
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

    /**
     * Виконує перевірку в JavaFX Application Thread і передає її виняток потоку тестів.
     *
     * @param action перевірка, яку потрібно виконати в JavaFX-потоці.
     * @throws Exception якщо потік JavaFX не відповів вчасно або перевірка завершилася помилкою.
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

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }

    /**
     * Збирає реальні контролери та подання для перевірки життєвого циклу без запуску головного вікна застосунку.
     */
    private static final class Fixture {

        private final NeuronMapModel model = new NeuronMapModel();
        private final NeuronService neuronService = new NeuronService(model);
        private final GroupService groupService = new GroupService(model);
        private final ConnectionService connectionService = new ConnectionService(model);
        private final EditorState state = new EditorState(1.0, 0.0, 0.0);
        private final WorkspaceView workspace = new WorkspaceView();
        private final Map<String, NeuronView> neuronViews = new HashMap<>();
        private final Map<String, com.example.neuronmap.view.ConnectionView> connectionViews =
                new HashMap<>();
        private final Map<String, RotationHandleView> rotationHandles = new HashMap<>();
        private final LocalizationService localization = new LocalizationService(Locale.ENGLISH);
        private final AtomicInteger refreshCount = new AtomicInteger();
        private final AtomicReference<MapPresentationCoordinator> presentationReference =
                new AtomicReference<>();

        private final SelectionController selectionController = new SelectionController(
                groupService,
                state,
                () -> { },
                () -> { },
                ignored -> { },
                localization
        );
        private final ConnectionController connectionController = new ConnectionController(
                connectionService,
                state,
                workspace,
                neuronViews,
                this::refreshPresentation,
                () -> { },
                ignored -> { },
                localization
        );
        private final SimulationController simulationController = new SimulationController(
                neuronService,
                new SimulationService(),
                new SimulationStepPresenter(
                        workspace,
                        neuronViews,
                        () -> { },
                        new PulseAnimationController(workspace)
                ),
                () -> { },
                ignored -> { },
                ignored -> { },
                ignored -> { },
                100.0,
                10.0,
                1_000.0,
                localization
        );
        private final NeuronInteractionController neuronController =
                new NeuronInteractionController(
                        neuronService,
                        groupService,
                        selectionController,
                        new NeuronClipboardService(neuronService, groupService),
                        state,
                        workspace,
                        neuronViews,
                        rotationHandles,
                        connectionController,
                        simulationController,
                        () -> { },
                        this::refreshPresentation,
                        ignored -> { },
                        new NeuronMenuView(
                                new Pane(),
                                state::selectedNeuronForMenu,
                                ignored -> { },
                                localization
                        ),
                        localization
                );
        private final MapPresentationCoordinator presentationCoordinator =
                new MapPresentationCoordinator(
                        neuronService,
                        workspace,
                        neuronViews,
                        connectionViews,
                        rotationHandles,
                        neuronController,
                        connectionController,
                        new NeuronLayerOrderController(
                                neuronViews,
                                connectionViews,
                                model::connections
                        )
                );
        private final MapPresentationCoordinator presentation = initializePresentationReference();

        /**
         * Публікує створений координатор, щоб колбеки під час тесту поверталися до реальної презентації.
         */
        private MapPresentationCoordinator initializePresentationReference() {
            presentationReference.set(presentationCoordinator);
            return presentationCoordinator;
        }

        /**
         * Оновлює лічильник і передає запит до координатора презентації.
         */
        private void refreshPresentation() {
            refreshCount.incrementAndGet();
            MapPresentationCoordinator coordinator = presentationReference.get();
            if (coordinator != null) {
                coordinator.refreshAll();
            }
        }

        /**
         * Завершує створені контролери й зупиняє анімації, щоб не залишати активних ресурсів між тестами.
         */
        private void dispose() {
            neuronController.dispose();
            simulationController.shutdown();
            connectionController.clearDeleteHighlights();
        }
    }
}
