package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє векторний значок, розміщення та поведінку ручки обертання нейрона. */
final class RotationHandleViewTest {

    private static final double EPSILON = 0.0001;

    /** Запускає JavaFX перед тестами, які створюють і перевіряють графічні вузли. */
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

    /** Перевіряє векторний значок і рівні відступи кругової дуги від усіх країв ручки. */
    @Test
    void handleUsesCenteredVectorArcWithSymmetricMargins() throws Exception {
        runOnFxThread(() -> {
            RotationHandleView handle = new RotationHandleView();
            assertTrue(handle.getText().isBlank(), "Значок не повинен залишатися текстовим символом");
            StackPane graphic = assertInstanceOf(StackPane.class, handle.getGraphic());
            Arc arc = assertInstanceOf(Arc.class, graphic.getChildren().get(1));
            Path arrowHead = assertInstanceOf(Path.class, graphic.getChildren().get(2));
            assertTrue(arc.getStyleClass().contains("rotation-handle-glyph"));
            assertTrue(arrowHead.getStyleClass().contains("rotation-handle-glyph"));
            assertFalse(arc.isManaged(), "Компонування не повинно зміщувати симетричну кругову дугу");
            assertFalse(arrowHead.isManaged(), "Компонування не повинно зміщувати вістря стрілки");

            double center = RotationHandleView.SIZE / 2.0;
            double leftMargin = arc.getCenterX() - arc.getRadiusX();
            double rightMargin = RotationHandleView.SIZE - arc.getCenterX() - arc.getRadiusX();
            double topMargin = arc.getCenterY() - arc.getRadiusY();
            double bottomMargin = RotationHandleView.SIZE - arc.getCenterY() - arc.getRadiusY();

            assertEquals(center, arc.getCenterX(), EPSILON);
            assertEquals(center, arc.getCenterY(), EPSILON);
            assertEquals(arc.getRadiusX(), arc.getRadiusY(), EPSILON);
            assertEquals(leftMargin, rightMargin, EPSILON,
                    "Кругова дуга має бути симетричною відносно лівого й правого країв");
            assertEquals(topMargin, bottomMargin, EPSILON,
                    "Кругова дуга має бути симетричною відносно верхнього й нижнього країв");
            assertEquals(leftMargin, topMargin, EPSILON,
                    "Проміжок від кругової дуги до країв має бути однаковим з усіх боків");

            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            handle.bindLocalizedTooltip(localization);
            assertEquals("Rotate", handle.getAccessibleText(),
                    "Текстова назва для допоміжних технологій має бути доступна окремо від значка");
            handle.dispose();
        });
    }

    /** Перевіряє початкове центрування ручки під нейроном і точний проміжок від його нижнього краю. */
    @Test
    void handleStartsCenteredBelowNeuronWithExactGap() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture(0.0);
            position(fixture);

            Point2D neuronBottom = fixture.neuronView().localToScene(
                    NeuronView.WIDTH / 2.0,
                    NeuronView.HEIGHT
            );
            Point2D handleCenter = handleCenterInScene(fixture.handle());

            assertEquals(neuronBottom.getX(), handleCenter.getX(), EPSILON);
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

    /** Переконується, що ручка переміщується разом із нейроном, зберігаючи відносне положення. */
    @Test
    void handleFollowsNeuronAfterMove() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture(0.0);
            position(fixture);

            Point2D before = handleCenterInScene(fixture.handle());
            Point2D neuronBefore = neuronCenterInScene(fixture.neuronView());

            fixture.presentation().moveBy(140.0, 100.0);
            fixture.neuronView().refreshVisuals(false);
            fixture.root().layout();
            position(fixture);

            Point2D after = handleCenterInScene(fixture.handle());
            Point2D neuronAfter = neuronCenterInScene(fixture.neuronView());

