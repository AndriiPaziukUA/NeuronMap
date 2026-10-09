package com.example.neuronmap.view;

import com.example.neuronmap.model.NeuronType;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

/**
 * Відображає попереднє зображення нейрона під час перетягування.
 */
public final class NeuronDragPreviewView extends Pane {

    private static final double WIDTH = NeuronVisualGeometry.WIDTH;
    private static final double HEIGHT = NeuronVisualGeometry.HEIGHT;

    private static final double LABEL_OFFSET_Y = 6.0;
    private static final double INPUT_LABEL_OFFSET_X = -24.0;
    private static final double OUTPUT_LABEL_OFFSET_X = WIDTH + 8.0;

    private final Rectangle body = new Rectangle(WIDTH, HEIGHT);
    private final Circle inputPort = new Circle(NeuronVisualGeometry.INPUT_PORT_RADIUS);
    private final Polygon outputPort = new Polygon();
    private final Label inputSignalLabel = new Label("1");
    private final Label outputSignalLabel = new Label("1");

    /**
     * Повертає результат операції «нейрон перетягування попередній перегляд відображення».
     *
     * @param type тип обʼєкта.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronDragPreviewView(NeuronType type) {
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

        getChildren().addAll(
                body,
                inputPort,
                outputPort,
                inputSignalLabel,
                outputSignalLabel
        );

        positionChildren();
    }

    /**
     * Задає або оновлює значення, повʼязані з «відповідну операцію».
     *
     * @param type тип обʼєкта.
     */
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

    /**
     * Задає або оновлює значення, повʼязані з «вхід».
     */
    private void configureInputPort() {
        inputPort.setFill(Color.WHITE);
        inputPort.setStroke(Color.BLACK);
        inputPort.setStrokeWidth(1.2);
        inputPort.setManaged(false);
        inputPort.setMouseTransparent(true);
    }

    /**
     * Задає або оновлює значення, повʼязані з «вихід».
     */
    private void configureOutputPort() {
        outputPort.setFill(Color.WHITE);
        outputPort.setStroke(Color.BLACK);
        outputPort.setStrokeWidth(1.2);
        outputPort.setManaged(false);
        outputPort.setMouseTransparent(true);
        NeuronVisualGeometry.positionOutputTriangle(outputPort, false);
    }

    /**
     * Задає або оновлює значення, повʼязані з «відповідну операцію».
     */
    private void configureLabels() {
        inputSignalLabel.setTextFill(Color.BLACK);
        inputSignalLabel.setMouseTransparent(true);
        inputSignalLabel.setManaged(false);

        outputSignalLabel.setTextFill(Color.BLACK);
        outputSignalLabel.setMouseTransparent(true);
        outputSignalLabel.setManaged(false);
    }

    /**
     * Виконує операцію «положення».
     */
    private void positionChildren() {
        NeuronVisualGeometry.positionInputPort(inputPort, false);
        NeuronVisualGeometry.positionOutputTriangle(outputPort, false);

        inputSignalLabel.relocate(INPUT_LABEL_OFFSET_X, LABEL_OFFSET_Y);
        outputSignalLabel.relocate(OUTPUT_LABEL_OFFSET_X, LABEL_OFFSET_Y);
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param type тип обʼєкта.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static Color bodyFill(NeuronType type) {
        return type == NeuronType.EXCITATORY
                ? Color.LIGHTGREEN
                : Color.LIGHTGRAY;
    }
}
