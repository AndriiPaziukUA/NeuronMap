package com.example.neuronmap.controller;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.NeuronLayerOrderManager;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.simulation.PulseAnimationView;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє життєвий цикл імпульсів, зміну їхньої швидкості та тривалість окремої анімації. */
class PulseAnimationControllerTest {

    /** Запускає JavaFX перед тестами, що працюють із графічними вузлами. */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Перевіряє, що імпульс переживає видалення нейрона й повертається з актуальним прогресом після відновлення зв'язку.
     *
     * @throws Exception якщо JavaFX не завершує асинхронне відновлення в межах тестового очікування.
     */
    @Test
    void activePulseContinuesFromCurrentProgressWhenConnectionViewIsRestored() throws Exception {
        CountDownLatch restored = new CountDownLatch(1);
        AtomicReference<Throwable> restorationFailure = new AtomicReference<>();
        AtomicReference<WorkspaceView> workspaceReference = new AtomicReference<>();
        AtomicReference<PulseAnimationController> controllerReference = new AtomicReference<>();
        AtomicReference<PulseAnimationView> pulseReference = new AtomicReference<>();
        AtomicReference<ConnectionView> originalConnectionViewReference = new AtomicReference<>();
        AtomicReference<ConnectionView> restoredConnectionViewReference = new AtomicReference<>();
        AtomicReference<Double> tipPositionBeforeRemoval = new AtomicReference<>();

        try {
            runOnFxThread(() -> {
                NeuronMapModel model = new NeuronMapModel();
                Neuron source = model.createNeuron(NeuronType.EXCITATORY, 50.0, 50.0);
                Neuron target = model.createNeuron(NeuronType.EXCITATORY, 250.0, 50.0);
                assertTrue(model.createConnection(source.id(), target.id()));

                WorkspaceView workspace = new WorkspaceView();
                new Scene(workspace.node(), 800.0, 600.0);
                HashMap<String, NeuronView> views = new HashMap<>();
                NeuronView sourceView = new NeuronView(model.presentation(source.id()));
                NeuronView targetView = new NeuronView(model.presentation(target.id()));
                views.put(source.id(), sourceView);
                views.put(target.id(), targetView);
                workspace.nodeLayer().getChildren().addAll(sourceView, targetView);

                Connection connection = model.connections().iterator().next();
                ConnectionView connectionView = new ConnectionView(connection, views::get, model::neuron);
                workspace.edgeLayer().getChildren().add(connectionView);
                PulseAnimationController controller = new PulseAnimationController(workspace);
                controller.setTickDurationMillis(1_800.0);
                controller.play(connectionView);

                PulseAnimationView activePulse = (PulseAnimationView) workspace.pulseLayer().getChildren().get(0);
                Polygon arrow = (Polygon) activePulse.getChildren().get(0);
                workspaceReference.set(workspace);
                controllerReference.set(controller);
                pulseReference.set(activePulse);
                originalConnectionViewReference.set(connectionView);
                tipPositionBeforeRemoval.set(arrow.getPoints().get(0));

                NeuronLayerOrderManager layerOrderManager = new NeuronLayerOrderManager(
                        views,
                        Map.of(connection.id(), connectionView),
                        model::connections);
                layerOrderManager.bringNeuronToFront(source.id());
                assertFalse(activePulse.isFinished());
                assertEquals(workspace.edgeLayer(), connectionView.getParent());
                assertEquals(workspace.nodeLayer(), sourceView.getParent());

                assertNotNull(model.removeNeuron(source.id()));
                assertTrue(model.connections().isEmpty());
                assertFalse(model.neurons().stream().anyMatch(neuron -> neuron.id().equals(source.id())));

                // Імітує вилучення подань після видалення нейрона: активний імпульс має залишитися прихованим.
                workspace.edgeLayer().getChildren().remove(connectionView);
                workspace.nodeLayer().getChildren().remove(sourceView);
                views.remove(source.id());
                assertFalse(workspace.edgeLayer().getChildren().contains(connectionView));
                assertFalse(workspace.nodeLayer().getChildren().contains(sourceView));
                assertFalse(activePulse.isFinished());
                assertFalse(activePulse.isVisible());
                assertEquals(1, controller.activeAnimationCountForTest());
                assertEquals(1, workspace.pulseLayer().getChildren().size());

                PauseTransition restoreDelay = new PauseTransition(Duration.millis(180.0));
                restoreDelay.setOnFinished(event -> {
                    try {
                        Neuron restoredSource = model.addNeuron(
                                source.id(),
                                source.type(),
                                source.activation(),
                                50.0,
                                50.0,
                                0.0
                        );
                        restoredSource.setParameters(source.parameters());
                        model.addConnection(connection.id(), source.id(), target.id());

                        NeuronView restoredSourceView = new NeuronView(model.presentation(source.id()));
                        views.put(source.id(), restoredSourceView);
                        workspace.nodeLayer().getChildren().add(restoredSourceView);

                        Connection restoredConnection = model.connections().stream()
                                .filter(candidate -> candidate.id().equals(connection.id()))
                                .findFirst()
                                .orElseThrow();
                        ConnectionView restoredView = new ConnectionView(
                                restoredConnection,
                                views::get,
                                model::neuron
                        );
                        restoredConnectionViewReference.set(restoredView);
                        workspace.edgeLayer().getChildren().add(restoredView);
                    } catch (Throwable throwable) {
                        restorationFailure.set(throwable);
                    } finally {
                        restored.countDown();
                    }
                });
                restoreDelay.play();
            });

            assertTrue(restored.await(5, TimeUnit.SECONDS), "Подання зв'язку має відновитися");
            if (restorationFailure.get() != null) {
                throw new AssertionError("Не вдалося відновити тестову модель", restorationFailure.get());
            }

            runOnFxThread(() -> {
                WorkspaceView workspace = workspaceReference.get();
                PulseAnimationController controller = controllerReference.get();
                PulseAnimationView activePulse = pulseReference.get();
                ConnectionView restoredConnectionView = restoredConnectionViewReference.get();
                Polygon arrow = (Polygon) activePulse.getChildren().get(0);

                assertNotNull(restoredConnectionView);
                assertNotSame(originalConnectionViewReference.get(), restoredConnectionView);
                assertEquals(workspace.edgeLayer(), restoredConnectionView.getParent());
                assertFalse(activePulse.isFinished());
                assertTrue(activePulse.isVisible());
                assertTrue(workspace.pulseLayer().getChildren().contains(activePulse));
                assertEquals(1, controller.activeAnimationCountForTest());
                assertEquals(1, workspace.pulseLayer().getChildren().size(),
                        "Відновлення не повинно створювати другий імпульс");

                double tipPositionAfterRestoration = arrow.getPoints().get(0);
                assertTrue(
                        tipPositionAfterRestoration > tipPositionBeforeRemoval.get() + 1.0,
                        "Трикутник має продовжити рух, а не повертатися на початок траєкторії"
                );
            });
        } finally {
            runOnFxThread(() -> {
                PulseAnimationController controller = controllerReference.get();
                WorkspaceView workspace = workspaceReference.get();
                if (controller != null) {
                    controller.stopAll();
                }
                if (workspace != null) {
                    workspace.dispose();
                }
            });
        }
    }

