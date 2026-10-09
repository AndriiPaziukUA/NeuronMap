package com.example.neuronmap.view;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronType;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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

/**
 * Створює панель інструментів із командами редактора та керуванням симуляцією.
 */
public final class ToolbarView {

    public static final double HEIGHT = 54.0;

    private final HBox root = new HBox();
    private final LocalizationService localization;

    private final Button addExcitatoryButton = toolButton();
    private final Button addInhibitoryButton = toolButton();
    private final Button groupButton = toolButton();
    private final Button ungroupButton = toolButton();
    private final Button exitDeleteButton = toolButton();
    private final Button pauseResumeButton = iconButton("❚❚");
    private final Button stopSignalsButton = iconButton("■");
    private final Separator simulationSeparator = new Separator();
    private final Label speedLabel = new Label();
    private final TextField speedField = new TextField();
    private final Label cameraCoordinatesLabel = new Label();

    private final Consumer<String> speedChanged;
    private final EventHandler<MouseEvent> sceneMousePressedHandler =
            this::handleSceneMousePressed;
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();
    private boolean committingSpeed;

    /**
     * Повертає результат операції «панель інструментів відображення».
     *
     * @param addNeuron значення, що визначає додати нейрон для цієї операції.
     *
     * @param group група нейронів.
     *
     * @param ungroup значення, що визначає відповідну операцію для цієї операції.
     *
     * @param exitDelete значення, що визначає видалити для цієї операції.
     *
     * @param pauseResume значення, що визначає відповідну операцію для цієї операції.
     *
     * @param stopSignals значення, що визначає зупинити сигнали для цієї операції.
     *
     * @param speedChanged значення, що визначає швидкість для цієї операції.
     *
     * @param initialSpeedMillis значення, що визначає швидкість для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public ToolbarView(
            Consumer<NeuronType> addNeuron,
            Runnable group,
            Runnable ungroup,
            Runnable exitDelete,
            Runnable pauseResume,
            Runnable stopSignals,
            Consumer<String> speedChanged,
            double initialSpeedMillis
    ) {
        this(
                addNeuron,
                group,
                ungroup,
                exitDelete,
                pauseResume,
                stopSignals,
                speedChanged,
                initialSpeedMillis,
                new LocalizationService(Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Повертає результат операції «панель інструментів відображення».
     *
     * @param addNeuron значення, що визначає додати нейрон для цієї операції.
     *
     * @param group група нейронів.
     *
     * @param ungroup значення, що визначає відповідну операцію для цієї операції.
     *
     * @param exitDelete значення, що визначає видалити для цієї операції.
     *
     * @param pauseResume значення, що визначає відповідну операцію для цієї операції.
     *
     * @param stopSignals значення, що визначає зупинити сигнали для цієї операції.
     *
     * @param speedChanged значення, що визначає швидкість для цієї операції.
     *
     * @param initialSpeedMillis значення, що визначає швидкість для цієї операції.
     *
     * @param localization значення, що визначає локалізація для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public ToolbarView(
            Consumer<NeuronType> addNeuron,
            Runnable group,
            Runnable ungroup,
            Runnable exitDelete,
            Runnable pauseResume,
            Runnable stopSignals,
            Consumer<String> speedChanged,
            double initialSpeedMillis,
            LocalizationService localization
    ) {
        this.speedChanged = speedChanged;
        this.localization = localization;

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

        exitDeleteButton.getStyleClass().add("tool-button-danger");
        exitDeleteButton.setVisible(false);
        exitDeleteButton.setManaged(false);

        pauseResumeButton.getStyleClass().add("simulation-icon-button");
        stopSignalsButton.getStyleClass().add("simulation-icon-button-danger");
        pauseResumeButton.setVisible(false);
        pauseResumeButton.setManaged(false);
        stopSignalsButton.setVisible(false);
        stopSignalsButton.setManaged(false);
        simulationSeparator.setVisible(false);
        simulationSeparator.setManaged(false);

        pauseResumeButton.setOnAction(event -> pauseResume.run());
        stopSignalsButton.setOnAction(event -> stopSignals.run());

        speedLabel.getStyleClass().add("toolbar-speed-label");
        speedField.getStyleClass().add("toolbar-speed-field");
        speedField.setPrefWidth(88.0);
        speedField.setText(formatSpeed(initialSpeedMillis));
        speedField.setAlignment(Pos.CENTER_LEFT);
        speedField.setStyle("-fx-alignment: CENTER-LEFT;");
        speedField.setTextFormatter(new TextFormatter<>(numericSpeedFilter()));
        speedField.setOnAction(event -> {
            commitSpeedEdit();
            event.consume();
        });
        speedField.focusedProperty().addListener((observable, oldFocused, focused) -> {
            if (!focused && !committingSpeed) {
                commitSpeedEdit();
            }
        });

        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(
                        MouseEvent.MOUSE_PRESSED,
                        sceneMousePressedHandler
                );
            }
            if (newScene != null) {
                newScene.addEventFilter(
                        MouseEvent.MOUSE_PRESSED,
                        sceneMousePressedHandler
                );
            }
        });

        cameraCoordinatesLabel.getStyleClass().add("toolbar-hint");
        cameraCoordinatesLabel.setMinWidth(140.0);
        cameraCoordinatesLabel.setAlignment(Pos.CENTER_RIGHT);

        addExcitatoryButton.setOnAction(event -> addNeuron.accept(NeuronType.EXCITATORY));
        addInhibitoryButton.setOnAction(event -> addNeuron.accept(NeuronType.INHIBITORY));
        groupButton.setOnAction(event -> group.run());
        ungroupButton.setOnAction(event -> ungroup.run());
        exitDeleteButton.setOnAction(event -> exitDelete.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        root.getChildren().addAll(
                title,
                addExcitatoryButton,
                addInhibitoryButton,
                new Separator(),
                groupButton,
                ungroupButton,
                exitDeleteButton,
                speedLabel,
                speedField,
                simulationSeparator,
                pauseResumeButton,
                stopSignalsButton,
                spacer,
                cameraCoordinatesLabel
        );

        localization.addListener(localizationListener);
        refreshTexts();
    }

    /**
     * Повертає результат операції «вузол».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public HBox node() {
        return root;
    }

    /**
     * Повертає результат операції «додати кнопка».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Button addExcitatoryButton() {
        return addExcitatoryButton;
    }

    /**
     * Повертає результат операції «додати кнопка».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Button addInhibitoryButton() {
        return addInhibitoryButton;
    }

    /**
     * Задає або оновлює значення, повʼязані з «видалити».
     *
     * @param visible ознака видимості елемента.
     */
    public void setDeleteModeVisible(boolean visible) {
        exitDeleteButton.setVisible(visible);
        exitDeleteButton.setManaged(visible);
    }

