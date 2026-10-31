package com.example.neuronmap.view.connection;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.NeuronVisualGeometry;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Перевіряє стиль зв'язку, завершення стрижня й наконечника біля порта та кінцеву точку імпульсу. */
final class ConnectionViewStyleTest {

    private static final double EPSILON = 0.001;

    /** Перевіряє передавання користувацького стилю лінії, зони вибору та знімку імпульсу. */
    @Test
    void appliesInjectedStyleToLineHitTargetAndPulseSnapshot() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Neuron source = new Neuron("style-source", NeuronType.EXCITATORY, 0);
            Neuron target = new Neuron("style-target", NeuronType.INHIBITORY, 0);
            NeuronView sourceView = new NeuronView(new NeuronPresentation(source, 100.0, 100.0, 0.0));
            NeuronView targetView = new NeuronView(new NeuronPresentation(target, 360.0, 100.0, 0.0));
            Map<String, NeuronView> views = Map.of(source.id(), sourceView, target.id(), targetView);
            Map<String, Neuron> neurons = Map.of(source.id(), source, target.id(), target);
            Connection connection = new Connection("style-connection", source.id(), target.id());
            ConnectionStyle style = new ConnectionStyle(
                    Color.BLUE, Color.RED, Color.GRAY, Color.LIME, Color.PINK, Color.YELLOW,
                    4.0, 22.0, 15.0, 7.0);

