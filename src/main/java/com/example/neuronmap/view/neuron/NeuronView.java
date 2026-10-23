package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.support.TooltipFactory;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Pane;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

/**
 * Відображає нейрон, його порти, залишок активних тактів, підказку параметрів, назву й стани взаємодії.
 *
 * <p>Область взаємодії визначається геометрією тіла з округленими кутами та видимими портами;
 * тінь і прямокутні межі контейнера не розширюють її.</p>
 */
public final class NeuronView extends StackPane {

    public static final double WIDTH = NeuronVisualGeometry.WIDTH;
    public static final double HEIGHT = NeuronVisualGeometry.HEIGHT;
    public static final double SELECTED_STROKE_WIDTH = 2.0;

    /** Текстова позначка вхідного сигналу, коли тимчасове значення відсутнє. */
    private static final String NO_INPUT_SIGNAL_TEXT = "0";
    private static final String NEURON_VIEW_STYLE_CLASS = "neuron-view";
    private static final String EXCITATORY_BODY_STYLE_CLASS = "neuron-card-excitatory";
    private static final String INHIBITORY_BODY_STYLE_CLASS = "neuron-card-inhibitory";
    private static final String SELECTED_BODY_STYLE_CLASS = "neuron-card-selected";
    private static final String INPUT_SIGNAL_INSUFFICIENT_STYLE_CLASS = "port-input-insufficient";
    private static final String INPUT_SIGNAL_SUFFICIENT_STYLE_CLASS = "port-input-sufficient";
    private static final String OUTPUT_SIGNAL_EXCITATORY_STYLE_CLASS = "port-output-signal-excitatory";
    private static final String OUTPUT_SIGNAL_INHIBITORY_STYLE_CLASS = "port-output-signal-inhibitory";
    private static final String NAME_LABEL_STYLE_CLASS = "neuron-name-label";
    private static final double NUMERIC_LABEL_WIDTH = 48.0;
    private static final double NUMERIC_LABEL_HEIGHT = 28.0;
    private static final double NUMERIC_LABEL_SIDE_GAP = 10.0;
    private static final double NAME_LABEL_HORIZONTAL_PADDING = 16.0;
    private static final double NAME_LABEL_MIN_WIDTH = 36.0;
    private static final double NAME_LABEL_MAX_WIDTH = 280.0;
    private static final double NAME_LABEL_MIN_HEIGHT = 26.0;

    private final NeuronPresentation presentation;
    private final Rectangle body = new Rectangle(WIDTH, HEIGHT);
    private final Circle inputPort = new Circle(NeuronVisualGeometry.INPUT_PORT_RADIUS);
    private final Polygon outputPort = new Polygon();
    private final Label inputSignalLabel = new Label();
    private final Label remainingActiveTicksLabel = new Label();
    private final Label nameLabel = new Label();
    private final Tooltip parameterTooltip = TooltipFactory.create("");

    private LocalizationService tooltipLocalization;
    private Integer displayedInputSignal;
    private int displayedRemainingActiveTicks;
    private boolean outputSignalActive;
    private boolean dragging;
    private boolean wasDragged;
    private boolean dragGroup;
    private boolean selected;
    private boolean pointerHovered;
    private boolean nameHiddenWhileRotating;
    private boolean parameterTooltipInstalled;
    private boolean parameterTooltipDisposed;
    private double dragStartSceneX;
    private double dragStartSceneY;
    private double lastSceneX;
    private double lastSceneY;

