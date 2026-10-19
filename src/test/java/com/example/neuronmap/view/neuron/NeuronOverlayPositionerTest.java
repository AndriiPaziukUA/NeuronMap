package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє розташування накладок на точній відстані від нейрона, у тому числі за обернутого напрямку та нульового вектора.
 */
class NeuronOverlayPositionerTest {

    /**
     * Перевіряє, що зазор між нейроном і ручкою визначається єдиною геометричною константою ручки.
     *
     * @throws Exception якщо перевірка не виконається в потоці JavaFX.
     */
    @Test
    void rotationHandleUsesCanonicalGapConstant() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronPresentation presentation = new NeuronPresentation(
                    new Neuron("rotation-gap-test", NeuronType.EXCITATORY, 0),
                    30.0,
                    40.0,
                    0.0
            );
            NeuronView neuronView = new NeuronView(presentation);
            RotationHandleView handle = new RotationHandleView();
            Pane world = new Pane();
            Pane overlay = new Pane();
            Pane root = new Pane(world, overlay);

            world.getChildren().add(neuronView);
            overlay.getChildren().add(handle);
            new Scene(root, 800.0, 600.0);
            root.layout();

            NeuronOverlayPositioner.positionRotationHandleInParent(
                    neuronView,
                    handle,
                    overlay
            );

            Point2D neuronBottom = neuronView.localToScene(
                    NeuronView.WIDTH / 2.0,
                    NeuronView.HEIGHT
            );
            Point2D handleCenter = handle.localToScene(
                    RotationHandleView.SIZE / 2.0,
                    RotationHandleView.SIZE / 2.0
            );
            double actualGap = neuronBottom.distance(handleCenter)
                    - RotationHandleView.SIZE / 2.0
                    - NeuronView.SELECTED_STROKE_WIDTH / 2.0;

            assertEquals(neuronBottom.getX(), handleCenter.getX(), 1e-6);
            assertEquals(RotationHandleView.GAP, actualGap, 1e-6);
        });
    }

    /**
     * Перевіряє, що зміщення вздовж нормалі зберігає вказану екранну відстань.
     */
    @Test
    void offsetAlongNormalKeepsExactScreenDistance() {
        Point2D anchor = new Point2D(100.0, 200.0);
        Point2D direction = new Point2D(100.0, 203.0);

        Point2D result = NeuronOverlayPositioner.offsetAlongNormal(
                anchor,
                direction,
                19.0
        );

        assertEquals(100.0, result.getX(), 1e-9);
        assertEquals(219.0, result.getY(), 1e-9);
        assertEquals(
                19.0,
                anchor.distance(result),
                1e-9
        );
    }

    /**
     * Перевіряє, що зміщення виконується уздовж нормалі після повороту.
     */
    @Test
    void offsetAlongNormalFollowsRotatedNormal() {
        Point2D anchor = new Point2D(100.0, 200.0);
        Point2D direction = new Point2D(97.0, 200.0);

        Point2D result = NeuronOverlayPositioner.offsetAlongNormal(
                anchor,
                direction,
                19.0
        );

        assertEquals(81.0, result.getX(), 1e-9);
        assertEquals(200.0, result.getY(), 1e-9);
    }

    /**
     * Перевіряє відхилення нульового вектора нормалі.
     */
    @Test
    void zeroNormalIsRejected() {
        Point2D anchor = new Point2D(100.0, 200.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> NeuronOverlayPositioner.offsetAlongNormal(
                        anchor,
                        anchor,
                        19.0
                )
        );
    }
}
