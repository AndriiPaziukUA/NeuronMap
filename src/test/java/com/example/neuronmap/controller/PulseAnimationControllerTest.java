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
import javafx.application.Platform;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє життєвий цикл активних імпульсів і зміну їхньої швидкості разом із тактом. */
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

    /** Перевіряє, що активний імпульс зберігається при зміні порядку шарів і зупиняється після видалення зв’язку. */
    @Test
    void activePulseSurvivesNeuronBringToFrontAndStopsWhenConnectionViewIsRemoved() throws Exception {
        runOnFxThread(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron source = model.createNeuron(NeuronType.EXCITATORY, 50, 50);
            Neuron target = model.createNeuron(NeuronType.EXCITATORY, 250, 50);
            assertTrue(model.createConnection(source.id(), target.id()));

            WorkspaceView workspace = new WorkspaceView();
            new Scene(workspace.node(), 800, 600);
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
            try {
                controller.play(connectionView);
                assertEquals(1, controller.activeAnimationCountForTest());
                assertEquals(1, workspace.pulseLayer().getChildren().size());
                PulseAnimationView activePulse = (PulseAnimationView) workspace.pulseLayer().getChildren().get(0);
                assertEquals(PulseAnimationView.DEFAULT_TICK_DURATION_MILLIS, activePulse.getTickDurationMillis());

                controller.setTickDurationMillis(75.0);
                assertEquals(75.0, activePulse.getTickDurationMillis());

                NeuronLayerOrderManager layerOrderManager = new NeuronLayerOrderManager(
                        views,
                        Map.of(connection.id(), connectionView),
                        model::connections);
                layerOrderManager.bringNeuronToFront(source.id());

                assertFalse(activePulse.isFinished());
                assertEquals(1, controller.activeAnimationCountForTest());
                assertEquals(1, workspace.pulseLayer().getChildren().size());
                assertEquals(workspace.edgeLayer(), connectionView.getParent());
                assertEquals(workspace.nodeLayer(), sourceView.getParent());

                assertNotNull(model.removeNeuron(source.id()));
                assertTrue(model.connections().isEmpty());
                assertFalse(model.neurons().stream().anyMatch(neuron -> neuron.id().equals(source.id())));

                // Імітує синхронізацію подань, яка відбувається відразу після зміни моделі.
                workspace.edgeLayer().getChildren().remove(connectionView);
                workspace.nodeLayer().getChildren().remove(sourceView);
                views.remove(source.id());

                assertFalse(workspace.edgeLayer().getChildren().contains(connectionView));
                assertFalse(workspace.nodeLayer().getChildren().contains(sourceView));
                assertTrue(activePulse.isFinished());
                assertEquals(0, controller.activeAnimationCountForTest());
                assertEquals(0, workspace.pulseLayer().getChildren().size());
            } finally {
                controller.stopAll();
            }
            assertEquals(0, workspace.pulseLayer().getChildren().size());
        });
    }

    /** Виконує дію в JavaFX-потоці та передає помилки виконання до потоку JUnit. */
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
