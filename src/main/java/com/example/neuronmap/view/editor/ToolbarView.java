package com.example.neuronmap.view.editor;

import com.example.neuronmap.ApplicationMetadata;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.util.NumberDisplayFormatter;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Створює панель інструментів редактора та керує її підписками на локалізацію і сцену.
 */
public final class ToolbarView {

    public static final double HEIGHT = 54.0;

    private static final String PAUSE_ICON_PATH = "M 0 0 H 4 V 14 H 0 Z M 6 0 H 10 V 14 H 6 Z";
    private static final String RESUME_ICON_PATH = "M 0 0 L 13 7 L 0 14 Z";
    private static final String STOP_ICON_PATH = "M 1 1 H 12 V 12 H 1 Z";

    private final HBox root = new HBox();
    private final LocalizationService localization;
    private final Button addExcitatoryButton = toolButton();
    private final Button addInhibitoryButton = toolButton();
    private final Button groupButton = toolButton();
    private final Button ungroupButton = toolButton();
    private final Button pauseResumeButton = iconButton();
    private final SVGPath simulationIcon = createSimulationIcon();
    private final Button stopSignalsButton = iconButton();
    private final SVGPath stopSignalsIcon = createStopSignalsIcon();
    private final Separator simulationSeparator = new Separator();
    private final Label speedLabel = new Label();
    private final TextField speedField = new TextField();
    private final Label cameraCoordinatesLabel = new Label();
    private final Consumer<String> speedChanged;
    private double appliedSpeedMillis;
    private boolean committingSpeed;
    private boolean simulationPaused;
    private boolean disposed;

    private final EventHandler<MouseEvent> sceneMousePressedHandler = this::handleSceneMousePressed;
    private final EventHandler<ActionEvent> speedActionHandler = event -> {
        commitSpeedEdit();
        event.consume();
    };
    private final ChangeListener<Boolean> speedFocusListener = (observable, oldFocused, focused) -> {
        if (!focused && !committingSpeed && !disposed) {
            commitSpeedEdit();
        }
    };
    private final ChangeListener<Scene> sceneChangeListener = this::handleSceneChanged;
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();
    private final EventHandler<ActionEvent> addExcitatoryHandler;
    private final EventHandler<ActionEvent> addInhibitoryHandler;
    private final EventHandler<ActionEvent> groupHandler;
    private final EventHandler<ActionEvent> ungroupHandler;
    private final EventHandler<ActionEvent> pauseResumeHandler;
    private final EventHandler<ActionEvent> stopSignalsHandler;

    private Scene sceneWithMouseFilter;

    /**
     * Створює панель інструментів зі спільною службою локалізації застосунку.
     *
     * @param addNeuron дія створення нейрона вибраного типу.
     * @param group дія групування вибраних нейронів.
     * @param ungroup дія розгрупування вибраних нейронів.
     * @param pauseResume дія перемикання паузи симуляції.
     * @param stopSignals дія зупинки передачі сигналів.
     * @param speedChanged дія застосування введеної швидкості.
     * @param initialSpeedMillis початкова тривалість такту симуляції в мілісекундах.
     * @param localization обов'язкова спільна служба локалізації повідомлень інтерфейсу.
     * @throws NullPointerException якщо локалізацію не передано.
     */
    public ToolbarView(
            Consumer<NeuronType> addNeuron,
            Runnable group,
            Runnable ungroup,
            Runnable pauseResume,
            Runnable stopSignals,
            Consumer<String> speedChanged,
            double initialSpeedMillis,
            LocalizationService localization
    ) {
        this.localization = Objects.requireNonNull(localization, "localization");
        this.speedChanged = Objects.requireNonNull(speedChanged, "speedChanged");
        this.addExcitatoryHandler = event -> addNeuron.accept(NeuronType.EXCITATORY);
        this.addInhibitoryHandler = event -> addNeuron.accept(NeuronType.INHIBITORY);
        this.groupHandler = event -> group.run();
        this.ungroupHandler = event -> ungroup.run();
        this.pauseResumeHandler = event -> pauseResume.run();
        this.stopSignalsHandler = event -> stopSignals.run();
        root.getStyleClass().add("topbar");
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(8, 10, 8, 10));
        root.setSpacing(8);
        root.setMinHeight(HEIGHT);
        root.setPrefHeight(HEIGHT);
        root.setMaxHeight(HEIGHT);
        root.setFocusTraversable(true);