    /** Перевіряє, що активний зв'язок не отримує дубльований імпульс, а наступний запуск зберігає типову тривалість. */
    @Test
    void activeConnectionRejectsDuplicatePulseAndKeepsDefaultDurationForNextPulse() throws Exception {
        runOnFxThread(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron source = model.createNeuron(NeuronType.EXCITATORY, 50.0, 50.0);
            Neuron target = model.createNeuron(NeuronType.EXCITATORY, 250.0, 50.0);
            assertTrue(model.createConnection(source.id(), target.id()));
            WorkspaceView workspace = new WorkspaceView();
            new Scene(workspace.node(), 800.0, 600.0);
            Map<String, NeuronView> views = Map.of(
                    source.id(), new NeuronView(model.presentation(source.id())),
                    target.id(), new NeuronView(model.presentation(target.id()))
            );
            workspace.nodeLayer().getChildren().addAll(views.values());
            Connection connection = model.connections().iterator().next();
            ConnectionView connectionView = new ConnectionView(connection, views::get, model::neuron);
            workspace.edgeLayer().getChildren().add(connectionView);
            PulseAnimationController controller = new PulseAnimationController(workspace);
            try {
                controller.setTickDurationMillis(500.0);
                controller.play(connectionView, 175.0);

                PulseAnimationView shortPulse = (PulseAnimationView) workspace.pulseLayer().getChildren().get(0);
                assertEquals(175.0, shortPulse.getTickDurationMillis());

                controller.play(connectionView);
                assertEquals(1, controller.activeAnimationCountForTest());
                assertEquals(1, workspace.pulseLayer().getChildren().size());
                assertTrue(workspace.pulseLayer().getChildren().contains(shortPulse));

                shortPulse.stop();
                controller.play(connectionView);
                assertEquals(1, controller.activeAnimationCountForTest());
                assertEquals(1, workspace.pulseLayer().getChildren().size());
                PulseAnimationView regularPulse = (PulseAnimationView) workspace.pulseLayer().getChildren().get(0);
                assertEquals(500.0, regularPulse.getTickDurationMillis());
            } finally {
                controller.stopAll();
                workspace.dispose();
            }
        });
    }

    /** Перевіряє, що межа такту завершує старий імпульс і звільняє зв'язок для наступного сигналу. */
    @Test
    void tickBoundaryCompletesPulseBeforeNextPulseOnSameConnection() throws Exception {
        runOnFxThread(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron source = model.createNeuron(NeuronType.EXCITATORY, 50.0, 50.0);
            Neuron target = model.createNeuron(NeuronType.EXCITATORY, 250.0, 50.0);
            assertTrue(model.createConnection(source.id(), target.id()));

            WorkspaceView workspace = new WorkspaceView();
            new Scene(workspace.node(), 800.0, 600.0);
            Map<String, NeuronView> views = Map.of(
                    source.id(), new NeuronView(model.presentation(source.id())),
                    target.id(), new NeuronView(model.presentation(target.id()))
            );
            workspace.nodeLayer().getChildren().addAll(views.values());
            Connection connection = model.connections().iterator().next();
            ConnectionView connectionView = new ConnectionView(connection, views::get, model::neuron);
            workspace.edgeLayer().getChildren().add(connectionView);
            PulseAnimationController controller = new PulseAnimationController(workspace);

            try {
                controller.play(connectionView, 175.0);
                PulseAnimationView firstPulse = (PulseAnimationView) workspace.pulseLayer().getChildren().get(0);
                controller.play(connectionView);
                assertEquals(1, controller.activeAnimationCountForTest());

                controller.completeAtTickBoundary();

                assertTrue(firstPulse.isFinished());
                assertEquals(0, controller.activeAnimationCountForTest());
                assertTrue(workspace.pulseLayer().getChildren().isEmpty());

                controller.play(connectionView);

                assertEquals(1, controller.activeAnimationCountForTest());
                assertEquals(1, workspace.pulseLayer().getChildren().size());
                PulseAnimationView nextPulse = (PulseAnimationView) workspace.pulseLayer().getChildren().get(0);
                assertTrue(nextPulse != firstPulse);
                assertEquals(PulseAnimationView.DEFAULT_TICK_DURATION_MILLIS, nextPulse.getTickDurationMillis());
            } finally {
                controller.stopAll();
                workspace.dispose();
            }
        });
    }

    /** Виконує дію в JavaFX-потоці та передає помилки до потоку JUnit. */
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

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
