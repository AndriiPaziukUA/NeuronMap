package com.example.neuronmap.view;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

/**
 * Відображає нейрон, його напрямок і візуальний стан.
 */
public final class NeuronView extends StackPane {

    public static final double WIDTH = NeuronVisualGeometry.WIDTH;
    public static final double HEIGHT = NeuronVisualGeometry.HEIGHT;
    public static final double SELECTED_STROKE_WIDTH = 2.5;

    private static final double SIGNAL_LABEL_WIDTH = 48.0;
    private static final double SIGNAL_LABEL_HEIGHT = 28.0;
    private static final double SIGNAL_LABEL_SIDE_GAP = 10.0;

    private final NeuronPresentation presentation;
    private final Rectangle body = new Rectangle(WIDTH, HEIGHT);
    private final Circle inputPort =
            new Circle(NeuronVisualGeometry.INPUT_PORT_RADIUS);
    private final Polygon outputPort = new Polygon();
    private final Label inputSignalLabel = new Label();
    private final Label outputSignalLabel = new Label();

private Integer displayedInputSignal;

    private boolean dragging;
    private boolean wasDragged;
    private boolean dragGroup;
    private double dragStartSceneX;
    private double dragStartSceneY;
    private double lastSceneX;
    private double lastSceneY;

    /**
     * Повертає результат операції «нейрон відображення».
     *
     * @param presentation значення, що визначає представлення для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronView(NeuronPresentation presentation) {
        if (presentation == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "presentation must not be null"
            );
        }

        this.presentation = presentation;

        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        setPickOnBounds(true);
        setRotate(presentation.rotationDegrees());

        body.getStyleClass().add("neuron-card");
        body.setArcWidth(28);
        body.setArcHeight(28);
        body.setMouseTransparent(true);
        body.setManaged(false);

        configureSignalLabel(inputSignalLabel);
        configureSignalLabel(outputSignalLabel);

        inputPort.getStyleClass().add("port");
        inputPort.setMouseTransparent(true);
        inputPort.setManaged(false);

        outputPort.getStyleClass().add("port");
        outputPort.setMouseTransparent(true);
        outputPort.setManaged(false);

        inputSignalLabel.setManaged(false);
        outputSignalLabel.setManaged(false);

        inputSignalLabel.setText("0");
        outputSignalLabel.setText(
                Integer.toString(model().signalWeight())
        );

        getChildren().addAll(
                body,
                inputSignalLabel,
                outputSignalLabel,
                inputPort,
                outputPort
        );

        refreshVisuals(false);
    }

    /**
     * Повертає результат операції «модель».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Neuron model() {
        return presentation.neuron();
    }

    /**
     * Повертає результат операції «представлення».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronPresentation presentation() {
        return presentation;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean wasDragged() {
        return wasDragged;
    }

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isDragging() {
        return dragging;
    }

    /**
     * Перевіряє, чи виконується умова «група».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isDraggingGroup() {
        return dragGroup;
    }

    /**
     * Запускає або планує дію, повʼязану з «перетягування».
     *
     * @param sceneX значення, що визначає сцена для цієї операції.
     *
     * @param sceneY значення, що визначає сцена для цієї операції.
     *
     * @param altPressed значення, що визначає відповідну операцію для цієї операції.
     *
     * @param belongsToGroup значення, що визначає до група для цієї операції.
     */
    public void beginDrag(
            double sceneX,
            double sceneY,
            boolean altPressed,
            boolean belongsToGroup
    ) {
        dragging = true;
        wasDragged = false;
        dragStartSceneX = sceneX;
        dragStartSceneY = sceneY;
        lastSceneX = sceneX;
        lastSceneY = sceneY;
        dragGroup = !altPressed && belongsToGroup;
    }

    /**
     * Переміщує обʼєкт «відповідну операцію» відповідно до переданого зміщення.
     *
     * @param sceneX значення, що визначає сцена для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    public double dragDeltaX(double sceneX) {
        double delta = sceneX - lastSceneX;
        lastSceneX = sceneX;
        return delta;
    }

    /**
     * Переміщує обʼєкт «відповідну операцію» відповідно до переданого зміщення.
     *
     * @param sceneY значення, що визначає сцена для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    public double dragDeltaY(double sceneY) {
        double delta = sceneY - lastSceneY;
        lastSceneY = sceneY;
        return delta;
    }

    /**
     * Задає або оновлює значення, повʼязані з «стан».
     *
     * @param sceneX значення, що визначає сцена для цієї операції.
     *
     * @param sceneY значення, що визначає сцена для цієї операції.
     */
    public void updateDraggedState(double sceneX, double sceneY) {
        if (Math.hypot(
                sceneX - dragStartSceneX,
                sceneY - dragStartSceneY
        ) > 4.0) {
            wasDragged = true;
        }
    }