    /**
     * Створює подання нейрона, його графічні дочірні вузли та підказку параметрів.
     *
     * @param presentation візуальне подання нейрона.
     * @throws IllegalArgumentException якщо подання не задано.
     */
    public NeuronView(NeuronPresentation presentation) {
        if (presentation == null) {
            throw new IllegalArgumentException("presentation must not be null");
        }

        this.presentation = presentation;
        getStyleClass().add(NEURON_VIEW_STYLE_CLASS);
        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        setPickOnBounds(false);
        setRotate(presentation.rotationDegrees());

        body.getStyleClass().add("neuron-card");
        body.setArcWidth(28);
        body.setArcHeight(28);
        body.setMouseTransparent(false);
        body.setManaged(false);

        configureNumericLabel(inputSignalLabel);
        configureNumericLabel(remainingActiveTicksLabel);
        configureNameLabel();
        inputPort.getStyleClass().add("port");
        inputPort.setMouseTransparent(false);
        inputPort.setManaged(false);
        outputPort.getStyleClass().add("port");
        outputPort.setMouseTransparent(false);
        outputPort.setManaged(false);
        inputSignalLabel.setManaged(false);
        remainingActiveTicksLabel.setManaged(false);
        inputSignalLabel.setText(NO_INPUT_SIGNAL_TEXT);
        remainingActiveTicksLabel.setText(Integer.toString(displayedRemainingActiveTicks));
        getChildren().addAll(body, inputSignalLabel, remainingActiveTicksLabel, inputPort, outputPort, nameLabel);
        addEventHandler(MouseEvent.MOUSE_ENTERED, _ -> {
            pointerHovered = true;
            refreshNameLabel();
            refreshParameterTooltip();
        });
        addEventHandler(MouseEvent.MOUSE_MOVED, _ -> {
            pointerHovered = true;
            refreshNameLabel();
            refreshParameterTooltip();
        });
        addEventHandler(MouseEvent.MOUSE_EXITED, _ -> {
            pointerHovered = false;
            refreshNameLabel();
        });
        refreshInputPortStyleClass();
        refreshOutputPortStyleClass();
        refreshVisuals(false);
    }

