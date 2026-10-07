package com.example.neuronmap.view;

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

public final class ToolbarView {

    public static final double HEIGHT = 54.0;

    private final HBox root = new HBox();

    private final Button addExcitatoryButton =
            toolButton(
                    "Активуючий",
                    "Додати активуючий нейрон"
            );

    private final Button addInhibitoryButton =
            toolButton(
                    "Гальмуючий",
                    "Додати гальмуючий нейрон"
            );

    private final Button groupButton =
            toolButton(
                    "Групувати",
                    "Групувати вибрані нейрони"
            );

    private final Button ungroupButton =
            toolButton(
                    "Розгрупувати",
                    "Розгрупувати вибрані нейрони"
            );

    private final Button exitDeleteButton =
            toolButton(
                    "Вийти з видалення зв'язків",
                    "Вийти з режиму видалення зв'язків"
            );

    private final Button pauseResumeButton =
            iconButton(
                    "❚❚",
                    "Поставити передачу імпульсів на паузу"
            );

    private final Button stopSignalsButton =
            iconButton(
                    "■",
                    "Повністю зупинити передачу імпульсів"
            );

    private final Separator simulationSeparator = new Separator();
    private final Label speedLabel = new Label("мс/такт:");
    private final TextField speedField = new TextField();
    private final Label cameraCoordinatesLabel = new Label("X: 0, Y: 0");

    private final Consumer<String> speedChanged;
    private final EventHandler<MouseEvent> sceneMousePressedHandler =
            this::handleSceneMousePressed;
    private boolean committingSpeed;

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
        this.speedChanged = speedChanged;

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
        stopSignalsButton.getStyleClass().add(
                "simulation-icon-button-danger"
        );
        pauseResumeButton.setVisible(false);
        pauseResumeButton.setManaged(false);
        stopSignalsButton.setVisible(false);
        stopSignalsButton.setManaged(false);
        simulationSeparator.setVisible(false);
        simulationSeparator.setManaged(false);

        pauseResumeButton.setOnAction(
                event -> pauseResume.run()
        );
        stopSignalsButton.setOnAction(
                event -> stopSignals.run()
        );

        speedLabel.getStyleClass().add("toolbar-speed-label");
        speedField.getStyleClass().add("toolbar-speed-field");
        speedField.setPrefWidth(88.0);
        speedField.setText(formatSpeed(initialSpeedMillis));
        speedField.setPromptText("650");
        speedField.setAlignment(Pos.CENTER_LEFT);
        speedField.setStyle("-fx-alignment: CENTER-LEFT;");
        speedField.setTextFormatter(
                new TextFormatter<>(
                        numericSpeedFilter()
                )
        );

        speedField.setOnAction(event -> {
            commitSpeedEdit();
            event.consume();
        });

        speedField.focusedProperty().addListener(
                (observable, oldFocused, focused) -> {
                    if (!focused && !committingSpeed) {
                        commitSpeedEdit();
                    }
                }
        );

        root.sceneProperty().addListener(
                (observable, oldScene, newScene) -> {
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
                }
        );

        cameraCoordinatesLabel.getStyleClass().add("toolbar-hint");
        cameraCoordinatesLabel.setMinWidth(140.0);
        cameraCoordinatesLabel.setAlignment(Pos.CENTER_RIGHT);

        addExcitatoryButton.setOnAction(
                event -> addNeuron.accept(NeuronType.EXCITATORY)
        );
        addInhibitoryButton.setOnAction(
                event -> addNeuron.accept(NeuronType.INHIBITORY)
        );
        groupButton.setOnAction(event -> group.run());
        ungroupButton.setOnAction(event -> ungroup.run());
        exitDeleteButton.setOnAction(event -> exitDelete.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        /*
         * Speed is deliberately placed before the dynamic simulation controls.
         * Showing/hiding pause/stop therefore never shifts the speed field.
         */
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
    }

    public HBox node() {
        return root;
    }

    public Button addExcitatoryButton() {
        return addExcitatoryButton;
    }

    public Button addInhibitoryButton() {
        return addInhibitoryButton;
    }

    public void setDeleteModeVisible(boolean visible) {
        exitDeleteButton.setVisible(visible);
        exitDeleteButton.setManaged(visible);
    }

    public void setSimulationControlsVisible(boolean visible) {
        simulationSeparator.setVisible(visible);
        simulationSeparator.setManaged(visible);
        pauseResumeButton.setVisible(visible);
        pauseResumeButton.setManaged(visible);
        stopSignalsButton.setVisible(visible);
        stopSignalsButton.setManaged(visible);
    }

    public void setSimulationPaused(boolean paused) {
        pauseResumeButton.setText(
                paused ? "▶" : "❚❚"
        );
        pauseResumeButton.setTooltip(
                new Tooltip(
                        paused
                                ? "Продовжити передачу імпульсів"
                                : "Поставити передачу імпульсів на паузу"
                )
        );
    }

    public void setSimulationSpeedMillis(double millis) {
        speedField.setText(formatSpeed(millis));
    }

    public void setCameraCoordinates(double x, double y) {
        cameraCoordinatesLabel.setText(
                "X: " + formatCoordinate(x)
                        + ", Y: " + formatCoordinate(y)
        );
    }

    private void handleSceneMousePressed(MouseEvent event) {
        if (!speedField.isFocused()) {
            return;
        }

        Node target = event.getTarget() instanceof Node node
                ? node
                : null;

        if (isDescendantOrSelf(target, speedField)) {
            return;
        }

        commitSpeedEdit();
    }

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

    private static UnaryOperator<TextFormatter.Change> numericSpeedFilter() {
        return change -> {
            String text = change.getControlNewText();

            if (text.isEmpty()) {
                return change;
            }

            return text.matches("\\d+(\\.\\d*)?")
                    ? change
                    : null;
        };
    }

    private static boolean isDescendantOrSelf(
            Node target,
            Node parent
    ) {
        Node current = target;

        while (current != null) {
            if (current == parent) {
                return true;
            }
            current = current.getParent();
        }

        return false;
    }

    private static Button toolButton(
            String text,
            String tooltipText
    ) {
        Button button = new Button(text);
        button.getStyleClass().add("tool-button");
        button.setTooltip(new Tooltip(tooltipText));
        return button;
    }

    private static Button iconButton(
            String text,
            String tooltipText
    ) {
        Button button = new Button(text);
        button.getStyleClass().add("tool-button");
        button.setTooltip(new Tooltip(tooltipText));
        return button;
    }

    private static String formatSpeed(double millis) {
        return Math.abs(millis - Math.rint(millis)) < 0.0001
                ? String.format(Locale.ROOT, "%.0f", millis)
                : String.format(Locale.ROOT, "%.1f", millis);
    }

    private static String formatCoordinate(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.format(
                    Locale.ROOT,
                    "%.0f",
                    value
            );
        }

        return String.format(
                Locale.ROOT,
                "%.1f",
                value
        );
    }
}
