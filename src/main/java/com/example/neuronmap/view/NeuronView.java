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
 * Відображає нейрон, його порти й напрямок сигналу та відстежує перетягування, вибір і показ вхідного сигналу.
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
     * Створює екземпляр NeuronView та зберігає передані залежності, потрібні для його роботи.
     *
     * @param presentation візуальне подання нейрона.
     */
    public NeuronView(NeuronPresentation presentation) {
        if (presentation == null) {

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
     * Повертає модель карти нейронів.
     *
     * @return модель карти нейронів.
     */
    public Neuron model() {
        return presentation.neuron();
    }

    /**
     * Повертає візуальне подання нейрона.
     *
     * @return візуальне подання нейрона.
     */
    public NeuronPresentation presentation() {
        return presentation;
    }

    /**
     * Повертає ознаку того, що подання нейрона справді перемістили під час поточного жесту миші.
     *
     * @return ознаку того, що подання нейрона справді перемістили під час поточного жесту миші.
     */
    public boolean wasDragged() {
        return wasDragged;
    }

    /**
     * Перевіряє, чи dragging за поточного стану компонента.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean isDragging() {
        return dragging;
    }

    /**
     * Перевіряє, чи dragging group за поточного стану компонента.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean isDraggingGroup() {
        return dragGroup;
    }

    /**
     * Запам’ятовує початкові координати перетягування та модифікатори, які впливають на переміщення.
     *
     * @param sceneX горизонтальна координата точки у системі координат сцени JavaFX.
     * @param sceneY вертикальна координата точки у системі координат сцени JavaFX.
     * @param altPressed значення «alt pressed», яке використовується в цьому методі.
     * @param belongsToGroup значення «belongs to group», яке використовується в цьому методі.
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
     * Обчислює горизонтальне зміщення від початку перетягування.
     *
     * @param sceneX горизонтальна координата точки у системі координат сцени JavaFX.
     */
    public double dragDeltaX(double sceneX) {
        double delta = sceneX - lastSceneX;
        lastSceneX = sceneX;
        return delta;
    }

    /**
     * Обчислює вертикальне зміщення від початку перетягування.
     *
     * @param sceneY вертикальна координата точки у системі координат сцени JavaFX.
     */
    public double dragDeltaY(double sceneY) {
        double delta = sceneY - lastSceneY;
        lastSceneY = sceneY;
        return delta;
    }

    /**
     * Оновлює dragged state після зміни даних або взаємодії користувача.
     *
     * @param sceneX горизонтальна координата точки у системі координат сцени JavaFX.
     * @param sceneY вертикальна координата точки у системі координат сцени JavaFX.
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
     * Завершує перетягування та очищає тимчасовий стан жесту.
     */
    public void endDrag() {
        dragging = false;
    }

/**
 * Показує на нейроні індикатор із величиною отриманого вхідного сигналу.
 *
 * @param signal значення «signal», яке використовується в цьому методі.
 */
public void showInputSignal(int signal) {
        displayedInputSignal = signal;
        inputSignalLabel.setText(Integer.toString(signal));
    }

/**
 * Приховує індикатор вхідного сигналу.
 */
public void hideInputSignal() {
        displayedInputSignal = null;
        inputSignalLabel.setText("0");
    }

/**
 * Очищає текст і стан відображення вхідного сигналу.
 */
public void clearDisplayedInputSignal() {
        hideInputSignal();
    }

    /**
     * Перевіряє, чи direction reversed за поточного стану компонента.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean isDirectionReversed() {
        return presentation.directionReversed();
    }

    /**
     * Перемикає напрямок портів нейрона та оновлює його видиме подання.
     */
    public void toggleDirection() {
        presentation.toggleDirection();
        positionFixedChildren();
    }

    /**
     * Оновлює колір, напрямок і позначення нейрона з урахуванням його стану та вибору.
     *
     * @param selected значення «selected», яке використовується в цьому методі.
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
 * Повертає світову точку на вихідному порту нейрона, до якої має приєднуватися зв’язок.
 *
 * @return світову точку на вихідному порту нейрона, до якої має приєднуватися зв’язок.
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
 * Повертає світову точку на вхідному порту нейрона, до якої має приєднуватися зв’язок.
 *
 * @return світову точку на вхідному порту нейрона, до якої має приєднуватися зв’язок.
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
     * Перераховує положення постійних графічних елементів усередині подання нейрона.
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
     * Перетворює точку з локальних координат нейрона на світові координати полотна.
     *
     * @param localPoint значення «local point», яке використовується в цьому методі.
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
     * Налаштовує signal label для роботи з відповідним елементом інтерфейсу.
     *
     * @param label значення «label», яке використовується в цьому методі.
     */
    private void configureSignalLabel(Label label) {
        label.getStyleClass().add("neuron-activation");
        label.setAlignment(Pos.CENTER);
        label.setMouseTransparent(true);
    }

    /**
     * Повертає базовий колір тіла нейрона відповідно до його типу.
     *
     * @return базовий колір тіла нейрона відповідно до його типу.
     */
    private Color baseColor() {
        return model().type() == NeuronType.EXCITATORY
                ? Color.web("#328b55")
                : Color.web("#646971");
    }
}