            assertEquals(140.0, after.getX() - before.getX(), EPSILON);
            assertEquals(100.0, after.getY() - before.getY(), EPSILON);
            assertEquals(before.getX() - neuronBefore.getX(), after.getX() - neuronAfter.getX(), EPSILON);
            assertEquals(before.getY() - neuronBefore.getY(), after.getY() - neuronAfter.getY(), EPSILON);
        });
    }

    /** Перевіряє орбітальний рух ручки при обертанні нейрона зі сталим проміжком до його краю. */
    @Test
    void handleOrbitsAroundNeuronWhileKeepingExactEdgeGap() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture(0.0);
            Point2D center = neuronCenterInScene(fixture.neuronView());

            double edgeGap = RotationHandleView.GAP
                    + RotationHandleView.SIZE / 2.0
                    + NeuronView.SELECTED_STROKE_WIDTH / 2.0;

            for (double degrees : new double[]{
                    0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0
            }) {
                fixture.presentation().setRotationDegrees(degrees);
                fixture.neuronView().refreshVisuals(false);
                fixture.root().layout();
                position(fixture);

                Point2D actual = handleCenterInScene(fixture.handle());
                double angle = Math.toRadians(degrees);
                double expectedDistance = NeuronView.HEIGHT / 2.0 + edgeGap;
                Point2D expected = new Point2D(
                        center.getX() - expectedDistance * Math.sin(angle),
                        center.getY() + expectedDistance * Math.cos(angle)
                );

                assertEquals(expected.getX(), actual.getX(), EPSILON);
                assertEquals(expected.getY(), actual.getY(), EPSILON);
            }
        });
    }

    /** Перевіряє, що проміжок від ручки до нейрона лишається сталим у екранних одиницях за різного масштабу. */
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
                Point2D handleCenter = handleCenterInScene(fixture.handle());

                double dx = normalSample.getX() - neuronEdge.getX();
                double dy = normalSample.getY() - neuronEdge.getY();
                double normalLength = Math.hypot(dx, dy);
                double screenUnitsPerLocalUnit = normalLength;
                double selectionStrokeReserve = NeuronView.SELECTED_STROKE_WIDTH / 2.0
                        * screenUnitsPerLocalUnit;
                double expectedCenterGap = RotationHandleView.GAP
                        + RotationHandleView.SIZE / 2.0
                        + selectionStrokeReserve;
                double actualGap = ((handleCenter.getX() - neuronEdge.getX()) * dx
                        + (handleCenter.getY() - neuronEdge.getY()) * dy) / normalLength;

                assertEquals(expectedCenterGap, actualGap, EPSILON);
                double gapOutsideSelectionStroke = actualGap - selectionStrokeReserve
                        - RotationHandleView.SIZE / 2.0;
                assertEquals(RotationHandleView.GAP, gapOutsideSelectionStroke, EPSILON);

                fixture.handle().dispose();
            }
        });
    }

    /** Переконується, що область попадання ручки має заданий розмір і не масштабується разом зі світом. */
    @Test
    void handleHitAreaStaysExactly22By22() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture(135.0);

            fixture.world().setScaleX(2.5);
            fixture.world().setScaleY(2.5);
            fixture.neuronView().refreshVisuals(false);
            fixture.root().layout();
            position(fixture);

            assertEquals(RotationHandleView.SIZE, fixture.handle().getPrefWidth(), EPSILON);
            assertEquals(RotationHandleView.SIZE, fixture.handle().getPrefHeight(), EPSILON);
            assertEquals(RotationHandleView.SIZE, fixture.handle().getBoundsInLocal().getWidth(), EPSILON);
            assertEquals(RotationHandleView.SIZE, fixture.handle().getBoundsInLocal().getHeight(), EPSILON);
        });
    }

    /** Розміщує ручку в батьківському шарі відповідно до актуальної геометрії нейрона. */
    private static void position(Fixture fixture) {
        NeuronOverlayPositioner.positionRotationHandleInParent(
                fixture.neuronView(),
                fixture.handle(),
                fixture.overlay()
        );
    }

    /** Створює тестову сцену з нейроном, ручкою обертання та окремим шаром накладок. */
    private static Fixture fixture(double rotationDegrees) {
        NeuronPresentation presentation = new NeuronPresentation(
                new Neuron("rotation-test-neuron", NeuronType.EXCITATORY, 0),
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

        return new Fixture(presentation, neuronView, handle, world, overlay, root);
    }

    /** Повертає геометричний центр нейрона в системі координат сцени. */
    private static Point2D neuronCenterInScene(NeuronView neuronView) {
        return neuronView.localToScene(
                NeuronView.WIDTH / 2.0,
                NeuronView.HEIGHT / 2.0
        );
    }

    /** Повертає геометричний центр ручки в системі координат сцени. */
    private static Point2D handleCenterInScene(RotationHandleView handle) {
        return handle.localToScene(
                RotationHandleView.SIZE / 2.0,
                RotationHandleView.SIZE / 2.0
        );
    }

    /** Виконує дію в потоці JavaFX і передає помилки до потоку JUnit. */
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
            NeuronPresentation presentation,
            NeuronView neuronView,
            RotationHandleView handle,
            Pane world,
            Pane overlay,
            Pane root
    ) {
    }
}