    /**
     * Перевіряє точку за геометрією видимих частин нейрона, не враховуючи ефекти та прямокутні межі контейнера.
     *
     * @param localX горизонтальна координата точки в локальній системі нейрона.
     * @param localY вертикальна координата точки в локальній системі нейрона.
     * @return {@code true}, якщо точка перетинає тіло, вхідний порт або вихідний трикутник.
     */
    @Override
    public boolean contains(double localX, double localY) {
        return body.contains(body.parentToLocal(localX, localY))
                || inputPort.contains(inputPort.parentToLocal(localX, localY))
                || outputPort.contains(outputPort.parentToLocal(localX, localY));
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

    /** Завершує перетягування та очищає відповідний стан. */
    public void endDrag() {
        dragging = false;
    }

    /**
     * Показує величину запланованого вхідного сигналу й забарвлює порт за порогом активації.
     *
     * @param signal уже обчислена сума сигналів, спрямованих до нейрона.
     */
    public void showInputSignal(int signal) {
        displayedInputSignal = signal;
        inputSignalLabel.setText(Integer.toString(signal));
        refreshInputPortStyleClass();
    }

    /** Приховує індикатор вхідного сигналу й повертає вхідний порт до нейтрального кольору. */
    public void hideInputSignal() {
        displayedInputSignal = null;
        inputSignalLabel.setText(NO_INPUT_SIGNAL_TEXT);
        refreshInputPortStyleClass();
    }

    /** Очищає текст і стан відображення вхідного сигналу. */
    public void clearDisplayedInputSignal() {
        hideInputSignal();
    }

    /**
     * Негайно встановлює кількість активних тактів, яка залишилася в нейрона.
     * Значення зберігається в поданні, щоб звичайне оновлення геометрії не повертало застарілий текст.
     *
     * @param remainingActiveTicks залишок тактів; нуль означає, що нейрон не має активних тактів.
     * @throws IllegalArgumentException якщо залишок тактів від'ємний.
     */
    public void setRemainingActiveTicks(int remainingActiveTicks) {
        if (remainingActiveTicks < 0) {
            throw new IllegalArgumentException("Remaining active ticks must not be negative.");
        }
        displayedRemainingActiveTicks = remainingActiveTicks;
        remainingActiveTicksLabel.setText(Integer.toString(remainingActiveTicks));
    }

    /**
     * Встановлює, чи передає нейрон вихідний сигнал, із кольором відповідно до його типу.
     *
     * @param active {@code true}, якщо нейрон надсилає сигнал; інакше {@code false}.
     */
    public void setOutputSignalActive(boolean active) {
        outputSignalActive = active;
        refreshOutputPortStyleClass();
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
     * Налаштовує локалізацію параметрів у спливаючій підказці та одразу оновлює її текст.
     *
     * @param localization спільна служба локалізації застосунку.
     * @throws IllegalArgumentException якщо службу локалізації не задано.
     */
    public void configureParameterTooltipLocalization(LocalizationService localization) {
        if (localization == null) {
            throw new IllegalArgumentException("localization must not be null");
        }
        tooltipLocalization = localization;
        parameterTooltipDisposed = false;
        refreshParameterTooltip();
    }

    /**
     * Приховує та від'єднує спливаючу підказку перед вилученням нейрона з карти.
     * Після повторного встановлення обробників підказку можна налаштувати знову.
     */
    public void disposeParameterTooltip() {
        parameterTooltipDisposed = true;
        parameterTooltip.hide();
        if (parameterTooltipInstalled) {
            Tooltip.uninstall(this, parameterTooltip);
            parameterTooltipInstalled = false;
        }
        tooltipLocalization = null;
        refreshParameterTooltipText();
    }

    /**
     * Оновлює розташування та класи стилю нейрона з урахуванням типу й вибору, не змінюючи залишок активних тактів.
     * Вибраний нейрон не показує підказку параметрів і власну назву.
     *
     * @param selected ознака вибраного нейрона.
     */
    public void refreshVisuals(boolean selected) {
        this.selected = selected;
        setLayoutX(presentation.x());
        setLayoutY(presentation.y());
        setRotate(presentation.rotationDegrees());
        refreshBodyStyleClasses(selected);
        refreshInputPortStyleClass();
        refreshOutputPortStyleClass();

        inputSignalLabel.setText(displayedInputSignal == null
                ? NO_INPUT_SIGNAL_TEXT
                : Integer.toString(displayedInputSignal));
        remainingActiveTicksLabel.setText(Integer.toString(displayedRemainingActiveTicks));
        positionFixedChildren();
        refreshNameLabel();
        refreshParameterTooltip();
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
        double labelY = (HEIGHT - NUMERIC_LABEL_HEIGHT) / 2.0;
        double leftLabelX = NUMERIC_LABEL_SIDE_GAP;
        double rightLabelX = WIDTH - NUMERIC_LABEL_WIDTH - NUMERIC_LABEL_SIDE_GAP;
        inputSignalLabel.resize(NUMERIC_LABEL_WIDTH, NUMERIC_LABEL_HEIGHT);
        inputSignalLabel.relocate(presentation.directionReversed() ? rightLabelX : leftLabelX, labelY);
        remainingActiveTicksLabel.resize(NUMERIC_LABEL_WIDTH, NUMERIC_LABEL_HEIGHT);
        remainingActiveTicksLabel.relocate(presentation.directionReversed() ? leftLabelX : rightLabelX, labelY);
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
     * бажаного. Це не спричиняє зайвого повторного CSS-проходу під час кожного такту симуляції.
     * Стан наведення визначається CSS за псевдокласом {@code :hover} батьківського вузла.</p>
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

    /** Визначає колір вхідного порту за вже відображеною сумою сигналів і порогом нейрона. */
    private void refreshInputPortStyleClass() {
        String desiredClass = null;
        if (displayedInputSignal != null) {
            desiredClass = displayedInputSignal >= model().activationThreshold()
                    ? INPUT_SIGNAL_SUFFICIENT_STYLE_CLASS
                    : INPUT_SIGNAL_INSUFFICIENT_STYLE_CLASS;
        }
        updateExclusivePortStyleClass(
                inputPort,
                INPUT_SIGNAL_INSUFFICIENT_STYLE_CLASS,
                INPUT_SIGNAL_SUFFICIENT_STYLE_CLASS,
                desiredClass
        );
    }

    /** Визначає колір вихідного порту за активністю передачі й типом нейрона. */
    private void refreshOutputPortStyleClass() {
        String desiredClass = null;
        if (outputSignalActive) {
            desiredClass = model().type() == NeuronType.EXCITATORY
                    ? OUTPUT_SIGNAL_EXCITATORY_STYLE_CLASS
                    : OUTPUT_SIGNAL_INHIBITORY_STYLE_CLASS;
        }
        updateExclusivePortStyleClass(
                outputPort,
                OUTPUT_SIGNAL_EXCITATORY_STYLE_CLASS,
                OUTPUT_SIGNAL_INHIBITORY_STYLE_CLASS,
                desiredClass
        );
    }

    /**
     * Залишає на графічному порту не більше одного класу з заданої пари.
     *
     * @param port графічний вузол порту.
     * @param firstClass перший взаємовиключний клас.
     * @param secondClass другий взаємовиключний клас.
     * @param desiredClass потрібний клас або {@code null}, щоб прибрати обидва.
     */
    private void updateExclusivePortStyleClass(
            Node port,
            String firstClass,
            String secondClass,
            String desiredClass
    ) {
        if (desiredClass == null) {
            port.getStyleClass().remove(firstClass);
            port.getStyleClass().remove(secondClass);
            return;
        }

        String otherClass = desiredClass.equals(firstClass) ? secondClass : firstClass;
        port.getStyleClass().remove(otherClass);
        if (!port.getStyleClass().contains(desiredClass)) {
            port.getStyleClass().add(desiredClass);
        }
    }

    /**
     * Оновлює текст підказки лише параметрами, які редагуються в налаштуваннях нейрона.
     * Мова береться з активної служби локалізації, а не із системної локалі JVM.
     */
    private void refreshParameterTooltipText() {
        Neuron neuron = model();
        String signalLabel = tooltipLocalization == null
                ? "Signal strength"
                : tooltipLocalization.text("neuron.settings.signal_strength");
        String thresholdLabel = tooltipLocalization == null
                ? "Activation threshold"
                : tooltipLocalization.text("neuron.settings.activation_threshold");
        String activeTicksLabel = tooltipLocalization == null
                ? "Active ticks"
                : tooltipLocalization.textFrom("i18n.neuronSettings", "active_ticks");
        parameterTooltip.setText(String.join("\n",
                parameterLine(signalLabel, neuron.signalStrength()),
                parameterLine(thresholdLabel, neuron.activationThreshold()),
                parameterLine(activeTicksLabel, neuron.activeTicks())));
    }

    /**
     * Форматує один рядок підказки, нормалізуючи підпис із налаштувань до єдиного формату.
     *
     * @param label локалізований підпис параметра, із двокрапкою або без неї.
     * @param value значення параметра нейрона.
     * @return рядок формату «назва параметра: значення».
     */
    private static String parameterLine(String label, int value) {
        String normalizedLabel = label == null ? "" : label.strip();
        while (normalizedLabel.endsWith(":")) {
            normalizedLabel = normalizedLabel.substring(0, normalizedLabel.length() - 1).stripTrailing();
        }
        return normalizedLabel + ": " + value;
    }

    /**
     * Перераховує геометрію назви після зміни перетворень камери.
     *
     * <p>Викликається зі спільного проходу оновлення екранних накладок, який уже використовує
     * контекстне меню під час zoom і pan. Метод не змінює стан наведення або вибору нейрона.</p>
     */
    public void refreshHoverNameGeometry() {
        refreshNameLabel();
    }

    /** Повертає назву як окрему накладку, щоб вона не залежала від порядку шарів нейронів. */
    public Label nameOverlayNode() {
        return nameLabel;
    }

    /** Оновлює геометрію назви в координатах верхнього шару полотна. */
    public void refreshHoverNameGeometry(Pane overlayLayer) {
        refreshNameLabel(overlayLayer);
    }

    /** Приховує назву лише на час активного жесту обертання. */
    public void setNameHiddenWhileRotating(boolean hidden) {
        nameHiddenWhileRotating = hidden;
        refreshNameLabel();
    }

    /** Узгоджує стан наведення назви з наведенням на ручку обертання. */
    public void setNameHoverActive(boolean active) {
        // Під час жесту обертання ручка може залишити область наведення, але назва має
        // знову з'явитися при відпусканні ручки, навіть якщо вказівник уже поза нейроном.
        if (!active && nameHiddenWhileRotating) {
            return;
        }
        pointerHovered = active;
        refreshNameLabel();
    }

    /** Оновлює текст підказки й узгоджує її встановлення з поточним станом вибору нейрона. */
    private void refreshParameterTooltip() {
        refreshParameterTooltipText();
        if (parameterTooltipDisposed) {
            return;
        }
        if (selected) {
            parameterTooltip.hide();
            if (parameterTooltipInstalled) {
                Tooltip.uninstall(this, parameterTooltip);
                parameterTooltipInstalled = false;
            }
            return;
        }
        if (!parameterTooltipInstalled) {
            Tooltip.install(this, parameterTooltip);
            parameterTooltipInstalled = true;
        }
    }

    /**
     * Оновлює окремий підпис назви та розміщує його над тілом нейрона, зберігаючи текст горизонтальним.
     * Підпис не є ціллю миші, тому він не заважає взаємодії з полотном.
     */
    private void refreshNameLabel() {
        // У тестах та до під'єднання до полотна позначка є дочірнім вузлом самого нейрона;
        // у редакторі її переносить координатор до окремого екранного шару.
        Node parent = nameLabel.getParent();
        refreshNameLabel(parent instanceof Pane pane && parent != this ? pane : null);
    }

    private void refreshNameLabel(Pane overlayLayer) {
        String name = model().name();
        boolean shouldShow = pointerHovered && !nameHiddenWhileRotating
                && name != null && !name.isEmpty();
        nameLabel.setText(name == null ? "" : name);
        nameLabel.setRotate(-presentation.rotationDegrees());
        if (shouldShow) {
            nameLabel.applyCss();
            double textWidth = nameLabel.prefWidth(-1.0);
            double labelWidth = Math.clamp(
                    textWidth + NAME_LABEL_HORIZONTAL_PADDING,
                    NAME_LABEL_MIN_WIDTH,
                    NAME_LABEL_MAX_WIDTH
            );
            double labelHeight = Math.max(
                    NAME_LABEL_MIN_HEIGHT,
                    nameLabel.prefHeight(labelWidth)
            );
            nameLabel.resize(labelWidth, labelHeight);
            positionNameLabel(labelWidth, labelHeight, overlayLayer);
        }
        nameLabel.setVisible(shouldShow);
        nameLabel.setManaged(false);
    }

    /**
     * Розміщує прямокутник над верхньою межею повернутого нейрона та зберігає його екранний розмір.
     *
     * @param labelWidth виміряна ширина прямокутника назви.
     * @param labelHeight виміряна висота прямокутника назви.
     */
    private void positionNameLabel(double labelWidth, double labelHeight, Pane overlayLayer) {
        double screenScale = sceneScale();
        if (overlayLayer != null && overlayLayer.getScene() != null) {
            // Використовуємо той самий принцип, що й NeuronOverlayPositioner для меню:
            // визначаємо екранну рамку нейрона через перетворені кути, а напис ставимо
            // горизонтально над її верхнім краєм у координатах шару накладок.
            Point2D[] corners = {
                    localToScene(0.0, 0.0),
                    localToScene(WIDTH, 0.0),
                    localToScene(0.0, HEIGHT),
                    localToScene(WIDTH, HEIGHT)
            };
            double topY = java.util.Arrays.stream(corners)
                    .mapToDouble(Point2D::getY)
                    .min()
                    .orElseThrow();
            Point2D centerInScene = localToScene(WIDTH / 2.0, HEIGHT / 2.0);
            Point2D center = overlayLayer.sceneToLocal(centerInScene);
            Point2D top = overlayLayer.sceneToLocal(centerInScene.getX(), topY);
            nameLabel.setRotate(0.0);
            nameLabel.setScaleX(1.0);
            nameLabel.setScaleY(1.0);
            nameLabel.relocate(
                    center.getX() - labelWidth / 2.0,
                    top.getY() - NeuronOverlayPositioner.MENU_GAP - labelHeight
            );
        } else {
            // До перенесення на екранний шар (зокрема в ізольованих тестах) напис лишається
            // у локальній системі нейрона й компенсує його поворот та масштаб камери.
            double angle = Math.toRadians(presentation.rotationDegrees());
            double halfRotatedHeight = (
                    Math.abs(Math.sin(angle)) * WIDTH
                            + Math.abs(Math.cos(angle)) * HEIGHT
            ) / 2.0;
            double centerOffsetY = -halfRotatedHeight
                    - (NeuronOverlayPositioner.MENU_GAP + labelHeight / 2.0) / screenScale;
            double centerX = WIDTH / 2.0 + centerOffsetY * Math.sin(angle);
            double centerY = HEIGHT / 2.0 + centerOffsetY * Math.cos(angle);
            nameLabel.setRotate(-presentation.rotationDegrees());
            nameLabel.setScaleX(1.0 / screenScale);
            nameLabel.setScaleY(1.0 / screenScale);
            nameLabel.relocate(centerX - labelWidth / 2.0, centerY - labelHeight / 2.0);
        }
    }

    /**
     * Визначає масштаб координат сцени відносно локальних координат нейрона.
     * Це дає змогу залишити підпис незмінного екранного розміру незалежно від масштабу карти.
     *
     * @return додатний скінченний масштаб або одиниця, якщо координати ще недоступні.
     */
    private double sceneScale() {
        Point2D center = localToScene(WIDTH / 2.0, HEIGHT / 2.0);
        Point2D sample = localToScene(WIDTH / 2.0 + 1.0, HEIGHT / 2.0);
        double scale = center.distance(sample);
        return Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
    }

    /** Повертає сформований текст підказки для перевірки формату в автоматизованих тестах. */
    String parameterTooltipTextForTest() {
        return parameterTooltip.getText();
    }

    /** Повертає факт встановлення підказки для перевірки поведінки вибраного нейрона. */
    boolean isParameterTooltipInstalledForTest() {
        return parameterTooltipInstalled;
    }

    /** Повертає вузол назви для тестів видимості та стилізації підпису. */
    Label nameLabelForTest() {
        return nameLabel;
    }

    /** Повертає ознаку відображення назви без потреби показувати справжнє вікно. */
    boolean isNameLabelVisibleForTest() {
        return nameLabel.isVisible();
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
     * Налаштовує текстову позначку вхідної суми або залишку активних тактів.
     *
     * @param label позначка числового стану нейрона.
     */
    private void configureNumericLabel(Label label) {
        label.getStyleClass().add("neuron-activation");
        label.setAlignment(Pos.CENTER);
        label.setMouseTransparent(true);
    }

    /** Налаштовує незалежний графічний прямокутник назви над нейроном. */
    private void configureNameLabel() {
        nameLabel.getStyleClass().add(NAME_LABEL_STYLE_CLASS);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(NAME_LABEL_MAX_WIDTH);
        nameLabel.setManaged(false);
        nameLabel.setVisible(false);
        nameLabel.setMouseTransparent(true);
        nameLabel.setViewOrder(0.0);
    }
}