    /**
     * Задає або оновлює значення, повʼязані з «відповідну операцію».
     *
     * @param visible ознака видимості елемента.
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
     * Задає або оновлює значення, повʼязані з «відповідну операцію».
     *
     * @param paused значення, що визначає відповідну операцію для цієї операції.
     */
    public void setSimulationPaused(boolean paused) {
        pauseResumeButton.setText(paused ? "▶" : "❚❚");
        pauseResumeButton.setTooltip(new Tooltip(
                localization.text(
                        paused ? "toolbar.resume.tooltip" : "toolbar.pause.tooltip"
                )
        ));
    }

    /**
     * Задає або оновлює значення, повʼязані з «швидкість».
     *
     * @param millis значення, що визначає відповідну операцію для цієї операції.
     */
    public void setSimulationSpeedMillis(double millis) {
        speedField.setText(formatSpeed(millis));
    }

    /**
     * Задає або оновлює значення, повʼязані з «камера координати».
     *
     * @param x координата по горизонталі.
     *
     * @param y координата по вертикалі.
     */
    public void setCameraCoordinates(double x, double y) {
        cameraCoordinatesLabel.setText(
                "X: " + formatCoordinate(x)
                        + ", Y: " + formatCoordinate(y)
        );
    }

    /**
     * Обробляє «відповідну операцію».
     */
    private void refreshTexts() {
        addExcitatoryButton.setText(localization.text("toolbar.excitatory"));
        addExcitatoryButton.setTooltip(new Tooltip(localization.text("toolbar.excitatory.tooltip")));
        addInhibitoryButton.setText(localization.text("toolbar.inhibitory"));
        addInhibitoryButton.setTooltip(new Tooltip(localization.text("toolbar.inhibitory.tooltip")));
        groupButton.setText(localization.text("toolbar.group"));
        groupButton.setTooltip(new Tooltip(localization.text("toolbar.group.tooltip")));
        ungroupButton.setText(localization.text("toolbar.ungroup"));
        ungroupButton.setTooltip(new Tooltip(localization.text("toolbar.ungroup.tooltip")));
        exitDeleteButton.setText(localization.text("toolbar.exit_delete"));
        exitDeleteButton.setTooltip(new Tooltip(localization.text("toolbar.exit_delete.tooltip")));
        stopSignalsButton.setTooltip(new Tooltip(localization.text("toolbar.stop.tooltip")));
        speedLabel.setText(localization.text("toolbar.speed"));
        speedField.setPromptText(localization.text("toolbar.speed.prompt"));
        setSimulationPaused(pauseResumeButton.getText().equals("▶"));
    }

    /**
     * Обробляє «сцена».
     *
     * @param event подія інтерфейсу.
     */
    private void handleSceneMousePressed(MouseEvent event) {
        if (!speedField.isFocused()) {
            return;
        }
        Node target = event.getTarget() instanceof Node node ? node : null;
        if (isDescendantOrSelf(target, speedField)) {
            return;
        }
        commitSpeedEdit();
    }

    /**
     * Виконує операцію «швидкість».
     */
    private void commitSpeedEdit() {
        if (committingSpeed) {
            return;
        }
        committingSpeed = true;
        try {
            speedChanged.accept(speedField.getText());
            root.requestFocus();
        } finally {
            committingSpeed = false;
        }
    }

    /**
     * Повертає результат операції «швидкість».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Перевіряє, чи виконується умова «або».
     *
     * @param target значення, що визначає кінцевий для цієї операції.
     *
     * @param parent батьківський графічний вузол.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
     * Повертає результат операції «кнопка».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static Button toolButton() {
        Button button = new Button();
        button.getStyleClass().add("tool-button");
        return button;
    }

    /**
     * Повертає результат операції «кнопка».
     *
     * @param text текст, який потрібно показати або обробити.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static Button iconButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("tool-button");
        return button;
    }

    /**
     * Повертає результат операції «швидкість».
     *
     * @param millis значення, що визначає відповідну операцію для цієї операції.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    private static String formatSpeed(double millis) {
        return Math.abs(millis - Math.rint(millis)) < 0.0001
                ? String.format(Locale.ROOT, "%.0f", millis)
                : String.format(Locale.ROOT, "%.1f", millis);
    }

    /**
     * Повертає результат операції «координата».
     *
     * @param value значення, яке потрібно передати або зберегти.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    private static String formatCoordinate(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
