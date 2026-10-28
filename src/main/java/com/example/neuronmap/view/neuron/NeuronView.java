package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
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

import java.util.List;

/**
 * Відображає нейрон, його порти, залишок активних тактів, підказку параметрів, назву й стани взаємодії.
 * Для структурного нейрона також утримує шухлядку та закріплює подання її внутрішніх нейронів.
 *
 * <p>Область взаємодії визначається геометрією тіла з округленими кутами та видимими портами;
 * тінь і прямокутні межі контейнера не розширюють її. Для структурного нейрона подання також містить
 * висувну шухлядку з прикріпленими поданнями внутрішніх нейронів.</p>
 */
public final class NeuronView extends StackPane {

    public static final double WIDTH = NeuronVisualGeometry.WIDTH;
    public static final double HEIGHT = NeuronVisualGeometry.HEIGHT;
    public static final double SELECTED_STROKE_WIDTH = 2.0;

    private static final double BODY_ARC_SIZE = 28.0;

    /** Текстова позначка вхідного сигналу, коли тимчасове значення відсутнє. */
    private static final String NO_INPUT_SIGNAL_TEXT = "0";
    private static final String NEURON_VIEW_STYLE_CLASS = "neuron-view";
    private static final String EXCITATORY_BODY_STYLE_CLASS = "neuron-card-excitatory";
    private static final String INHIBITORY_BODY_STYLE_CLASS = "neuron-card-inhibitory";
    private static final String STRUCTURE_BODY_STYLE_CLASS = "neuron-card-structure";
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
    private final Rectangle body = new Rectangle(
            WIDTH - SELECTED_STROKE_WIDTH, HEIGHT - SELECTED_STROKE_WIDTH);
    private final Circle inputPort = new Circle(NeuronVisualGeometry.INPUT_PORT_RADIUS);
    private final Polygon outputPort = new Polygon();
    private final Label inputSignalLabel = new Label();
    private final Label remainingActiveTicksLabel = new Label();
    private final Label nameLabel = new Label();
    private final Tooltip parameterTooltip = TooltipFactory.create("");
    private NeuronStructureDrawerView structureDrawer;

    private LocalizationService tooltipLocalization;
    private Integer displayedInputSignal;
    private Boolean displayedInputSufficient;
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
    private boolean structureMember;
    private double dragStartSceneX;
    private double dragStartSceneY;
    private double lastSceneX;
    private double lastSceneY;

