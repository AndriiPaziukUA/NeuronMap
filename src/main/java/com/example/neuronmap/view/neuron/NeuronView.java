package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
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

    /** Текстова позначка вхідного сигналу, коли тимчасове значення відсутнє. */
    private static final String NO_INPUT_SIGNAL_TEXT = "0";
    private static final String EXCITATORY_BODY_STYLE_CLASS = "neuron-card-excitatory";
    private static final String INHIBITORY_BODY_STYLE_CLASS = "neuron-card-inhibitory";
    private static final String SELECTED_BODY_STYLE_CLASS = "neuron-card-selected";
    private static final double SIGNAL_LABEL_WIDTH = 48.0;
    private static final double SIGNAL_LABEL_HEIGHT = 28.0;
    private static final double SIGNAL_LABEL_SIDE_GAP = 10.0;

    private final NeuronPresentation presentation;
    private final Rectangle body = new Rectangle(WIDTH, HEIGHT);
    private final Circle inputPort = new Circle(NeuronVisualGeometry.INPUT_PORT_RADIUS);
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
     * Створює подання нейрона та його графічні дочірні вузли.
     *
     * @param presentation візуальне подання нейрона.
     * @throws IllegalArgumentException якщо подання не задано.
     */
    public NeuronView(NeuronPresentation presentation) {
        if (presentation == null) {
            throw new IllegalArgumentException("presentation must not be null");
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
        inputSignalLabel.setText(NO_INPUT_SIGNAL_TEXT);
        outputSignalLabel.setText(Integer.toString(model().signalWeight()));
        getChildren().addAll(body, inputSignalLabel, outputSignalLabel, inputPort, outputPort);
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
     * Повертає ознаку фактичного переміщення під час поточного жесту миші.
     *
     * @return {@code true}, якщо нейрон перемістили.
     */
    public boolean wasDragged() {
        return wasDragged;
    }

    /**
     * Перевіряє стан перетягування.
     *
     * @return {@code true}, якщо перетягування активне.
     */
    public boolean isDragging() {
        return dragging;
    }

    /**
     * Перевіряє, чи перетягується група нейронів.
     *
     * @return {@code true}, якщо рух відбувається в режимі групи.
     */
    public boolean isDraggingGroup() {
        return dragGroup;
    }

    /**
     * Запам'ятовує початкові координати перетягування та модифікатори переміщення.
     *
     * @param sceneX горизонтальна координата у системі координат сцени.
     * @param sceneY вертикальна координата у системі координат сцени.
     * @param altPressed чи натиснуто Alt.
     * @param belongsToGroup чи належить нейрон до групи.
     */
    public void beginDrag(double sceneX, double sceneY, boolean altPressed, boolean belongsToGroup) {
        dragging = true;
        wasDragged = false;
        dragStartSceneX = sceneX;
        dragStartSceneY = sceneY;
        lastSceneX = sceneX;
        lastSceneY = sceneY;
        dragGroup = !altPressed && belongsToGroup;
    }

    /**
     * Обчислює горизонтальне зміщення від попередньої точки жесту.
     *
     * @param sceneX поточна координата X сцени.
     * @return горизонтальне зміщення.
     */
    public double dragDeltaX(double sceneX) {
        double delta = sceneX - lastSceneX;
        lastSceneX = sceneX;
        return delta;
    }

    /**
     * Обчислює вертикальне зміщення від попередньої точки жесту.
     *
     * @param sceneY поточна координата Y сцени.
     * @return вертикальне зміщення.
     */
    public double dragDeltaY(double sceneY) {
        double delta = sceneY - lastSceneY;
        lastSceneY = sceneY;
        return delta;
    }

    /**
     * Позначає жест як переміщення після перевищення порога руху.
     *
     * @param sceneX поточна координата X сцени.
     * @param sceneY поточна координата Y сцени.
     */
    public void updateDraggedState(double sceneX, double sceneY) {
        if (Math.hypot(sceneX - dragStartSceneX, sceneY - dragStartSceneY) > 4.0) {
            wasDragged = true;
        }
    }

    /**
     * Завершує перетягування та очищає відповідний стан.
     */
    public void endDrag() {
        dragging = false;
    }

    /**
     * Показує на нейроні індикатор із величиною отриманого вхідного сигналу.
     *
     * @param signal величина вхідного сигналу.
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
        inputSignalLabel.setText(NO_INPUT_SIGNAL_TEXT);
    }

    /**
     * Очищає текст і стан відображення вхідного сигналу.
     */
    public void clearDisplayedInputSignal() {
        hideInputSignal();
    }

    /**
     * Перевіряє, чи розвернуто напрямок нейрона.
     *
     * @return {@code true}, якщо напрямок розвернуто.
     */
    public boolean isDirectionReversed() {
        return presentation.directionReversed();
    }

    /**
     * Оновлює розташування та класи стилю нейрона з урахуванням типу й вибору.
     *
     * @param selected ознака вибраного нейрона.
     */
    public void refreshVisuals(boolean selected) {
        setLayoutX(presentation.x());
        setLayoutY(presentation.y());
        setRotate(presentation.rotationDegrees());
        refreshBodyStyleClasses(selected);

        inputSignalLabel.setText(displayedInputSignal == null ? NO_INPUT_SIGNAL_TEXT : Integer.toString(displayedInputSignal));
        outputSignalLabel.setText(Integer.toString(model().signalWeight()));
        positionFixedChildren();
    }

    /**
     * Повертає світову точку вихідного порту для під'єднання зв'язку.
     *
     * @return точка вихідного порту.
     */
    public Point2D outputPoint() {
        return localPointToWorld(new Point2D(
                NeuronVisualGeometry.outputTipX(presentation.directionReversed()), HEIGHT / 2.0));
    }

    /**
     * Повертає світову точку вхідного порту для під'єднання зв'язку.
     *
     * @return точка вхідного порту.
     */
    public Point2D inputPoint() {
        return localPointToWorld(new Point2D(
                NeuronVisualGeometry.inputPortX(presentation.directionReversed()), HEIGHT / 2.0));
    }

    /**
     * Оновлює положення графічних елементів усередині подання нейрона.
     *
     * <p>Тіло нейрона вже має незмінні локальні координати (0, 0). Його не можна повторно
     * позиціонувати через {@code relocate}: цей метод спирається на {@code layoutBounds},
     * які залежать від CSS-ширини рамки та могли б зрушувати тіло під час оновлень симуляції.</p>
     */
    private void positionFixedChildren() {
        double signalY = (HEIGHT - SIGNAL_LABEL_HEIGHT) / 2.0;
        double leftSignalX = SIGNAL_LABEL_SIDE_GAP;
        double rightSignalX = WIDTH - SIGNAL_LABEL_WIDTH - SIGNAL_LABEL_SIDE_GAP;
        inputSignalLabel.resize(SIGNAL_LABEL_WIDTH, SIGNAL_LABEL_HEIGHT);
        inputSignalLabel.relocate(presentation.directionReversed() ? rightSignalX : leftSignalX, signalY);
        outputSignalLabel.resize(SIGNAL_LABEL_WIDTH, SIGNAL_LABEL_HEIGHT);
        outputSignalLabel.relocate(presentation.directionReversed() ? leftSignalX : rightSignalX, signalY);
        NeuronVisualGeometry.positionInputPort(inputPort, presentation.directionReversed());
        NeuronVisualGeometry.positionOutputTriangle(outputPort, presentation.directionReversed());
        outputPort.setTranslateX(presentation.directionReversed()
                ? -SELECTED_STROKE_WIDTH / 2.0
                : SELECTED_STROKE_WIDTH / 2.0);
    }

    /**
     * Синхронізує CSS-класи тіла нейрона з типом моделі та станом вибору.
     *
     * <p>Класи змінюються лише тоді, коли фактичний тип або стан вибору відрізняється від
     * бажаного. Це не спричиняє зайвого повторного CSS-проходу під час кожного такту симуляції.</p>
     *
     * @param selected ознака вибраного нейрона.
     */
    private void refreshBodyStyleClasses(boolean selected) {
        String desiredTypeClass = model().type() == NeuronType.EXCITATORY
                ? EXCITATORY_BODY_STYLE_CLASS
                : INHIBITORY_BODY_STYLE_CLASS;
        String otherTypeClass = desiredTypeClass.equals(EXCITATORY_BODY_STYLE_CLASS)
                ? INHIBITORY_BODY_STYLE_CLASS
                : EXCITATORY_BODY_STYLE_CLASS;

        if (!body.getStyleClass().contains(desiredTypeClass)) {
            body.getStyleClass().remove(otherTypeClass);
            body.getStyleClass().add(desiredTypeClass);
        }

        boolean currentlySelected = body.getStyleClass().contains(SELECTED_BODY_STYLE_CLASS);
        if (selected && !currentlySelected) {
            body.getStyleClass().add(SELECTED_BODY_STYLE_CLASS);
        } else if (!selected && currentlySelected) {
            body.getStyleClass().remove(SELECTED_BODY_STYLE_CLASS);
        }
    }

    /**
     * Перетворює точку з локальних координат нейрона на світові координати полотна.
     *
     * @param localPoint локальна точка.
     * @return світова точка на полотні.
     */
    private Point2D localPointToWorld(Point2D localPoint) {
        double centerX = WIDTH / 2.0;
        double centerY = HEIGHT / 2.0;
        double localX = localPoint.getX() - centerX;
        double localY = localPoint.getY() - centerY;
        double angle = Math.toRadians(presentation.rotationDegrees());
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rotatedX = localX * cos - localY * sin;
        double rotatedY = localX * sin + localY * cos;
        return new Point2D(presentation.x() + centerX + rotatedX, presentation.y() + centerY + rotatedY);
    }

    /**
     * Налаштовує текстову позначку вхідного або вихідного сигналу.
     *
     * @param label позначка сигналу.
     */
    private void configureSignalLabel(Label label) {
        label.getStyleClass().add("neuron-activation");
        label.setAlignment(Pos.CENTER);
        label.setMouseTransparent(true);
    }
}
