package com.example.neuronmap.controller;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.application.Platform;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє керування активними анімаціями імпульсів, включно з їхнім завершенням і очищенням.
 */
class PulseAnimationControllerTest {

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

    @Test
    void pulseSurvivesRemovalOfOriginalConnectionView() throws Exception {
        runOnFxThread(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron source = model.createNeuron(
                    NeuronType.EXCITATORY,
                    50,
                    50
            );
            Neuron target = model.createNeuron(
                    NeuronType.EXCITATORY,
                    250,
                    50
            );
            assertTrue(model.createConnection(source.id(), target.id()));

            WorkspaceView workspace = new WorkspaceView();
            new Scene(workspace.node(), 800, 600);

            HashMap<String, NeuronView> views = new HashMap<>();
            NeuronView sourceView =
                    new NeuronView(model.presentation(source.id()));
            NeuronView targetView =
                    new NeuronView(model.presentation(target.id()));
            views.put(source.id(), sourceView);
            views.put(target.id(), targetView);
            workspace.nodeLayer().getChildren().addAll(
                    sourceView,
                    targetView
            );

            Connection connection = model.connections().iterator().next();
            ConnectionView connectionView = new ConnectionView(
                    connection,
                    views::get,
                    model::neuron
            );
            workspace.edgeLayer().getChildren().add(connectionView);

            PulseAnimationController controller =
                    new PulseAnimationController(workspace);
            controller.play(connectionView);

            assertEquals(1, controller.activeAnimationCountForTest());
            workspace.edgeLayer().getChildren().remove(connectionView);

            assertEquals(
                    1,
                    controller.activeAnimationCountForTest()
            );
            assertEquals(
                    1,
                    workspace.pulseLayer().getChildren().size()
            );

            controller.stopAll();
            assertEquals(0, workspace.pulseLayer().getChildren().size());
        });
    }

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