    /**
     * Створює подання нейрона, його графічні дочірні вузли та підказку параметрів.
     * Структурний нейрон додатково отримує приховану шухлядку для внутрішніх подань.
     * Контур тіла центрується в межах загального розміру картки, тому не виходить за межі рядка.
     *
     * @param presentation візуальне подання нейрона.
     * @throws IllegalArgumentException якщо подання не задано.
     */
    public NeuronView(NeuronPresentation presentation) {
        if (presentation == null) {
            throw new IllegalArgumentException("presentation must not be null");
        }

        this.presentation = presentation;
        structureDrawer = presentation.neuron().type() == NeuronType.STRUCTURE
                ? new NeuronStructureDrawerView() : null;
        getStyleClass().add(NEURON_VIEW_STYLE_CLASS);
        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        setPickOnBounds(false);
        setRotate(presentation.rotationDegrees());

        body.setX(SELECTED_STROKE_WIDTH / 2.0);
        body.setY(SELECTED_STROKE_WIDTH / 2.0);
        body.getStyleClass().add("neuron-card");
        body.setArcWidth(BODY_ARC_SIZE);
        body.setArcHeight(BODY_ARC_SIZE);
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
        if (structureDrawer != null) {
            structureDrawer.setViewOrder(-1.0);
            getChildren().add(structureDrawer);
        }
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

    /** Повертає модель нейрона, яку відображає подання. */
    public Neuron model() { return presentation.neuron(); }

    /** Повертає візуальне подання нейрона. */
    public NeuronPresentation presentation() { return presentation; }

    /** Повертає ознаку фактичного переміщення під час поточного жесту миші. */
    public boolean wasDragged() { return wasDragged; }

    /** Перевіряє, чи триває перетягування нейрона. */
    public boolean isDragging() { return dragging; }

    /** Перевіряє, чи перетягується група нейронів. */
    public boolean isDraggingGroup() { return dragGroup; }

    /** Перевіряє, чи закріплене подання всередині структури.
     * @return {@code true}, якщо нейрон не можна переміщувати окремо.
     */
    public boolean isStructureMember() { return structureMember; }

    /** Позначає подання як внутрішній елемент структури або як звичайний нейрон карти.
     * @param structureMember {@code true}, щоб закріпити нейрон усередині структури.
     */
    public void setStructureMember(boolean structureMember) {
        this.structureMember = structureMember;
        body.setArcWidth(BODY_ARC_SIZE);
        body.setArcHeight(BODY_ARC_SIZE);
        if (structureMember) {
            endDrag();
        }
    }

    /**
     * Установлює подання учасників структури в порядку моделі.
     * Шухлядка створюється за потреби, оскільки структура є окремою сутністю моделі.
     *
     * @param members подання нейронів, які зберігає структура.
     */
    public void setStructureMembers(List<NeuronView> members) {
        if (structureDrawer == null) {
            structureDrawer = new NeuronStructureDrawerView();
            structureDrawer.setViewOrder(-1.0);
            getChildren().add(structureDrawer);
        }
        structureDrawer.setMembers(members);
        structureDrawer.refreshLayout();
    }

    /** Перевіряє, чи має це подання шухлядку сутності структури.
     * @return {@code true}, якщо подання містить шухлядку структури.
     */
    public boolean hasStructureDrawer() {
        return structureDrawer != null;
    }

    /** Обчислює висоту рядка з урахуванням відкритої шухлядки вкладеної структури.
     * @return висота картки та її видимого вкладеного вмісту.
     */
    double displayedStructureRowHeight() {
        return HEIGHT + (structureDrawer != null && structureDrawer.isExpanded()
                ? structureDrawer.getHeight() : 0.0);
    }

    /** Відкриває або закриває шухлядку структури.
     * @return {@code true}, якщо шухлядка відкрита після перемикання.
     */
    public boolean toggleStructureDrawer() {
        return structureDrawer != null && structureDrawer.toggleExpanded();
    }

    /** Повертає поточний стан шухлядки.
     * @return {@code true}, якщо шухлядка структури відкрита.
     */
    public boolean isStructureDrawerExpanded() {
        return structureDrawer != null && structureDrawer.isExpanded();
    }

    /** Запам'ятовує початкові координати перетягування та модифікатори переміщення. */
    public void beginDrag(double sceneX, double sceneY, boolean altPressed, boolean belongsToGroup) {
        if (structureMember) {
            return;
        }
        dragging = true;
        wasDragged = false;
        dragStartSceneX = sceneX;
        dragStartSceneY = sceneY;
        lastSceneX = sceneX;
        lastSceneY = sceneY;
        dragGroup = !altPressed && belongsToGroup;
    }

    /** Обчислює горизонтальне зміщення від попередньої точки жесту. */
    public double dragDeltaX(double sceneX) {
        double delta = sceneX - lastSceneX;
        lastSceneX = sceneX;
        return delta;
    }

    /** Обчислює вертикальне зміщення від попередньої точки жесту. */
    public double dragDeltaY(double sceneY) {
        double delta = sceneY - lastSceneY;
        lastSceneY = sceneY;
        return delta;
    }

    /** Позначає жест як переміщення після перевищення порога руху. */
    public void updateDraggedState(double sceneX, double sceneY) {
        if (!structureMember && Math.hypot(sceneX - dragStartSceneX, sceneY - dragStartSceneY) > 4.0) {
            wasDragged = true;
        }
    }

    /** Завершує перетягування та очищає відповідний стан. */
    public void endDrag() { dragging = false; }

    /** Показує величину вхідного сигналу й забарвлює порт за чинним числовим порогом нейрона. */
    public void showInputSignal(int signal) {
        setDisplayedInputSignal(signal, null);
    }

    /**
     * Показує вхідне значення та застосовує результат порівняння із порогом, обчислений симуляцією.
     * Це дає змогу правильно забарвити порт, коли значення є знаковим лічильником, а поріг — відсотком.
     *
     * @param signal знакова сума вхідних значень для поточного режиму активації.
     * @param sufficientForActivation {@code true}, якщо це значення досягає порога активації.
     */
    public void showInputSignal(int signal, boolean sufficientForActivation) {
        setDisplayedInputSignal(signal, sufficientForActivation);
    }

    /** Оновлює текст і стан порту, зберігаючи за потреби результат порівняння, обчислений симуляцією. */
    private void setDisplayedInputSignal(int signal, Boolean sufficientForActivation) {
        displayedInputSignal = signal;
        displayedInputSufficient = sufficientForActivation;
        inputSignalLabel.setText(Integer.toString(signal));
        refreshInputPortStyleClass();
    }

    /** Приховує індикатор вхідного сигналу й повертає вхідний порт до нейтрального кольору. */
    public void hideInputSignal() {
        displayedInputSignal = null;
        displayedInputSufficient = null;
        inputSignalLabel.setText(NO_INPUT_SIGNAL_TEXT);
        refreshInputPortStyleClass();
    }

    /** Очищає текст і стан відображення вхідного сигналу. */
    public void clearDisplayedInputSignal() { hideInputSignal(); }

    /**
     * Негайно встановлює кількість активних тактів, яка залишилася в нейрона.
     * Значення зберігається в поданні, щоб оновлення геометрії не повертало застарілий текст.
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

    /** Встановлює, чи передає нейрон вихідний сигнал, із кольором відповідно до його типу. */
    public void setOutputSignalActive(boolean active) {
        outputSignalActive = active;
        refreshOutputPortStyleClass();
    }

    /** Перевіряє, чи розвернуто напрямок нейрона. */
    public boolean isDirectionReversed() { return presentation.directionReversed(); }

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

    /** Приховує та від'єднує підказку перед вилученням нейрона з карти. */
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

    /** Оновлює розташування й стилі нейрона, не змінюючи залишок активних тактів. */
    public void refreshVisuals(boolean selected) {
        this.selected = selected;
        if (!structureMember) {
            setLayoutX(presentation.x());
            setLayoutY(presentation.y());
        }
        setRotate(presentation.rotationDegrees());
        refreshBodyStyleClasses(selected);
        refreshInputPortStyleClass();
        refreshOutputPortStyleClass();
        inputSignalLabel.setText(displayedInputSignal == null
                ? NO_INPUT_SIGNAL_TEXT
                : Integer.toString(displayedInputSignal));
        remainingActiveTicksLabel.setText(Integer.toString(displayedRemainingActiveTicks));
        positionFixedChildren();
        if (structureDrawer != null) {
            structureDrawer.refreshLayout();
        }
        refreshNameLabel();
        refreshParameterTooltip();
    }

    /** Повертає світову точку вихідного порту за координатами моделі. */
    public Point2D outputPoint() {
        return localPointToWorld(new Point2D(
                NeuronVisualGeometry.outputTipX(presentation.directionReversed()), HEIGHT / 2.0));
    }

    /** Повертає світову точку вхідного порту за координатами моделі. */
    public Point2D inputPoint() {
        return localPointToWorld(new Point2D(
                NeuronVisualGeometry.inputPortX(presentation.directionReversed()), HEIGHT / 2.0));
    }

    /**
     * Повертає вихідну точку порту з урахуванням фактичного положення вузла в дереві JavaFX.
     * @return координати вихідного порту в системі координат сцени.
     */
    public Point2D outputPointInScene() {
        return localToScene(new Point2D(
                NeuronVisualGeometry.outputTipX(presentation.directionReversed()), HEIGHT / 2.0));
    }

    /**
     * Повертає вхідну точку порту з урахуванням фактичного положення вузла в дереві JavaFX.
     * @return координати вхідного порту в системі координат сцени.
     */
    public Point2D inputPointInScene() {
        return localToScene(new Point2D(
                NeuronVisualGeometry.inputPortX(presentation.directionReversed()), HEIGHT / 2.0));
    }

    /** Перевіряє, чи ховає закрита шухлядка цей нейрон або будь-яку з його вкладених структур.
     * @return {@code true}, якщо хоча б одна батьківська шухлядка закрита.
     */
    public boolean isHiddenInClosedStructureDrawer() {
        Node ancestor = getParent();
        while (ancestor != null) {
            if (ancestor instanceof NeuronStructureDrawerView drawer && !drawer.isVisible()) {
                return true;
            }
            ancestor = ancestor.getParent();
        }
        return false;
    }

    /** Оновлює положення графічних елементів усередині подання нейрона. */
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

    /** Синхронізує CSS-класи тіла нейрона з його типом і станом вибору. */
    private void refreshBodyStyleClasses(boolean selected) {
        String desiredTypeClass = switch (model().type()) {
            case EXCITATORY -> EXCITATORY_BODY_STYLE_CLASS;
            case INHIBITORY -> INHIBITORY_BODY_STYLE_CLASS;
            case STRUCTURE -> STRUCTURE_BODY_STYLE_CLASS;
        };
        if (!body.getStyleClass().contains(desiredTypeClass)) {
            body.getStyleClass().removeAll(
                    EXCITATORY_BODY_STYLE_CLASS, INHIBITORY_BODY_STYLE_CLASS, STRUCTURE_BODY_STYLE_CLASS);
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
            boolean sufficient = displayedInputSufficient != null
                    ? displayedInputSufficient
                    : displayedInputSignal >= model().activationThreshold();
            desiredClass = sufficient
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

    /** Визначає колір вихідного порту за активністю передачі й активуючою властивістю типу нейрона. */
    private void refreshOutputPortStyleClass() {
        String desiredClass = null;
        if (outputSignalActive) {
            desiredClass = model().type().isActivating()
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

    /** Залишає на графічному порту не більше одного класу з заданої пари. */
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

    /** Оновлює підказку параметрами з налаштувань нейрона та активної локалі застосунку. */
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
        String delayLabel = tooltipLocalization == null
                ? "Delay"
                : tooltipLocalization.textFrom("i18n.neuronSettings", "delay");
        String thresholdText = parameterLine(thresholdLabel, neuron.activationThreshold());
        if (neuron.activationMode() == NeuronActivationMode.SIGNAL_COUNT) {
            thresholdText += "%";
        }
        parameterTooltip.setText(String.join("\n",
                parameterLine(signalLabel, neuron.signalStrength()),
                thresholdText,
                parameterLine(activeTicksLabel, neuron.activeTicks()),
                parameterLine(delayLabel, neuron.delayTicks())));
    }

    /** Форматує рядок підказки як «назва параметра: значення», уніфікуючи двокрапки в підписах. */
    private static String parameterLine(String label, int value) {
        String normalizedLabel = label == null ? "" : label.strip();
        while (normalizedLabel.endsWith(":")) {
            normalizedLabel = normalizedLabel.substring(0, normalizedLabel.length() - 1).stripTrailing();
        }
        return normalizedLabel + ": " + value;
    }

    /** Перераховує геометрію назви після зміни перетворень камери. */
    public void refreshHoverNameGeometry() { refreshNameLabel(); }

    /** Повертає назву як окрему накладку, щоб вона не залежала від порядку шарів нейронів. */
    public Label nameOverlayNode() { return nameLabel; }

    /** Оновлює геометрію назви в координатах верхнього шару полотна. */
    public void refreshHoverNameGeometry(Pane overlayLayer) { refreshNameLabel(overlayLayer); }

    /** Приховує назву лише на час активного жесту обертання. */
    public void setNameHiddenWhileRotating(boolean hidden) {
        nameHiddenWhileRotating = hidden;
        refreshNameLabel();
    }

    /** Узгоджує стан наведення назви з наведенням на ручку обертання. */
    public void setNameHoverActive(boolean active) {
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

    /** Оновлює окремий підпис назви та розміщує його над тілом нейрона, зберігаючи текст горизонтальним. */
    private void refreshNameLabel() {
        Node parent = nameLabel.getParent();
        refreshNameLabel(parent instanceof Pane pane && parent != this ? pane : null);
    }

    /** Перераховує текст і геометрію підпису назви в локальному або окремому екранному шарі. */
    private void refreshNameLabel(Pane overlayLayer) {
        String name = model().name();
        boolean shouldShow = pointerHovered && !nameHiddenWhileRotating
                && !isHiddenInClosedStructureDrawer()
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
            double labelHeight = Math.max(NAME_LABEL_MIN_HEIGHT, nameLabel.prefHeight(labelWidth));
            nameLabel.resize(labelWidth, labelHeight);
            positionNameLabel(labelWidth, labelHeight, overlayLayer);
        }
        nameLabel.setVisible(shouldShow);
        nameLabel.setManaged(false);
    }

    /** Розміщує прямокутник над верхньою межею повернутого нейрона та зберігає його екранний розмір. */
    private void positionNameLabel(double labelWidth, double labelHeight, Pane overlayLayer) {
        double screenScale = sceneScale();
        if (overlayLayer != null && overlayLayer.getScene() != null) {
            Point2D[] corners = {
                    localToScene(0.0, 0.0),
                    localToScene(WIDTH, 0.0),
                    localToScene(0.0, HEIGHT),
                    localToScene(WIDTH, HEIGHT)
            };
            double topY = java.util.Arrays.stream(corners).mapToDouble(Point2D::getY).min().orElseThrow();
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

    /** Визначає масштаб координат сцени відносно локальних координат нейрона. */
    private double sceneScale() {
        Point2D center = localToScene(WIDTH / 2.0, HEIGHT / 2.0);
        Point2D sample = localToScene(WIDTH / 2.0 + 1.0, HEIGHT / 2.0);
        double scale = center.distance(sample);
        return Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
    }

    /** Повертає сформований текст підказки для перевірки формату в автоматизованих тестах. */
    String parameterTooltipTextForTest() { return parameterTooltip.getText(); }

    /** Повертає факт встановлення підказки для перевірки поведінки вибраного нейрона. */
    boolean isParameterTooltipInstalledForTest() { return parameterTooltipInstalled; }

    /** Повертає вузол назви для тестів видимості та стилізації підпису. */
    Label nameLabelForTest() { return nameLabel; }

    /** Повертає ознаку відображення назви без потреби показувати справжнє вікно. */
    boolean isNameLabelVisibleForTest() { return nameLabel.isVisible(); }

    /** Перетворює точку з локальних координат нейрона на світові координати полотна. */
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

    /** Налаштовує текстову позначку вхідної суми або залишку активних тактів. */
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
