package com.example.neuronmap.view.connection;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.NeuronVisualGeometry;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє стиль, геометрію та життєвий цикл тимчасового графічного прев’ю зв’язку.
 */
final class ConnectionPreviewViewTest {

    /** Запускає JavaFX toolkit перед тестами графічного подання. */
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

    /** Перевіряє стиль лінії та її розміщення на шарі накладок від виходу джерела до курсора. */
    @Test
    void showCreatesStyledMouseTransparentLineAtSourceAndCursor() throws Exception {
        runOnFxThread(() -> {
            PreviewFixture fixture = createFixture();
            ConnectionPreviewView preview = new ConnectionPreviewView(fixture.workspace());
            Point2D cursor = new Point2D(310.0, 245.0);

            preview.show(fixture.source(), cursor);

            assertEquals(1, fixture.workspace().overlayLayer().getChildren().size());
            Line line = (Line) fixture.workspace().overlayLayer().getChildren().getFirst();
            assertEquals(Color.web("#c5ccd6"), line.getStroke());
            assertEquals(2.2, line.getStrokeWidth(), 0.001);
            assertEquals(java.util.List.of(9.0, 7.0), line.getStrokeDashArray());
            assertTrue(line.isMouseTransparent());
            assertLineStartsAtSource(line, fixture.workspace(), fixture.source());
            assertEquals(cursor.getX(), line.getEndX(), 0.001);
            assertEquals(cursor.getY(), line.getEndY(), 0.001);

            preview.hide();
        });
    }

    /** Перевіряє, що оновлення враховує переміщення джерела, напрямок нейрона та зміну камери. */
    @Test
    void updateRecalculatesGeometryAfterSourceAndCameraChanges() throws Exception {
        runOnFxThread(() -> {
            PreviewFixture fixture = createFixture();
            ConnectionPreviewView preview = new ConnectionPreviewView(fixture.workspace());
            preview.show(fixture.source(), new Point2D(200.0, 180.0));
            Line line = (Line) fixture.workspace().overlayLayer().getChildren().getFirst();

            fixture.workspace().setWorldTransform(1.75, 42.0, 28.0);
            fixture.source().presentation().setPosition(220.0, 150.0);
            fixture.source().presentation().setDirectionReversed(true);
            fixture.source().refreshVisuals(false);
            Point2D updatedCursor = new Point2D(415.0, 335.0);
            preview.update(fixture.source(), updatedCursor);

            assertLineStartsAtSource(line, fixture.workspace(), fixture.source());
            assertEquals(updatedCursor.getX(), line.getEndX(), 0.001);
            assertEquals(updatedCursor.getY(), line.getEndY(), 0.001);
            preview.hide();
        });
    }

    /** Перевіряє ідемпотентність показу й приховування та не відновлює приховану лінію під час оновлення. */
    @Test
    void repeatedShowAndHideDoNotDuplicateOrLeakPreviewLine() throws Exception {
        runOnFxThread(() -> {
            PreviewFixture fixture = createFixture();
            ConnectionPreviewView preview = new ConnectionPreviewView(fixture.workspace());
            Point2D firstCursor = new Point2D(150.0, 160.0);
            preview.show(fixture.source(), firstCursor);
            Line firstLine = (Line) fixture.workspace().overlayLayer().getChildren().getFirst();

            preview.show(fixture.source(), new Point2D(260.0, 270.0));
            assertEquals(1, fixture.workspace().overlayLayer().getChildren().size());
            assertSame(firstLine, fixture.workspace().overlayLayer().getChildren().getFirst());
            assertEquals(260.0, firstLine.getEndX(), 0.001);
            assertEquals(270.0, firstLine.getEndY(), 0.001);

            preview.hide();
            preview.hide();
            assertTrue(fixture.workspace().overlayLayer().getChildren().isEmpty());
            preview.update(fixture.source(), new Point2D(350.0, 360.0));
            assertTrue(fixture.workspace().overlayLayer().getChildren().isEmpty());

            preview.show(fixture.source(), firstCursor);
            Line secondLine = (Line) fixture.workspace().overlayLayer().getChildren().getFirst();
            assertNotSame(firstLine, secondLine);
            assertEquals(1, fixture.workspace().overlayLayer().getChildren().size());
            preview.hide();
            assertFalse(fixture.workspace().overlayLayer().getChildren().contains(secondLine));
        });
    }

    /**
     * Створює полотно зі сценним нейроном, щоб перевіряти реальні перетворення координат JavaFX.
     *
     * @return підготовлене полотно, подання нейрона та сцену.
     */
    private static PreviewFixture createFixture() {
        WorkspaceView workspace = new WorkspaceView();
        Neuron sourceModel = new Neuron("preview-source", NeuronType.EXCITATORY, 0);
        NeuronView source = new NeuronView(
                new NeuronPresentation(sourceModel, 100.0, 80.0, 0.0)
        );
        workspace.nodeLayer().getChildren().add(source);
        Scene scene = new Scene(workspace.node(), 800.0, 600.0);
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        return new PreviewFixture(workspace, source, scene);
    }

    /**
     * Перевіряє, що початок лінії відповідає вихідному порту нейрона в координатах накладки.
     *
     * @param line графічна лінія прев’ю.
     * @param workspace полотно редактора.
     * @param source нейрон-джерело.
     */
    private static void assertLineStartsAtSource(
            Line line,
            WorkspaceView workspace,
            NeuronView source
    ) {
        Point2D expectedScenePoint = source.localToScene(
                NeuronVisualGeometry.outputTipX(source.isDirectionReversed()),
                NeuronView.HEIGHT / 2.0
        );
        Point2D expectedPoint = workspace.node().sceneToLocal(expectedScenePoint);
        assertEquals(expectedPoint.getX(), line.getStartX(), 0.001);
        assertEquals(expectedPoint.getY(), line.getStartY(), 0.001);
    }

    /** Виконує тестову дію в потоці JavaFX і передає помилку до потоку JUnit. */
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
            throw new AssertionError("Помилка тесту подання JavaFX", failure[0]);
        }
    }

    /** Зберігає вузли тестового полотна, потрібні для перевірки координат у сцені. */
    private record PreviewFixture(WorkspaceView workspace, NeuronView source, Scene scene) {
    }
}
