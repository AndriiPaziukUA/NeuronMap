package com.example.neuronmap.controller;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.simulation.PulseAnimationView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.shape.Polygon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє синхронне оновлення траєкторії активного імпульсу через події геометрії зв'язку. */
class PulseAnimationGeometrySynchronizationTest {

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

    /** Перевіряє, що зміна геометрії переміщує той самий імпульс без другого циклу анімації. */
    @Test
    void pausedPulseTracksConnectionGeometryChangeWithoutRestart() throws Exception {
        runOnFxThread(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron source = model.createNeuron(NeuronType.EXCITATORY, 50.0, 50.0);
            Neuron target = model.createNeuron(NeuronType.EXCITATORY, 250.0, 50.0);
            assertTrue(model.createConnection(source.id(), target.id()));

            WorkspaceView workspace = new WorkspaceView();
            new Scene(workspace.node(), 800.0, 600.0);
            Map<String, NeuronView> neuronViews = new HashMap<>();
            NeuronView sourceView = new NeuronView(model.presentation(source.id()));
            NeuronView targetView = new NeuronView(model.presentation(target.id()));
            neuronViews.put(source.id(), sourceView);
            neuronViews.put(target.id(), targetView);
            workspace.nodeLayer().getChildren().addAll(sourceView, targetView);

            Connection connection = model.connections().iterator().next();
            ConnectionView connectionView = new ConnectionView(connection, neuronViews::get, model::neuron);
            workspace.edgeLayer().getChildren().add(connectionView);
            PulseAnimationController controller = new PulseAnimationController(workspace);
            try {
                controller.play(connectionView);
                controller.pauseAll();

                PulseAnimationView pulse = (PulseAnimationView) workspace.pulseLayer().getChildren().getFirst();
                Polygon arrow = (Polygon) pulse.getChildren().getFirst();
                double previousTipY = arrow.getPoints().get(1);

                model.presentation(target.id()).setPosition(250.0, 250.0);
                targetView.refreshVisuals(false);
                connectionView.updateGeometry();

                double updatedTipY = arrow.getPoints().get(1);
                assertNotEquals(previousTipY, updatedTipY,
                        "Активний імпульс має оновити позицію одразу після зміни геометрії зв'язку");
                assertSame(pulse, workspace.pulseLayer().getChildren().getFirst(),
                        "Зміна геометрії не повинна створювати новий графічний імпульс");
                assertFalse(pulse.isFinished());
                assertEquals(1, controller.activeAnimationCountForTest());
            } finally {
                controller.stopAll();
            }
            assertEquals(0, workspace.pulseLayer().getChildren().size());
        });
    }

    /** Виконує перевірку в JavaFX-потоці та передає помилки до потоку JUnit. */
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
