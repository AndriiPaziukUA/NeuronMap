package com.example.neuronmap.controller;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.NeuronOverlayPositioner;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Перевіряє обчислення координат меню та накладок.
 */
final class NeuronOverlayCoordinateTest {

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
     * Перевіряє очікувану поведінку: центр накладка координати.
     */
    @Test
    void rectangleCenterIsConvertedIntoOverlayCoordinates() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture();

            Point2D actual = fixture.positioner().rectangleCenterIn(
                    fixture.neuronView(),
                    fixture.overlay()
            );

            Point2D expected = fixture.neuronView().localToScene(
                    NeuronView.WIDTH / 2.0,
                    NeuronView.HEIGHT / 2.0
            );
            expected = fixture.overlay().sceneToLocal(expected);

            assertPointEquals(expected, actual);
        });
    }

    /**
     * Перевіряє очікувану поведінку: центр карта і.
     */
    @Test
    void rectangleCenterIncludesWorldTranslationAndScale() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture();

            fixture.world().setLayoutX(40.0);
            fixture.world().setLayoutY(20.0);
            fixture.world().setScaleX(1.5);
            fixture.world().setScaleY(1.5);
            fixture.root().layout();

            Point2D actual = fixture.positioner().rectangleCenterIn(
                    fixture.neuronView(),
                    fixture.overlay()
            );

            Point2D expected = fixture.neuronView().localToScene(
                    NeuronView.WIDTH / 2.0,
                    NeuronView.HEIGHT / 2.0
            );
            expected = fixture.overlay().sceneToLocal(expected);

            assertPointEquals(expected, actual);
        });
    }

    /**
     * Перевіряє очікувану поведінку: центр залишається після обертання.
     */
    @Test
    void rectangleCenterRemainsGeometricallyCenteredAfterRotation()
            throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture();

            fixture.presentation().setRotationDegrees(90.0);
            fixture.neuronView().refreshVisuals(false);
            fixture.root().layout();

            Point2D actual = fixture.positioner().rectangleCenterIn(
                    fixture.neuronView(),
                    fixture.overlay()
            );

            Point2D expected = fixture.neuronView().localToScene(
                    NeuronView.WIDTH / 2.0,
                    NeuronView.HEIGHT / 2.0
            );
            expected = fixture.overlay().sceneToLocal(expected);

            assertPointEquals(expected, actual);
        });
    }

    /**
     * Перевіряє очікувану поведінку: меню центр.
     */
    @Test
    void menuIsCenteredOnRectangleCenter() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture();

            VBox menu = new VBox();
            menu.setPrefSize(100.0, 40.0);
            menu.setVisible(true);
            fixture.overlay().getChildren().add(menu);

            fixture.positioner().position(
                    fixture.neuronView(),
                    menu,
                    null
            );

            Point2D rectangleCenter = fixture.positioner().rectangleCenterIn(
                    fixture.neuronView(),
                    fixture.overlay()
            );

            assertEquals(
                    rectangleCenter.getX(),
                    menu.getLayoutX() + menu.getWidth() / 2.0,
                    EPSILON
            );
        });
    }

    /**
     * Перевіряє очікувану поведінку: меню центр нейрон.
     */
    @Test
    void menuHorizontalCenterTracksNeuronMovement() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = fixture();

            VBox menu = new VBox();
            menu.setPrefSize(100.0, 40.0);
            menu.setVisible(true);
            fixture.overlay().getChildren().add(menu);

            fixture.positioner().position(
                    fixture.neuronView(),
                    menu,
                    null
            );

            double before =
                    menu.getLayoutX() + menu.getWidth() / 2.0;

            fixture.presentation().moveBy(140.0, 100.0);
            fixture.neuronView().refreshVisuals(false);
            fixture.root().layout();

            fixture.positioner().position(
                    fixture.neuronView(),
                    menu,
                    null
            );

            double after =
                    menu.getLayoutX() + menu.getWidth() / 2.0;

            assertEquals(140.0, after - before, EPSILON);
        });
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static Fixture fixture() {
        NeuronPresentation presentation = new NeuronPresentation(
                new Neuron(
                        "overlay-test-neuron",
                        NeuronType.EXCITATORY,
                        0
                ),
                100.0,
                80.0,
                0.0
        );

        NeuronView neuronView = new NeuronView(presentation);
        WorkspaceView workspace = new WorkspaceView();

        Pane root = workspace.node();
        Pane world = workspace.node().getChildren().stream()
                .filter(node -> node == workspace.node().getChildren().get(1))
                .findFirst()
                .map(node -> (Pane) node)
                .orElseThrow();

        Pane overlay = workspace.overlayLayer();
        world.getChildren().add(neuronView);

        new Scene(root, 1000.0, 800.0);
        root.layout();

        return new Fixture(
                presentation,
                neuronView,
                workspace,
                world,
                overlay,
                new NeuronOverlayPositioner(workspace),
                root
        );
    }

    /**
     * Виконує операцію «відповідну операцію».
     *
     * @param expected значення, що визначає відповідну операцію для цієї операції.
     *
     * @param actual значення, що визначає відповідну операцію для цієї операції.
     */
    private static void assertPointEquals(
            Point2D expected,
            Point2D actual
    ) {
        assertEquals(expected.getX(), actual.getX(), EPSILON);
        assertEquals(expected.getY(), actual.getY(), EPSILON);
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
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param world значення, що визначає карта для цієї операції.
     *
     * @param overlay значення, що визначає накладка для цієї операції.
     *
     * @param positioner значення, що визначає відповідну операцію для цієї операції.
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
            WorkspaceView workspace,
            Pane world,
            Pane overlay,
            NeuronOverlayPositioner positioner,
            Pane root
    ) {
    }
}
