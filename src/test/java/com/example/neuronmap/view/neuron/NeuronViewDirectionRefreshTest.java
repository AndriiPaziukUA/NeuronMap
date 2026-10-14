package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import javafx.application.Platform;
import javafx.scene.shape.Circle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє, що напрямок нейрона змінює сервіс, а подання лише відображає його стан.
 */
final class NeuronViewDirectionRefreshTest {

    private static final double EPSILON = 0.0001;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "JavaFX startup timed out"
            );
        }
    }

    @Test
    void viewDoesNotDeclareDirectionMutationMethod() {
        assertFalse(
                Arrays.stream(NeuronView.class.getDeclaredMethods())
                        .map(Method::getName)
                        .anyMatch("toggleDirection"::equals)
        );
    }

    @Test
    void refreshVisualsReflectsDirectionChangedThroughService()
            throws Exception {
        runOnFxThread(() -> {
            NeuronMapModel model = new NeuronMapModel();
            NeuronService service = new NeuronService(model);
            Neuron neuron = service.create(
                    NeuronType.EXCITATORY,
                    100.0,
                    80.0
            );
            NeuronPresentation presentation =
                    service.presentation(neuron.id());
            NeuronView view = new NeuronView(presentation);
            Circle inputPort = (Circle) view.getChildren().get(3);

            assertFalse(presentation.directionReversed());
            assertEquals(0.0, inputPort.getCenterX(), EPSILON);

            assertTrue(service.toggleDirection(neuron.id()));
            assertTrue(presentation.directionReversed());
            assertEquals(0.0, inputPort.getCenterX(), EPSILON);

            view.refreshVisuals(false);

            assertTrue(view.isDirectionReversed());
            assertEquals(NeuronView.WIDTH, inputPort.getCenterX(), EPSILON);
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

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "JavaFX test timed out"
            );
        }

        if (failure[0] != null) {
            throw new AssertionError(
                    "JavaFX test failed",
                    failure[0]
            );
        }
    }
}
