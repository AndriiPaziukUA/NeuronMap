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
 * Створює панель інструментів і керує кнопками нейронів, групуванням, симуляцією, швидкістю й відображенням координат камери.
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
     * Створює екземпляр ToolbarView та зберігає передані залежності, потрібні для його роботи.
     *
     * @param addNeuron значення «add neuron», яке використовується в цьому методі.
     * @param group значення «group», яке використовується в цьому методі.
     * @param ungroup значення «ungroup», яке використовується в цьому методі.
     * @param exitDelete callback, який завершує режим видалення зв’язку.
     * @param pauseResume значення «pause resume», яке використовується в цьому методі.
     * @param stopSignals значення «stop signals», яке використовується в цьому методі.
     * @param speedChanged значення «speed changed», яке використовується в цьому методі.
     * @param initialSpeedMillis значення «initial speed millis», яке використовується в цьому методі.
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
     * Створює екземпляр ToolbarView та зберігає передані залежності, потрібні для його роботи.
     *
     * @param addNeuron значення «add neuron», яке використовується в цьому методі.
     * @param group значення «group», яке використовується в цьому методі.
     * @param ungroup значення «ungroup», яке використовується в цьому методі.
     * @param exitDelete callback, який завершує режим видалення зв’язку.
     * @param pauseResume значення «pause resume», яке використовується в цьому методі.
     * @param stopSignals значення «stop signals», яке використовується в цьому методі.
     * @param speedChanged значення «speed changed», яке використовується в цьому методі.
     * @param initialSpeedMillis значення «initial speed millis», яке використовується в цьому методі.
     * @param localization служба локалізації інтерфейсу.
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
     * Повертає кореневий вузол інтерфейсу.
     *
     * @return кореневий вузол інтерфейсу.
     */
    public HBox node() {
        return root;
    }

    /**
     * Створює та додає кнопку інструмента для додавання збуджувального нейрона.
     */
    public Button addExcitatoryButton() {
        return addExcitatoryButton;
    }

    /**
     * Створює та додає кнопку інструмента для додавання гальмівного нейрона.
     */
    public Button addInhibitoryButton() {
        return addInhibitoryButton;
    }

    /**
     * Установлює delete mode visible для поточного об’єкта.
     *
     * @param visible значення «visible», яке використовується в цьому методі.
     */
    public void setDeleteModeVisible(boolean visible) {
        exitDeleteButton.setVisible(visible);
        exitDeleteButton.setManaged(visible);
    }

    /**
     * Установлює simulation controls visible для поточного об’єкта.
     *
     * @param visible значення «visible», яке використовується в цьому методі.
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
     * Оновлює вигляд елементів керування, щоб показати, чи симуляція призупинена.
     *
     * @param paused значення «paused», яке використовується в цьому методі.
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
     * Установлює значення швидкості симуляції, яке показує панель інструментів.
     *
     * @param millis тривалість такту в мілісекундах.
     */
    public void setSimulationSpeedMillis(double millis) {
        speedField.setText(formatSpeed(millis));
    }

    /**
     * Оновлює координати камери, показані на панелі інструментів.
     *
     * @param x координата X.
     * @param y координата Y.
     */
    public void setCameraCoordinates(double x, double y) {
        cameraCoordinatesLabel.setText(
                "X: " + formatCoordinate(x)
                        + ", Y: " + formatCoordinate(y)
        );
    }

    /**
     * Оновлює «texts» за поточним станом моделі або інтерфейсу.
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
     * Обробляє подію «scene mouse pressed» і передає її до відповідної операції редактора.
     *
     * @param event подія інтерфейсу, яку потрібно обробити.
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
     * Перевіряє введену тривалість такту й передає нове значення контролеру симуляції.
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
     * Створює фільтр текстового поля, який обмежує введення числом допустимого формату.
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
     * Перевіряє, чи descendant or self за поточного стану компонента.
     *
     * @param target цільовий вузол або об’єкт інтерфейсу, який потрібно перевірити чи знайти.
     * @param parent батьківський XML-елемент або вузол, у якому виконується пошук.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
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
     * Створює кнопку інструмента для панелі редактора зі спільним оформленням.
     */
    private static Button toolButton() {
        Button button = new Button();
        button.getStyleClass().add("tool-button");
        return button;
    }

    /**
     * Створює кнопку панелі інструментів із заданим текстом і значком.
     *
     * @param text текст, який потрібно показати або розібрати.
     */
    private static Button iconButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("tool-button");
        return button;
    }

    /**
     * Форматує тривалість такту для короткого відображення на панелі.
     *
     * @param millis тривалість такту в мілісекундах.
     */
    private static String formatSpeed(double millis) {
        return Math.abs(millis - Math.rint(millis)) < 0.0001
                ? String.format(Locale.ROOT, "%.0f", millis)
                : String.format(Locale.ROOT, "%.1f", millis);
    }

    /**
     * Форматує координату камери для відображення з обмеженою кількістю знаків.
     *
     * @param value значення, яке потрібно зберегти або перевірити.
     */
    private static String formatCoordinate(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