        Label title = new Label(ApplicationMetadata.NAME);
        title.getStyleClass().add("toolbar-title");
        pauseResumeButton.getStyleClass().add("simulation-icon-button");
        pauseResumeButton.setGraphic(simulationIcon);
        stopSignalsButton.getStyleClass().add("simulation-icon-button-danger");
        stopSignalsButton.setGraphic(stopSignalsIcon);
        pauseResumeButton.setVisible(false);
        pauseResumeButton.setManaged(false);
        stopSignalsButton.setVisible(false);
        stopSignalsButton.setManaged(false);
        simulationSeparator.setVisible(false);
        simulationSeparator.setManaged(false);
        pauseResumeButton.addEventHandler(ActionEvent.ACTION, pauseResumeHandler);
        stopSignalsButton.addEventHandler(ActionEvent.ACTION, stopSignalsHandler);

        speedLabel.getStyleClass().add("toolbar-speed-label");
        speedField.getStyleClass().add("toolbar-speed-field");
        speedField.setPrefWidth(88.0);
        speedField.setText(NumberDisplayFormatter.format(initialSpeedMillis));
        appliedSpeedMillis = initialSpeedMillis;
        speedField.setTextFormatter(new TextFormatter<>(numericSpeedFilter()));
        speedField.addEventHandler(ActionEvent.ACTION, speedActionHandler);
        speedField.focusedProperty().addListener(speedFocusListener);
        root.sceneProperty().addListener(sceneChangeListener);
        attachSceneMouseFilter(root.getScene());

