package com.example.neuronmap.view;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Перевіряє положення та поведінку ручки обертання.
 */
final class RotationHandleViewTest {

    private static final double EPSILON = 0.0001;

    /**
     * Запускає або планує дію, повʼязану з «відповідну операцію».
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
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException(
                    "JavaFX startup timed out"
            );
        }
    }

    /**
     * Перевіряє очікувану поведінку: обробити запускає нейрон із.
     */
    @Test
    void handleStartsCenteredBelowNeuronWithExactGap() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture(0.0);
            position(fixture);

            Point2D neuronBottom = fixture.neuronView().localToScene(
                    NeuronView.WIDTH / 2.0,
                    NeuronView.HEIGHT
            );
            Point2D handleCenter = handleCenterInScene(
                    fixture.handle()
            );

            assertEquals(
                    neuronBottom.getX(),
                    handleCenter.getX(),
                    EPSILON
            );
            assertEquals(
                    RotationHandleView.GAP
                            + RotationHandleView.SIZE / 2.0
                            + NeuronView.SELECTED_STROKE_WIDTH / 2.0,
                    neuronBottom.distance(handleCenter),
                    EPSILON
            );

            fixture.handle().dispose();
        });
    }

    /**
     * Перевіряє очікувану поведінку: обробити слідує нейрон після перемістити.
     */
    @Test
    void handleFollowsNeuronAfterMove() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture(0.0);
            position(fixture);

            Point2D before = handleCenterInScene(fixture.handle());
            Point2D neuronBefore = neuronCenterInScene(
                    fixture.neuronView()
            );

            fixture.presentation().moveBy(140.0, 100.0);
            fixture.neuronView().refreshVisuals(false);
            fixture.root().layout();
            position(fixture);

            Point2D after = handleCenterInScene(fixture.handle());
            Point2D neuronAfter = neuronCenterInScene(
                    fixture.neuronView()
            );

            assertEquals(
                    140.0,
                    after.getX() - before.getX(),
                    EPSILON
            );
            assertEquals(
                    100.0,
                    after.getY() - before.getY(),
                    EPSILON
            );
            assertEquals(
                    before.getX() - neuronBefore.getX(),
                    after.getX() - neuronAfter.getX(),
                    EPSILON
            );
            assertEquals(
                    before.getY() - neuronBefore.getY(),
                    after.getY() - neuronAfter.getY(),
                    EPSILON
            );
        });
    }

    /**
     * Перевіряє очікувану поведінку: обробити нейрон коли.
     */
    @Test
    void handleOrbitsAroundNeuronWhileKeepingExactEdgeGap()
            throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture(0.0);
            Point2D center = neuronCenterInScene(
                    fixture.neuronView()
            );

            double edgeGap =
                    RotationHandleView.GAP
                            + RotationHandleView.SIZE / 2.0
                            + NeuronView.SELECTED_STROKE_WIDTH / 2.0;

            for (double degrees : new double[]{
                    0.0,
                    45.0,
                    90.0,
                    135.0,
                    180.0,
                    225.0,
                    270.0,
                    315.0
            }) {
                fixture.presentation().setRotationDegrees(degrees);
                fixture.neuronView().refreshVisuals(false);
                fixture.root().layout();
                position(fixture);

                Point2D actual = handleCenterInScene(
                        fixture.handle()
                );

                double angle = Math.toRadians(degrees);
                double expectedDistance =
                        NeuronView.HEIGHT / 2.0 + edgeGap;

                // JavaFX positive rotation is clockwise in screen coordinates.
                // Starting from the rectangle bottom edge, 90 degrees therefore
                // moves the handle to the left of the neuron center.
                Point2D expected = new Point2D(
                        center.getX()
                                - expectedDistance * Math.sin(angle),
                        center.getY()
                                + expectedDistance * Math.cos(angle)
                );

                assertEquals(
                        expected.getX(),
                        actual.getX(),
                        EPSILON
                );
                assertEquals(
                        expected.getY(),
                        actual.getY(),
                        EPSILON
                );
            }
        });
    }

    /**
     * Перевіряє очікувану поведінку: обробити зберігає карта масштаб.
     */
    @Test
    void handleKeepsScreenGapThroughWorldZoom() throws Exception {
        runOnFxThread(() -> {
            for (double zoom : new double[]{0.5, 1.0, 1.75, 2.0, 3.0}) {
                Fixture fixture = fixture(135.0);

                fixture.world().setScaleX(zoom);
                fixture.world().setScaleY(zoom);
                fixture.presentation().setRotationDegrees(135.0);
                fixture.neuronView().refreshVisuals(false);
                fixture.root().layout();
                position(fixture);

                Point2D neuronEdge = fixture.neuronView().localToScene(
                        NeuronView.WIDTH / 2.0,
                        NeuronView.HEIGHT
                );
                Point2D normalSample = fixture.neuronView().localToScene(
                        NeuronView.WIDTH / 2.0,
                        NeuronView.HEIGHT + 1.0
                );
                Point2D handleCenter = handleCenterInScene(
                        fixture.handle()
                );

                double dx =
                        normalSample.getX() - neuronEdge.getX();
                double dy =
                        normalSample.getY() - neuronEdge.getY();
                double normalLength = Math.hypot(dx, dy);

                double screenUnitsPerLocalUnit =
                        normalLength;
                double selectionStrokeReserve =
                        NeuronView.SELECTED_STROKE_WIDTH / 2.0
                                * screenUnitsPerLocalUnit;

                double expectedCenterGap =
                        RotationHandleView.GAP
                                + RotationHandleView.SIZE / 2.0
                                + selectionStrokeReserve;

                double actualGap =
                        ((handleCenter.getX() - neuronEdge.getX()) * dx
                                + (handleCenter.getY() - neuronEdge.getY()) * dy)
                                / normalLength;

                assertEquals(
                        expectedCenterGap,
                        actualGap,
                        EPSILON
                );

                double gapOutsideSelectionStroke =
                        actualGap - selectionStrokeReserve
                                - RotationHandleView.SIZE / 2.0;

                assertEquals(
                        RotationHandleView.GAP,
                        gapOutsideSelectionStroke,
                        EPSILON
                );

                fixture.handle().dispose();
            }
        });
    }

    /**
     * Перевіряє очікувану поведінку: обробити за.
     */
    @Test
    void handleHitAreaStaysExactly22By22() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture(135.0);

            fixture.world().setScaleX(2.5);
            fixture.world().setScaleY(2.5);
            fixture.neuronView().refreshVisuals(false);
            fixture.root().layout();
            position(fixture);

            assertEquals(
                    RotationHandleView.SIZE,
                    fixture.handle().getPrefWidth(),
                    EPSILON
            );
            assertEquals(
                    RotationHandleView.SIZE,
                    fixture.handle().getPrefHeight(),
                    EPSILON
            );
            assertEquals(
                    RotationHandleView.SIZE,
                    fixture.handle().getBoundsInLocal().getWidth(),
                    EPSILON
            );
            assertEquals(
                    RotationHandleView.SIZE,
                    fixture.handle().getBoundsInLocal().getHeight(),
                    EPSILON
            );
        });
    }

    /**
     * Виконує операцію «положення».
     *
     * @param fixture значення, що визначає відповідну операцію для цієї операції.
     */
    private static void position(Fixture fixture) {
        NeuronOverlayPositioner.positionRotationHandleInParent(
                fixture.neuronView(),
                fixture.handle(),
                fixture.overlay()
        );
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param rotationDegrees значення, що визначає обертання градуси для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static Fixture fixture(double rotationDegrees) {
        NeuronPresentation presentation = new NeuronPresentation(
                new Neuron(
                        "rotation-test-neuron",
                        NeuronType.EXCITATORY,
                        0
                ),
                100.0,
                80.0,
                rotationDegrees
        );

        NeuronView neuronView = new NeuronView(presentation);
        RotationHandleView handle = new RotationHandleView();

        Pane world = new Pane();
        Pane overlay = new Pane();
        Pane root = new Pane(world, overlay);

        world.getChildren().add(neuronView);
        overlay.getChildren().add(handle);
        new Scene(root, 1000.0, 800.0);

        root.layout();

        /**
         * Повертає результат операції «відповідну операцію».
         *
         * @param presentation значення, що визначає представлення для цієї операції.
         *
         * @param neuronView значення, що визначає нейрон відображення для цієї операції.
         *
         * @param handle значення, що визначає обробити для цієї операції.
         *
         * @param world значення, що визначає карта для цієї операції.
         *
         * @param overlay значення, що визначає накладка для цієї операції.
         *
         * @param root кореневий каталог.
         *
         * @return значення або обʼєкт, визначений описаною операцією.
         */
        return new Fixture(
                presentation,
                neuronView,
                handle,
                world,
                overlay,
                root
        );
    }

    /**
     * Повертає результат операції «нейрон центр сцена».
     *
     * @param neuronView значення, що визначає нейрон відображення для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static Point2D neuronCenterInScene(NeuronView neuronView) {
        return neuronView.localToScene(
                NeuronView.WIDTH / 2.0,
                NeuronView.HEIGHT / 2.0
        );
    }

    /**
     * Обробляє «центр сцена».
     *
     * @param handle значення, що визначає обробити для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static Point2D handleCenterInScene(
            RotationHandleView handle
    ) {
        return handle.localToScene(
                RotationHandleView.SIZE / 2.0,
                RotationHandleView.SIZE / 2.0
        );
    }

    /**
     * Запускає або планує дію, повʼязану з «відповідну операцію».
     *
     * @param action дія, яку потрібно виконати.
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
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException(
                    "JavaFX test timed out"
            );
        }

        if (failure[0] != null) {
            /**
             * Повертає результат операції «відповідну операцію».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new AssertionError(
                    "JavaFX test failed",
                    failure[0]
            );
        }
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param presentation значення, що визначає представлення для цієї операції.
     *
     * @param neuronView значення, що визначає нейрон відображення для цієї операції.
     *
     * @param handle значення, що визначає обробити для цієї операції.
     *
     * @param world значення, що визначає карта для цієї операції.
     *
     * @param overlay значення, що визначає накладка для цієї операції.
     *
     * @param root кореневий каталог.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    /**
     * Набір модульних тестів типу Fixture. Перевіряє його основну поведінку та обробку некоректних або крайових даних.
     */
    private record Fixture(
            NeuronPresentation presentation,
            NeuronView neuronView,
            RotationHandleView handle,
            Pane world,
            Pane overlay,
            Pane root
    ) {
    }
}
