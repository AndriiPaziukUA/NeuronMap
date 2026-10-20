package com.example.neuronmap.view.neuron;

import com.example.neuronmap.controller.PulseAnimationController;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє геометрію вихідного трикутника та кольори під час передавання сигналів. */
final class NeuronOutputTriangleAlignmentTest {

    private static final double EPSILON = 0.0001;
    private static final Color NEUTRAL_PORT_COLOR = Color.web("#d6d9de");
    private static final Color EXCITATORY_SIGNAL_COLOR = Color.web("#facc15");
    private static final Color INHIBITORY_SIGNAL_COLOR = Color.web("#ef4444");

    /** Запускає JavaFX перед тестами, що працюють із графічними вузлами. */
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

    /** Перевіряє центр трикутника відносно зовнішнього краю виділеного штриха за звичайного напрямку нейрона. */
    @Test
    void outputTriangleIsCenteredOnSelectedStrokeForNormalDirection() throws Exception {
        runOnFxThread(() -> {
            NeuronView view = neuronView(false);
            Polygon triangle = outputTriangle(view);

            assertEquals(
                    NeuronView.WIDTH + NeuronView.SELECTED_STROKE_WIDTH / 2.0,
                    triangleCenterX(triangle),
                    EPSILON
            );
        });
    }

    /** Перевіряє, що трикутник вирівнюється відносно протилежного краю штриха після розвороту напрямку. */
    @Test
    void outputTriangleIsCenteredOnSelectedStrokeForReversedDirection() throws Exception {
        runOnFxThread(() -> {
            NeuronView view = neuronView(true);
            Polygon triangle = outputTriangle(view);

            assertEquals(
                    -NeuronView.SELECTED_STROKE_WIDTH / 2.0,
                    triangleCenterX(triangle),
                    EPSILON
            );
        });
    }

    /**
     * Перевіряє, що під час такту трикутник збуджувального нейрона жовтий, гальмівного — червоний,
     * а після такту або очищення runtime повертається нейтральний колір.
     *
     * @throws Exception якщо JavaFX-потік не виконає перевірку вчасно.
     */
    @Test
    void outputTriangleColorFollowsNeuronTypeDuringSignalTransmission() throws Exception {
        runOnFxThread(() -> {
            WorkspaceView workspace = new WorkspaceView();
            NeuronView excitatoryView = new NeuronView(new NeuronPresentation(
                    new Neuron("output-color-excitatory", NeuronType.EXCITATORY, 0),
                    40.0,
                    50.0,
                    0.0
            ));
            NeuronView inhibitoryView = new NeuronView(new NeuronPresentation(
                    new Neuron("output-color-inhibitory", NeuronType.INHIBITORY, 0),
                    300.0,
                    50.0,
                    0.0
            ));
            HBox root = new HBox(excitatoryView, inhibitoryView, workspace.node());
            Scene scene = new Scene(root, 900.0, 300.0);
            scene.getStylesheets().add(
                    NeuronOutputTriangleAlignmentTest.class.getResource("/app.css").toExternalForm());
            root.applyCss();

            PulseAnimationController pulseAnimations = new PulseAnimationController(workspace);
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace,
                    Map.of(
                            excitatoryView.model().id(), excitatoryView,
                            inhibitoryView.model().id(), inhibitoryView
                    ),
                    () -> {
                        excitatoryView.refreshVisuals(false);
                        inhibitoryView.refreshVisuals(false);
                    },
                    pulseAnimations
            );
            Polygon excitatoryTriangle = outputTriangle(excitatoryView);
            Polygon inhibitoryTriangle = outputTriangle(inhibitoryView);

            try {
                presenter.apply(new SimulationStep(
                        BigInteger.ZERO,
                        Map.of(),
                        Set.of(excitatoryView.model().id(), inhibitoryView.model().id()),
                        Map.of()
                ));
                root.applyCss();

                assertEquals(EXCITATORY_SIGNAL_COLOR, excitatoryTriangle.getFill());
                assertEquals(INHIBITORY_SIGNAL_COLOR, inhibitoryTriangle.getFill());

                presenter.apply(new SimulationStep(
                        BigInteger.ONE,
                        Map.of(),
                        Set.of(),
                        Map.of()
                ));
                root.applyCss();

                assertEquals(NEUTRAL_PORT_COLOR, excitatoryTriangle.getFill());
                assertEquals(NEUTRAL_PORT_COLOR, inhibitoryTriangle.getFill());

                presenter.apply(new SimulationStep(
                        BigInteger.TWO,
                        Map.of(),
                        Set.of(excitatoryView.model().id(), inhibitoryView.model().id()),
                        Map.of()
                ));
                root.applyCss();
                presenter.clearRuntime();
                root.applyCss();

                assertEquals(NEUTRAL_PORT_COLOR, excitatoryTriangle.getFill());
                assertEquals(NEUTRAL_PORT_COLOR, inhibitoryTriangle.getFill());
            } finally {
                presenter.clearRuntime();
                workspace.dispose();
            }
        });
    }

    /** Створює подання нейрона з указаним напрямком і розміщує його в тестовій сцені. */
    private static NeuronView neuronView(boolean reversed) {
        Neuron neuron = new Neuron("triangle-alignment-test", NeuronType.EXCITATORY, 0);
        NeuronPresentation presentation = new NeuronPresentation(neuron, 100.0, 80.0, 0.0);

        if (reversed) {
            presentation.toggleDirection();
        }

        NeuronView view = new NeuronView(presentation);
        new Scene(view, 400.0, 300.0);
        view.layout();
        return view;
    }

    /** Знаходить графічний трикутник вихідного порту серед дочірніх вузлів подання нейрона. */
    private static Polygon outputTriangle(NeuronView view) {
        for (Node child : view.getChildrenUnmodifiable()) {
            if (child instanceof Polygon polygon) {
                return polygon;
            }
        }
        throw new AssertionError("Output triangle was not found");
    }

    /** Обчислює горизонтальну координату геометричного центра трикутника з урахуванням його зміщення. */
    private static double triangleCenterX(Polygon triangle) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < triangle.getPoints().size(); i += 2) {
            double x = triangle.getPoints().get(i);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
        }

        return (minX + maxX) / 2.0 + triangle.getTranslateX();
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

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }

        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
