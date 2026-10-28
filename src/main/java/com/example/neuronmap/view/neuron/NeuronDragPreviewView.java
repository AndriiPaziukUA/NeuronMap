package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.NeuronType;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

import java.util.Objects;

/** Відображає напівпрозорий попередній вигляд нейрона під час розміщення інструмента на карті. */
public final class NeuronDragPreviewView extends Pane {

    private static final double WIDTH = NeuronVisualGeometry.WIDTH;
    private static final double HEIGHT = NeuronVisualGeometry.HEIGHT;
    private static final double LABEL_OFFSET_Y = 6.0;
    private static final double INPUT_LABEL_OFFSET_X = -24.0;
    private static final double OUTPUT_LABEL_OFFSET_X = WIDTH + 8.0;
    private static final Color STRUCTURE_COLOR = Color.web("#f59e0b");

    private final Rectangle body = new Rectangle(WIDTH, HEIGHT);
    private final Circle inputPort = new Circle(NeuronVisualGeometry.INPUT_PORT_RADIUS);
    private final Polygon outputPort = new Polygon();
    private final Label inputSignalLabel = new Label("1");
    private final Label outputSignalLabel = new Label("1");

    /**
     * Створює напівпрозоре подання вказаного типу нейрона.
     *
     * @param type тип нейрона, вигляд якого потрібно попередньо показати.
     * @throws NullPointerException якщо тип не задано.
     */
    public NeuronDragPreviewView(NeuronType type) {
        Objects.requireNonNull(type, "type");
        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        setOpacity(0.45);
        setManaged(false);
        setPickOnBounds(false);
        setMouseTransparent(true);

        configureBody(type);
        configureInputPort();
        configureOutputPort();
        configureLabels();
        getChildren().addAll(body, inputPort, outputPort, inputSignalLabel, outputSignalLabel);
        positionChildren();
    }

    /** Налаштовує тіло попереднього перегляду відповідно до типу нейрона. */
    private void configureBody(NeuronType type) {
        body.setX(0);
        body.setY(0);
        body.setWidth(WIDTH);
        body.setHeight(HEIGHT);
        body.setArcWidth(18);
        body.setArcHeight(18);
        body.setFill(bodyFill(type));
        body.setStroke(Color.DARKGRAY);
        body.setStrokeWidth(1.2);
        body.setMouseTransparent(true);
    }

    /** Налаштовує вхідний порт попереднього перегляду. */
    private void configureInputPort() {
        inputPort.setFill(Color.WHITE);
        inputPort.setStroke(Color.BLACK);
        inputPort.setStrokeWidth(1.2);
        inputPort.setManaged(false);
        inputPort.setMouseTransparent(true);
    }

    /** Налаштовує вихідний трикутник попереднього перегляду. */
    private void configureOutputPort() {
        outputPort.setFill(Color.WHITE);
        outputPort.setStroke(Color.BLACK);
        outputPort.setStrokeWidth(1.2);
        outputPort.setManaged(false);
        outputPort.setMouseTransparent(true);
        NeuronVisualGeometry.positionOutputTriangle(outputPort, false);
    }

    /** Налаштовує текстові позначення сигналів попереднього перегляду. */
    private void configureLabels() {
        inputSignalLabel.setTextFill(Color.BLACK);
        inputSignalLabel.setMouseTransparent(true);
        inputSignalLabel.setManaged(false);
        outputSignalLabel.setTextFill(Color.BLACK);
        outputSignalLabel.setMouseTransparent(true);
        outputSignalLabel.setManaged(false);
    }

    /** Розташовує всі дочірні графічні елементи у координатах тіла нейрона. */
    private void positionChildren() {
        NeuronVisualGeometry.positionInputPort(inputPort, false);
        NeuronVisualGeometry.positionOutputTriangle(outputPort, false);
        inputSignalLabel.relocate(INPUT_LABEL_OFFSET_X, LABEL_OFFSET_Y);
        outputSignalLabel.relocate(OUTPUT_LABEL_OFFSET_X, LABEL_OFFSET_Y);
    }

    /**
     * Повертає колір тіла попереднього перегляду для заданого типу.
     *
     * @param type тип нейрона.
     * @return колір заливки тіла.
     */
    private static Color bodyFill(NeuronType type) {
        return switch (type) {
            case EXCITATORY -> Color.LIGHTGREEN;
            case INHIBITORY -> Color.LIGHTGRAY;
            case STRUCTURE -> STRUCTURE_COLOR;
        };
    }
}