    /**
     * Виконує операцію «перетягування».
     */
    public void endDrag() {
        dragging = false;
    }

/**
 * Відображає «вхід сигнал» в інтерфейсі.
 *
 * @param signal значення, що визначає сигнал для цієї операції.
 */
public void showInputSignal(int signal) {
        displayedInputSignal = signal;
        inputSignalLabel.setText(Integer.toString(signal));
    }

/**
 * Виконує операцію «приховати вхід сигнал».
 */
public void hideInputSignal() {
        displayedInputSignal = null;
        inputSignalLabel.setText("0");
    }

/**
 * Видаляє або скидає дані, повʼязані з «вхід сигнал».
 */
public void clearDisplayedInputSignal() {
        hideInputSignal();
    }

    /**
     * Перевіряє, чи виконується умова «напрямок».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isDirectionReversed() {
        return presentation.directionReversed();
    }

    /**
     * Перемикає стан «напрямок».
     */
    public void toggleDirection() {
        presentation.toggleDirection();
        positionFixedChildren();
    }

    /**
     * Обробляє «відповідну операцію».
     *
     * @param selected значення, що визначає вибраний для цієї операції.
     */
    public void refreshVisuals(boolean selected) {
        setLayoutX(presentation.x());
        setLayoutY(presentation.y());
        setRotate(presentation.rotationDegrees());

        body.setFill(baseColor());
        body.setStroke(
                selected
                        ? Color.WHITE
                        : Color.web("#25292f")
        );
        body.setStrokeWidth(
                selected
                        ? SELECTED_STROKE_WIDTH
                        : 2.0
        );

        if (displayedInputSignal == null) {
            inputSignalLabel.setText("0");
        } else {
            inputSignalLabel.setText(
                    Integer.toString(displayedInputSignal)
            );
        }

        outputSignalLabel.setText(
                Integer.toString(model().signalWeight())
        );

        positionFixedChildren();
    }

/**
 * Повертає результат операції «вихід».
 *
 * @return значення або обʼєкт, визначений описаною операцією.
 */
public Point2D outputPoint() {
        return localPointToWorld(
                new Point2D(
                        NeuronVisualGeometry.outputTipX(
                                presentation.directionReversed()
                        ),
                        HEIGHT / 2.0
                )
        );
    }

/**
 * Повертає результат операції «вхід».
 *
 * @return значення або обʼєкт, визначений описаною операцією.
 */
public Point2D inputPoint() {
        return localPointToWorld(
                new Point2D(
                        NeuronVisualGeometry.inputPortX(
                                presentation.directionReversed()
                        ),
                        HEIGHT / 2.0
                )
        );
    }

    /**
     * Виконує операцію «положення».
     */
    private void positionFixedChildren() {
        body.relocate(0.0, 0.0);

        double signalY =
                (HEIGHT - SIGNAL_LABEL_HEIGHT) / 2.0;
        double leftSignalX = SIGNAL_LABEL_SIDE_GAP;
        double rightSignalX =
                WIDTH
                        - SIGNAL_LABEL_WIDTH
                        - SIGNAL_LABEL_SIDE_GAP;

        inputSignalLabel.resize(
                SIGNAL_LABEL_WIDTH,
                SIGNAL_LABEL_HEIGHT
        );
        inputSignalLabel.relocate(
                presentation.directionReversed()
                        ? rightSignalX
                        : leftSignalX,
                signalY
        );

        outputSignalLabel.resize(
                SIGNAL_LABEL_WIDTH,
                SIGNAL_LABEL_HEIGHT
        );
        outputSignalLabel.relocate(
                presentation.directionReversed()
                        ? leftSignalX
                        : rightSignalX,
                signalY
        );

        NeuronVisualGeometry.positionInputPort(
                inputPort,
                presentation.directionReversed()
        );

        NeuronVisualGeometry.positionOutputTriangle(
                outputPort,
                presentation.directionReversed()
        );

        outputPort.setTranslateX(
                presentation.directionReversed()
                        ? -SELECTED_STROKE_WIDTH / 2.0
                        : SELECTED_STROKE_WIDTH / 2.0
        );
    }

    /**
     * Повертає результат операції «до карта».
     *
     * @param localPoint значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private Point2D localPointToWorld(Point2D localPoint) {
        double centerX = WIDTH / 2.0;
        double centerY = HEIGHT / 2.0;

        double localX = localPoint.getX() - centerX;
        double localY = localPoint.getY() - centerY;

        double angle = Math.toRadians(
                presentation.rotationDegrees()
        );
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        double rotatedX =
                localX * cos - localY * sin;
        double rotatedY =
                localX * sin + localY * cos;

        return new Point2D(
                presentation.x()
                        + centerX
                        + rotatedX,
                presentation.y()
                        + centerY
                        + rotatedY
        );
    }

    /**
     * Задає або оновлює значення, повʼязані з «сигнал підпис».
     *
     * @param label значення, що визначає підпис для цієї операції.
     */
    private void configureSignalLabel(Label label) {
        label.getStyleClass().add("neuron-activation");
        label.setAlignment(Pos.CENTER);
        label.setMouseTransparent(true);
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private Color baseColor() {
        return model().type() == NeuronType.EXCITATORY
                ? Color.web("#328b55")
                : Color.web("#646971");
    }
}