            ConnectionView view = new ConnectionView(connection, views::get, neurons::get, style);
            Line visibleLine = (Line) view.getChildren().get(0);
            Line hitLine = (Line) view.getChildren().get(1);
            assertEquals(Color.BLUE, visibleLine.getStroke());
            assertEquals(4.0, visibleLine.getStrokeWidth(), EPSILON);
            assertEquals(22.0, hitLine.getStrokeWidth(), EPSILON);
            ConnectionPulseSnapshot snapshot = view.capturePulseSnapshot();
            assertNotNull(snapshot);
            assertEquals(Color.LIME, snapshot.pulseColor());
            assertEquals(Color.BLUE, snapshot.normalColor());
            sourceView.disposeParameterTooltip();
            targetView.disposeParameterTooltip();
        });
    }

    /** Перевіряє помаранчеві лінію й наконечник та блакитний сигнал структурного джерела. */
    @Test
    void usesOrangeStructureConnectionAndBlueSignal() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Neuron source = new Neuron("structure-source", NeuronType.STRUCTURE, 0);
            Neuron target = new Neuron("structure-target", NeuronType.EXCITATORY, 0);
            NeuronView sourceView = new NeuronView(new NeuronPresentation(source, 100.0, 100.0, 0.0));
            NeuronView targetView = new NeuronView(new NeuronPresentation(target, 360.0, 100.0, 0.0));
            Map<String, NeuronView> views = Map.of(source.id(), sourceView, target.id(), targetView);
            Map<String, Neuron> neurons = Map.of(source.id(), source, target.id(), target);
            Connection connection = new Connection("structure-connection", source.id(), target.id());

            ConnectionView view = new ConnectionView(connection, views::get, neurons::get);
            Line visibleLine = (Line) view.getChildren().get(0);
            Polygon arrow = (Polygon) view.getChildren().get(2);
            ConnectionPulseSnapshot snapshot = view.capturePulseSnapshot();

            assertEquals(Color.web("#f59e0b"), visibleLine.getStroke());
            assertEquals(Color.web("#f59e0b"), arrow.getFill());
            assertNotNull(snapshot);
            assertEquals(Color.web("#38bdf8"), snapshot.pulseColor());
            assertEquals(Color.web("#f59e0b"), snapshot.normalColor());
            sourceView.disposeParameterTooltip();
            targetView.disposeParameterTooltip();
        });
    }

    /**
     * Перевіряє індивідуальне розташування вістря на найближчій точці краю порта для різних напрямків підходу.
     */
    @Test
    void connectionShaftArrowAndPulseEndAtPortBoundaryForDifferentApproachDirections() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            assertConnectionEndsAtPortBoundary(100.0, 360.0, 100.0);
            assertConnectionEndsAtPortBoundary(500.0, 180.0, 100.0);
            assertConnectionEndsAtPortBoundary(100.0, 360.0, 220.0);
        });
    }

    /** Перевіряє безпечну поведінку, коли відстань між портами менша за потрібний відступ до порта. */
    @Test
    void omitsArrowAndPulseWhenThereIsNotEnoughRoomBeforeInputPort() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Neuron source = new Neuron("short-source", NeuronType.EXCITATORY, 0);
            Neuron target = new Neuron("short-target", NeuronType.INHIBITORY, 0);
            NeuronView sourceView = new NeuronView(new NeuronPresentation(source, 100.0, 100.0, 0.0));
            NeuronView targetView = new NeuronView(new NeuronPresentation(target, 250.0, 100.0, 0.0));
            Map<String, NeuronView> views = Map.of(source.id(), sourceView, target.id(), targetView);
            Map<String, Neuron> neurons = Map.of(source.id(), source, target.id(), target);
            ConnectionView view = new ConnectionView(
                    new Connection("short-connection", source.id(), target.id()), views::get, neurons::get);

            Line line = (Line) view.getChildren().get(0);
            Polygon arrow = (Polygon) view.getChildren().get(2);

            assertEquals(line.getStartX(), line.getEndX(), EPSILON);
            assertEquals(line.getStartY(), line.getEndY(), EPSILON);
            assertEquals(0, arrow.getPoints().size());
            assertNull(view.capturePulseSnapshot());

            sourceView.disposeParameterTooltip();
            targetView.disposeParameterTooltip();
        });
    }

    /**
     * Перевіряє завершення стрижня на основі трикутника, а вістря та імпульсу — перед краєм вхідного порта.
     *
     * @param sourceX горизонтальна позиція джерела.
     * @param targetX горизонтальна позиція цільового нейрона.
     * @param targetY вертикальна позиція цільового нейрона; джерело залишається на базовій горизонталі.
     */
    private static void assertConnectionEndsAtPortBoundary(
            double sourceX,
            double targetX,
            double targetY
    ) {
        Neuron source = new Neuron("arrow-source", NeuronType.EXCITATORY, 0);
        Neuron target = new Neuron("arrow-target", NeuronType.INHIBITORY, 0);
        NeuronView sourceView = new NeuronView(
                new NeuronPresentation(source, sourceX, 100.0, 0.0));
        NeuronView targetView = new NeuronView(
                new NeuronPresentation(target, targetX, targetY, 0.0));
        Map<String, NeuronView> views = Map.of(source.id(), sourceView, target.id(), targetView);
        Map<String, Neuron> neurons = Map.of(source.id(), source, target.id(), target);
        ConnectionView view = new ConnectionView(
                new Connection("arrow-connection", source.id(), target.id()), views::get, neurons::get);

        Line line = (Line) view.getChildren().get(0);
        Line hitLine = (Line) view.getChildren().get(1);
        Polygon arrow = (Polygon) view.getChildren().get(2);
        Point2D start = sourceView.outputPoint();
        Point2D portCenter = targetView.inputPoint();
        Point2D unitDirection = portCenter.subtract(start).normalize();
        double clearance = NeuronVisualGeometry.INPUT_PORT_OUTER_RADIUS + 0.5;
        Point2D expectedTip = portCenter.subtract(unitDirection.multiply(clearance));
        Point2D expectedBase = expectedTip.subtract(
                unitDirection.multiply(ConnectionStyle.DEFAULT.arrowSize()));
        Point2D actualTip = new Point2D(arrow.getPoints().get(0), arrow.getPoints().get(1));
        Point2D actualLeftBase = new Point2D(arrow.getPoints().get(2), arrow.getPoints().get(3));
        Point2D actualRightBase = new Point2D(arrow.getPoints().get(4), arrow.getPoints().get(5));
        Point2D actualBaseCenter = actualLeftBase.midpoint(actualRightBase);
        ConnectionPulseSnapshot snapshot = view.capturePulseSnapshot();

        assertEquals(expectedBase.getX(), line.getEndX(), EPSILON);
        assertEquals(expectedBase.getY(), line.getEndY(), EPSILON);
        assertEquals(expectedTip.getX(), hitLine.getEndX(), EPSILON);
        assertEquals(expectedTip.getY(), hitLine.getEndY(), EPSILON);
        assertEquals(expectedTip.getX(), actualTip.getX(), EPSILON);
        assertEquals(expectedTip.getY(), actualTip.getY(), EPSILON);
        assertEquals(expectedBase.getX(), actualBaseCenter.getX(), EPSILON);
        assertEquals(expectedBase.getY(), actualBaseCenter.getY(), EPSILON);
        assertEquals(clearance, actualTip.distance(portCenter), EPSILON);
        assertNotNull(snapshot);
        assertEquals(expectedTip, snapshot.end());

        sourceView.disposeParameterTooltip();
        targetView.disposeParameterTooltip();
    }

}