        cameraCoordinatesLabel.getStyleClass().add("toolbar-hint");
        cameraCoordinatesLabel.setMinWidth(140.0);
        cameraCoordinatesLabel.setAlignment(Pos.CENTER_RIGHT);
        addExcitatoryButton.addEventHandler(ActionEvent.ACTION, addExcitatoryHandler);
        addInhibitoryButton.addEventHandler(ActionEvent.ACTION, addInhibitoryHandler);
        groupButton.addEventHandler(ActionEvent.ACTION, groupHandler);
        ungroupButton.addEventHandler(ActionEvent.ACTION, ungroupHandler);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        root.getChildren().addAll(
                title, addExcitatoryButton, addInhibitoryButton, new Separator(),
                groupButton, ungroupButton, speedLabel, speedField, simulationSeparator,
                pauseResumeButton, stopSignalsButton, spacer, cameraCoordinatesLabel);
        localization.addListener(localizationListener);
        refreshTexts();
    }

    /**
     * Повертає кореневий вузол інтерфейсу.
     *
     * @return коренева панель інструментів.
     */
    public HBox node() {
        return root;
    }

    /**
     * Повертає кнопку додавання збуджувального нейрона.
     *
     * @return кнопка створення збуджувального нейрона.
     */
    public Button addExcitatoryButton() {
        return addExcitatoryButton;
    }

    /**
     * Повертає кнопку додавання гальмівного нейрона.
     *
     * @return кнопка створення гальмівного нейрона.
     */
    public Button addInhibitoryButton() {
        return addInhibitoryButton;
    }

    /**
     * Відображає або приховує елементи керування активною симуляцією.
     *
     * @param visible чи потрібно показати елементи.
     */
    public void setSimulationControlsVisible(boolean visible) {
        simulationSeparator.setVisible(visible);
        simulationSeparator.setManaged(visible);
        pauseResumeButton.setVisible(visible);
        pauseResumeButton.setManaged(visible);
        stopSignalsButton.setVisible(visible);
        stopSignalsButton.setManaged(visible);
    }

    /**
     * Оновлює векторний значок і підказку відповідно до стану паузи симуляції.
     *
     * @param paused чи призупинено симуляцію.
     */
    public void setSimulationPaused(boolean paused) {
        simulationPaused = paused;
        simulationIcon.setContent(paused ? RESUME_ICON_PATH : PAUSE_ICON_PATH);
        String tooltipText = localization.text(
                paused ? "toolbar.resume.tooltip" : "toolbar.pause.tooltip");
        pauseResumeButton.setTooltip(new Tooltip(tooltipText));
        pauseResumeButton.setAccessibleText(tooltipText);
    }

    /**
     * Установлює значення швидкості симуляції, яке показує панель інструментів.
     *
     * @param millis тривалість такту в мілісекундах.
     */
    public void setSimulationSpeedMillis(double millis) {
        appliedSpeedMillis = millis;
        speedField.setText(NumberDisplayFormatter.format(millis));
    }

    /**
     * Установлює координати вказівника або очищує підпис, якщо координати не скінченні.
     *
     * @param x горизонтальна координата.
     * @param y вертикальна координата.
     */
    public void setCameraCoordinates(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            cameraCoordinatesLabel.setText("");
            return;
        }
        cameraCoordinatesLabel.setText("X: " + NumberDisplayFormatter.format(x)
                + ", Y: " + NumberDisplayFormatter.format(y));
    }

    /**
     * Від'єднує власні слухачі та фільтр сцени; повторний виклик безпечний.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        localization.removeListener(localizationListener);
        root.sceneProperty().removeListener(sceneChangeListener);
        detachSceneMouseFilter(sceneWithMouseFilter);
        sceneWithMouseFilter = null;
        speedField.removeEventHandler(ActionEvent.ACTION, speedActionHandler);
        speedField.focusedProperty().removeListener(speedFocusListener);
        addExcitatoryButton.removeEventHandler(ActionEvent.ACTION, addExcitatoryHandler);
        addInhibitoryButton.removeEventHandler(ActionEvent.ACTION, addInhibitoryHandler);
        groupButton.removeEventHandler(ActionEvent.ACTION, groupHandler);
        ungroupButton.removeEventHandler(ActionEvent.ACTION, ungroupHandler);
        pauseResumeButton.removeEventHandler(ActionEvent.ACTION, pauseResumeHandler);
        stopSignalsButton.removeEventHandler(ActionEvent.ACTION, stopSignalsHandler);
    }

    /**
     * Під'єднує фільтр кліків до нової сцени й від'єднує його від попередньої.
     *
     * @param observable властивість сцени.
     * @param oldScene попередня сцена.
     * @param newScene нова сцена.
     */
    private void handleSceneChanged(
            javafx.beans.value.ObservableValue<? extends Scene> observable,
            Scene oldScene,
            Scene newScene
    ) {
        detachSceneMouseFilter(sceneWithMouseFilter);
        sceneWithMouseFilter = null;
        attachSceneMouseFilter(newScene);
    }

    /**
     * Додає фільтр до сцени, якщо панель ще не звільнена.
     *
     * @param scene сцена, на яку додається фільтр.
     */
    private void attachSceneMouseFilter(Scene scene) {
        if (disposed || scene == null || scene == sceneWithMouseFilter) {
            return;
        }
        detachSceneMouseFilter(sceneWithMouseFilter);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, sceneMousePressedHandler);
        sceneWithMouseFilter = scene;
    }

    /**
     * Знімає фільтр із тієї сцени, де його фактично зареєстровано.
     *
     * @param scene сцена для очищення.
     */
    private void detachSceneMouseFilter(Scene scene) {
        if (scene != null) {
            scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, sceneMousePressedHandler);
        }
    }

    /**
     * Оновлює написи панелі інструментів відповідно до поточної мови.
     */
    private void refreshTexts() {
        if (disposed) {
            return;
        }
        addExcitatoryButton.setText(localization.text("toolbar.excitatory"));
        addExcitatoryButton.setTooltip(new Tooltip(localization.text("toolbar.excitatory.tooltip")));
        addInhibitoryButton.setText(localization.text("toolbar.inhibitory"));
        addInhibitoryButton.setTooltip(new Tooltip(localization.text("toolbar.inhibitory.tooltip")));
        groupButton.setText(localization.text("toolbar.group"));
        groupButton.setTooltip(new Tooltip(localization.text("toolbar.group.tooltip")));
        ungroupButton.setText(localization.text("toolbar.ungroup"));
        ungroupButton.setTooltip(new Tooltip(localization.text("toolbar.ungroup.tooltip")));
        String stopTooltipText = localization.text("toolbar.stop.tooltip");
        stopSignalsButton.setTooltip(new Tooltip(stopTooltipText));
        stopSignalsButton.setAccessibleText(stopTooltipText);
        speedLabel.setText(localization.text("toolbar.speed"));
        speedField.setPromptText(localization.text("toolbar.speed.prompt"));
        setSimulationPaused(simulationPaused);
    }

    /**
     * Завершує редагування швидкості, якщо користувач натиснув поза текстовим полем.
     *
     * @param event подія натискання миші.
     */
    private void handleSceneMousePressed(MouseEvent event) {
        if (disposed || !speedField.isFocused()) {
            return;
        }
        Node target = event.getTarget() instanceof Node node ? node : null;
        if (!isDescendantOrSelf(target, speedField)) {
            commitSpeedEdit();
        }
    }

    /**
     * Перевіряє введену тривалість такту та передає нове значення контролеру.
     */
    private void commitSpeedEdit() {
        if (committingSpeed || disposed) {
            return;
        }
        committingSpeed = true;
        try {
            String enteredText = speedField.getText();
            if (isAppliedSpeed(enteredText)) {
                speedField.setText(NumberDisplayFormatter.format(appliedSpeedMillis));
            } else {
                speedChanged.accept(enteredText);
            }
            root.requestFocus();
        } finally {
            committingSpeed = false;
        }
    }

    /**
     * Перевіряє, чи дорівнює введення вже застосованій швидкості.
     *
     * @param text введене значення.
     * @return {@code true}, якщо значення вже застосовано.
     */
    private boolean isAppliedSpeed(String text) {
        try {
            double enteredMillis = Double.parseDouble(text);
            return Double.isFinite(enteredMillis) && Double.compare(enteredMillis, appliedSpeedMillis) == 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /**
     * Створює фільтр поля швидкості, який пропускає лише число допустимого формату.
     *
     * @return фільтр введення.
     */
    private static UnaryOperator<TextFormatter.Change> numericSpeedFilter() {
        return change -> {
            String text = change.getControlNewText();
            if (text.isEmpty()) {
                return change;
            }
            return text.matches("\\d+(\\.\\d*)?") ? change : null;
        };
    }

    /**
     * Перевіряє, чи є вузол ціллю або нащадком заданого вузла.
     *
     * @param target вузол для перевірки.
     * @param parent шуканий предок.
     * @return {@code true}, якщо вузол збігається або є нащадком.
     */
    private static boolean isDescendantOrSelf(Node target, Node parent) {
        Node current = target;
        while (current != null) {
            if (current == parent) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Створює кнопку інструмента панелі редактора.
     *
     * @return кнопка з базовим CSS-класом інструмента.
     */
    private static Button toolButton() {
        Button button = new Button();
        button.getStyleClass().add("tool-button");
        return button;
    }

    /**
     * Створює кнопку панелі інструментів, графічний значок якої задається окремо.
     *
     * @return кнопка з базовим CSS-класом інструмента та без текстового символу.
     */
    private static Button iconButton() {
        Button button = new Button();
        button.setText("");
        button.getStyleClass().add("tool-button");
        return button;
    }

    /**
     * Створює векторний значок керування симуляцією, геометрію якого можна точно налаштувати через SVG-шлях.
     *
     * @return значок з окремим CSS-класом для застосування кольору.
     */
    private static SVGPath createSimulationIcon() {
        SVGPath icon = new SVGPath();
        icon.getStyleClass().add("simulation-icon");
        icon.setContent(PAUSE_ICON_PATH);
        return icon;
    }

    /**
     * Створює векторний квадрат для кнопки зупинки сигналів.
     *
     * @return векторний значок зупинки з окремим CSS-класом кольору.
     */
    private static SVGPath createStopSignalsIcon() {
        SVGPath icon = new SVGPath();
        icon.getStyleClass().add("simulation-stop-icon");
        icon.setContent(STOP_ICON_PATH);
        return icon;
    }
}
