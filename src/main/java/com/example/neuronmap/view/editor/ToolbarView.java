package com.example.neuronmap.view.editor;

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

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Створює панель інструментів редактора та керує її підписками на локалізацію і сцену. */
public final class ToolbarView {

    public static final double HEIGHT = 54.0;

    private final HBox root = new HBox();
    private final LocalizationService localization;
    private final Button addExcitatoryButton = toolButton();
    private final Button addInhibitoryButton = toolButton();
    private final Button groupButton = toolButton();
    private final Button ungroupButton = toolButton();
    private final Button pauseResumeButton = iconButton("❚❚");
    private final Button stopSignalsButton = iconButton("■");
    private final Separator simulationSeparator = new Separator();
    private final Label speedLabel = new Label();
    private final TextField speedField = new TextField();
    private final Label cameraCoordinatesLabel = new Label();
    private final Consumer<String> speedChanged;
    private double appliedSpeedMillis;
    private boolean committingSpeed;
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
     * Створює панель інструментів зі стандартною українською локалізацією.
     *
     * @param addNeuron дія створення нейрона вибраного типу.
     * @param group дія групування вибраних нейронів.
     * @param ungroup дія розгрупування вибраних нейронів.
     * @param pauseResume дія перемикання паузи симуляції.
     * @param stopSignals дія зупинки передачі сигналів.
     * @param speedChanged дія застосування введеної швидкості.
     * @param initialSpeedMillis початкова тривалість такту симуляції в мілісекундах.
     */
    public ToolbarView(
            Consumer<NeuronType> addNeuron,
            Runnable group,
            Runnable ungroup,
            Runnable pauseResume,
            Runnable stopSignals,
            Consumer<String> speedChanged,
            double initialSpeedMillis
    ) {
        this(addNeuron, group, ungroup, pauseResume, stopSignals, speedChanged, initialSpeedMillis,
                new LocalizationService(Locale.forLanguageTag("uk")));
    }

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
     * @param localization служба локалізації повідомлень інтерфейсу.
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
        this.speedChanged = speedChanged;
        this.localization = localization;
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

        Label title = new Label("NeuronMap");
        title.getStyleClass().add("toolbar-title");
        pauseResumeButton.getStyleClass().add("simulation-icon-button");
        stopSignalsButton.getStyleClass().add("simulation-icon-button-danger");
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
        speedField.setAlignment(Pos.CENTER_LEFT);
        speedField.setStyle("-fx-alignment: CENTER-LEFT;");
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

    /** Повертає кореневий вузол інтерфейсу. */
    public HBox node() {
        return root;
    }

    /** Повертає кнопку додавання збуджувального нейрона. */
    public Button addExcitatoryButton() {
        return addExcitatoryButton;
    }

    /** Повертає кнопку додавання гальмівного нейрона. */
    public Button addInhibitoryButton() {
        return addInhibitoryButton;
    }

    /** Відображає або приховує елементи керування активною симуляцією. */
    public void setSimulationControlsVisible(boolean visible) {
        simulationSeparator.setVisible(visible);
        simulationSeparator.setManaged(visible);
        pauseResumeButton.setVisible(visible);
        pauseResumeButton.setManaged(visible);
        stopSignalsButton.setVisible(visible);
        stopSignalsButton.setManaged(visible);
    }

    /** Оновлює вигляд елементів керування, щоб показати, чи симуляція призупинена. */
    public void setSimulationPaused(boolean paused) {
        pauseResumeButton.setText(paused ? "▶" : "❚❚");
        pauseResumeButton.setTooltip(new Tooltip(localization.text(
                paused ? "toolbar.resume.tooltip" : "toolbar.pause.tooltip")));
    }

    /** Установлює значення швидкості симуляції, яке показує панель інструментів. */
    public void setSimulationSpeedMillis(double millis) {
        appliedSpeedMillis = millis;
        speedField.setText(NumberDisplayFormatter.format(millis));
    }

    /** Установлює координати вказівника, а за відсутності скінченних координат очищує підпис. */
    public void setCameraCoordinates(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            cameraCoordinatesLabel.setText("");
            return;
        }
        cameraCoordinatesLabel.setText("X: " + NumberDisplayFormatter.format(x)
                + ", Y: " + NumberDisplayFormatter.format(y));
    }

    /** Від'єднує слухач локалізації та фільтр сцени, не зачіпаючи обробники, додані іншими власниками. */
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

    /** Під'єднує обробник кліків до поточної сцени, зберігаючи фактичного власника реєстрації. */
    private void handleSceneChanged(
            javafx.beans.value.ObservableValue<? extends Scene> observable,
            Scene oldScene,
            Scene newScene
    ) {
        detachSceneMouseFilter(sceneWithMouseFilter);
        sceneWithMouseFilter = null;
        attachSceneMouseFilter(newScene);
    }

    /** Додає обробник до сцени, якщо вона існує й панель ще не звільнена. */
    private void attachSceneMouseFilter(Scene scene) {
        if (disposed || scene == null || scene == sceneWithMouseFilter) {
            return;
        }
        detachSceneMouseFilter(sceneWithMouseFilter);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, sceneMousePressedHandler);
        sceneWithMouseFilter = scene;
    }

    /** Знімає обробник зі сцени, де він фактично був зареєстрований. */
    private void detachSceneMouseFilter(Scene scene) {
        if (scene != null) {
            scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, sceneMousePressedHandler);
        }
    }

    /** Оновлює підписи панелі інструментів відповідно до поточної мови інтерфейсу. */
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
        stopSignalsButton.setTooltip(new Tooltip(localization.text("toolbar.stop.tooltip")));
        speedLabel.setText(localization.text("toolbar.speed"));
        speedField.setPromptText(localization.text("toolbar.speed.prompt"));
        setSimulationPaused(pauseResumeButton.getText().equals("▶"));
    }

    /** Завершує редагування швидкості, коли користувач клацає за межами текстового поля. */
    private void handleSceneMousePressed(MouseEvent event) {
        if (disposed || !speedField.isFocused()) {
            return;
        }
        Node target = event.getTarget() instanceof Node node ? node : null;
        if (!isDescendantOrSelf(target, speedField)) {
            commitSpeedEdit();
        }
    }

    /** Перевіряє введену тривалість такту й передає нове значення контролеру симуляції. */
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

    /** Перевіряє, чи відповідає введення вже застосованій швидкості без зайвого запису до сховища. */
    private boolean isAppliedSpeed(String text) {
        try {
            double enteredMillis = Double.parseDouble(text);
            return Double.isFinite(enteredMillis) && Double.compare(enteredMillis, appliedSpeedMillis) == 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /** Створює фільтр текстового поля, який дозволяє вводити лише число допустимого формату. */
    private static UnaryOperator<TextFormatter.Change> numericSpeedFilter() {
        return change -> {
            String text = change.getControlNewText();
            if (text.isEmpty()) {
                return change;
            }
            return text.matches("\\d+(\\.\\d*)?") ? change : null;
        };
    }

    /** Перевіряє, чи є вузол ціллю або нащадком заданого вузла. */
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

    /** Створює кнопку інструмента панелі редактора зі спільним оформленням. */
    private static Button toolButton() {
        Button button = new Button();
        button.getStyleClass().add("tool-button");
        return button;
    }

    /** Створює кнопку панелі інструментів із заданим текстом та значком. */
    private static Button iconButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("tool-button");
        return button;
    }
}
