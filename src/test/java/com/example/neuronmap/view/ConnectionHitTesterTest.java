package com.example.neuronmap.view;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Перевіряє пошук видимого подання зв’язку за точкою сцени.
 */
final class ConnectionHitTesterTest {

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

    @Test
    void findsVisibleConnectionAtSceneCoordinates() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture();
            Point2D scenePoint = fixture.scenePointOnConnection();

            assertSame(
                    fixture.connectionView(),
                    fixture.hitTester().findConnectionViewAt(
                            scenePoint.getX(),
                            scenePoint.getY()
                    )
            );
        });
    }

    @Test
    void ignoresConnectionWhenItsViewIsNotVisible() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture();
            Point2D scenePoint = fixture.scenePointOnConnection();
            fixture.connectionView().setVisible(false);

            assertNull(
                    fixture.hitTester().findConnectionViewAt(
                            scenePoint.getX(),
                            scenePoint.getY()
                    )
            );
        });
    }

    @Test
    void returnsNullWhenPointDoesNotIntersectVisibleConnection() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture();

            assertNull(
                    fixture.hitTester().findConnectionViewAt(
                            15.0,
                            550.0
                    )
            );
        });
    }

    /**
     * Створює сцену з одним зв’язком для перевірки геометричного пошуку.
     *
     * @return підготовлені компоненти тестової сцени.
     */
    private static Fixture fixture() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.addNeuron(
                "hit-test-source",
                NeuronType.EXCITATORY,
                0,
                100.0,
                100.0,
                0.0
        );
        Neuron target = model.addNeuron(
                "hit-test-target",
                NeuronType.INHIBITORY,
                0,
                430.0,
                100.0,
                0.0
        );

        WorkspaceView workspace = new WorkspaceView();
        Map<String, NeuronView> neuronViews = new HashMap<>();
        NeuronView sourceView = new NeuronView(model.presentation(source.id()));
        NeuronView targetView = new NeuronView(model.presentation(target.id()));
        neuronViews.put(source.id(), sourceView);
        neuronViews.put(target.id(), targetView);
        workspace.nodeLayer().getChildren().addAll(sourceView, targetView);

        Connection connection = new Connection(
                "hit-test-connection",
                source.id(),
                target.id()
        );
        ConnectionView connectionView = new ConnectionView(
                connection,
                neuronViews::get,
                model::neuron
        );
        workspace.edgeLayer().getChildren().add(connectionView);

        Scene scene = new Scene(workspace.node(), 900.0, 650.0);
        workspace.node().applyCss();
        workspace.node().layout();
        connectionView.updateGeometry();

        Point2D start = sourceView.outputPoint();
        Point2D end = targetView.inputPoint();
        Point2D midpointInWorld = start.midpoint(end);
        Point2D midpointInScene = workspace.edgeLayer().localToScene(midpointInWorld);

        return new Fixture(
                new ConnectionHitTester(workspace),
                connectionView,
                midpointInScene,
                scene
        );
    }

    /**
     * Виконує дію в потоці JavaFX і передає помилку тестовому потоку.
     *
     * @param action дія, яку потрібно виконати.
     * @throws Exception якщо очікування потоку JavaFX перервано.
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

    private record Fixture(
            ConnectionHitTester hitTester,
            ConnectionView connectionView,
            Point2D scenePointOnConnection,
            Scene scene
    ) {
    }
}
