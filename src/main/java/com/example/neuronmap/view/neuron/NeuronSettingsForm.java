package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronParameters;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;

/** Володіє введенням параметрів нейрона, вибором режиму активації та перевіркою значень перед збереженням. */
final class NeuronSettingsForm {

    private final Button saveButton;
    private final LocalizationService localization;
    private final TextField nameField = createNameField();
    private final TextField signalField = createNumericField();
    private final TextField thresholdField = createNumericField();
    private final TextField activeTicksField = createNumericField();
    private final TextField delayTicksField = createNumericField();
    private final ComboBox<NeuronActivationMode> activationModeSelector = new ComboBox<>();
    private boolean updatingSelection;
    private String positiveIntegerPrompt = "";
    private String percentagePrompt = "";

    /**
     * Створює форму, налаштовує локалізований список режимів і валідацію числових полів.
     *
     * @param saveButton кнопка збереження, доступністю якої керує форма.
     * @param localization спільна служба локалізації застосунку.
     * @throws NullPointerException якщо кнопку або службу локалізації не задано.
     */
    NeuronSettingsForm(Button saveButton, LocalizationService localization) {
        this.saveButton = Objects.requireNonNull(saveButton, "saveButton");
        this.localization = Objects.requireNonNull(localization, "localization");
        signalField.textProperty().addListener((_, _, _) -> validateInput());
        thresholdField.textProperty().addListener((_, _, _) -> validateInput());
        activeTicksField.textProperty().addListener((_, _, _) -> validateInput());
        delayTicksField.textProperty().addListener((_, _, _) -> validateInput());
        configureActivationModeSelector();
        validateInput();
    }

    /** Завантажує параметри нейрона й обраний режим, не змінюючи саму модель до підтвердження форми. */
    void load(Neuron neuron) {
        Objects.requireNonNull(neuron, "neuron");
        nameField.setText(neuron.name() == null ? "" : neuron.name());
        signalField.setText(Integer.toString(neuron.signalStrength()));
        thresholdField.setText(Integer.toString(neuron.activationThreshold()));
        activeTicksField.setText(Integer.toString(neuron.activeTicks()));
        delayTicksField.setText(Integer.toString(neuron.delayTicks()));
        updatingSelection = true;
        try {
            activationModeSelector.setValue(neuron.activationMode());
        } finally {
            updatingSelection = false;
        }
        for (TextField field : fields()) {
            field.deselect();
            field.positionCaret(field.getLength());
        }
        nameField.deselect();
        nameField.positionCaret(nameField.getLength());
        updateThresholdPrompt();
        validateInput();
    }

    /** Установлює підказки числових полів відповідно до локалі та вибраного режиму активації. */
    void setPromptText(String positiveIntegerPrompt, String percentagePrompt) {
        this.positiveIntegerPrompt = Objects.requireNonNull(positiveIntegerPrompt, "positiveIntegerPrompt");
        this.percentagePrompt = Objects.requireNonNull(percentagePrompt, "percentagePrompt");
        for (TextField field : fields()) {
            if (field != thresholdField) {
                field.setPromptText(this.positiveIntegerPrompt);
            }
        }
        updateThresholdPrompt();
    }

    /** Оновлює назви режимів у кнопці й випадаючому списку після зміни локалі. */
    void refreshTexts() {
        NeuronActivationMode selectedMode = activationModeSelector.getValue();
        updatingSelection = true;
        try {
            activationModeSelector.getItems().setAll(NeuronActivationMode.values());
            activationModeSelector.setValue(selectedMode == null
                    ? NeuronActivationMode.SIGNAL_STRENGTH
                    : selectedMode);
        } finally {
            updatingSelection = false;
        }
        updateThresholdPrompt();
        validateInput();
    }

    /** Повертає поле назви, яке дозволяє довільні символи в межах установленого ліміту. */
    TextField nameField() { return nameField; }

    /** Повертає введену назву або {@code null}, якщо поле порожнє. */
    String name() {
        String value = nameField.getText();
        return value == null || value.isEmpty() ? null : value;
    }

    /** Повертає поле введення сили сигналу. */
    TextField signalField() { return signalField; }

    /** Повертає поле введення порога активації або відсотка. */
    TextField thresholdField() { return thresholdField; }

    /** Повертає поле введення кількості активних тактів. */
    TextField activeTicksField() { return activeTicksField; }

    /** Повертає поле введення затримки перед першим вихідним сигналом. */
    TextField delayTicksField() { return delayTicksField; }

    /** Повертає список вибору способу активації нейрона. */
    ComboBox<NeuronActivationMode> activationModeSelector() { return activationModeSelector; }

    /**
     * Повертає перевірений набір параметрів лише тоді, коли кожне поле відповідає обраному режиму.
     *
     * @return параметри для збереження або порожнє значення, якщо введення некоректне.
     */
    Optional<NeuronParameters> parameters() {
        OptionalInt signalStrength = parsePositive(signalField.getText());
        OptionalInt activationThreshold = parseThreshold(
                thresholdField.getText(), activationModeSelector.getValue());
        OptionalInt activeTicks = parsePositive(activeTicksField.getText());
        OptionalInt delayTicks = parsePositive(delayTicksField.getText());
        NeuronActivationMode activationMode = activationModeSelector.getValue();
        if (signalStrength.isEmpty() || activationThreshold.isEmpty()
                || activeTicks.isEmpty() || delayTicks.isEmpty() || activationMode == null) {
            return Optional.empty();
        }
        return Optional.of(new NeuronParameters(
                signalStrength.getAsInt(),
                activationThreshold.getAsInt(),
                activeTicks.getAsInt(),
                delayTicks.getAsInt(),
                activationMode
        ));
    }

    /** Налаштовує ComboBox за тими самими правилами комірок та геометрії popup, що й список мови в меню. */
    private void configureActivationModeSelector() {
        activationModeSelector.getItems().setAll(NeuronActivationMode.values());
        activationModeSelector.setValue(NeuronActivationMode.SIGNAL_STRENGTH);
        activationModeSelector.setMaxWidth(Double.MAX_VALUE);
        activationModeSelector.setCellFactory(_ -> activationModeCell());
        activationModeSelector.setButtonCell(activationModeCell());
        activationModeSelector.setConverter(new StringConverter<>() {
            /** Повертає локалізовану назву вибраного способу активації. */
            @Override
            public String toString(NeuronActivationMode mode) {
                return mode == null ? "" : activationModeText(mode);
            }

            /** Текстове введення не перетворюється на значення переліку режимів. */
            @Override
            public NeuronActivationMode fromString(String value) {
                return null;
            }
        });
        activationModeSelector.setOnShowing(_ -> preparePopupGeometry());
        activationModeSelector.setOnAction(event -> {
            if (!updatingSelection && activationModeSelector.getValue() != null) {
                activationModeSelector.hide();
                updateThresholdPrompt();
                validateInput();
            }
            event.consume();
        });
    }

    /** Створює комірку списку, яка показує назву режиму відповідно до активної локалі. */
    private ListCell<NeuronActivationMode> activationModeCell() {
        return new ListCell<>() {
            /** Оновлює текст комірки для поточного елемента списку. */
            @Override
            protected void updateItem(NeuronActivationMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : activationModeText(item));
            }
        };
    }

    /** Повертає локалізовану назву конкретного способу активації. */
    private String activationModeText(NeuronActivationMode mode) {
        return localization.textFrom("i18n.neuronSettings", mode == NeuronActivationMode.SIGNAL_STRENGTH
                ? "activation_mode_strength"
                : "activation_mode_count");
    }

    /** Оновлює CSS і компонування перед розрахунком екранної позиції випадаючого списку. */
    private void preparePopupGeometry() {
        if (activationModeSelector.getScene() == null) {
            return;
        }
        Parent sceneRoot = activationModeSelector.getScene().getRoot();
        sceneRoot.applyCss();
        sceneRoot.layout();
    }

    /** Оновлює підказку порога відповідно до вибраного режиму активації. */
    private void updateThresholdPrompt() {
        NeuronActivationMode mode = activationModeSelector.getValue();
        thresholdField.setPromptText(mode == NeuronActivationMode.SIGNAL_COUNT
                ? percentagePrompt
                : positiveIntegerPrompt);
    }

    /** Оновлює доступність збереження відповідно до валідності режиму й усіх числових полів. */
    private void validateInput() {
        NeuronActivationMode mode = activationModeSelector.getValue();
        boolean invalidNumericInput = fields().stream().anyMatch(field ->
                field == thresholdField
                        ? parseThreshold(field.getText(), mode).isEmpty()
                        : parsePositive(field.getText()).isEmpty());
        saveButton.setDisable(mode == null || invalidNumericInput);
    }

    /** Повертає числові поля у стабільному порядку для спільної валідації та налаштування. */
    private List<TextField> fields() {
        return List.of(signalField, thresholdField, activeTicksField, delayTicksField);
    }

    /** Створює поле назви, яке не обмежує набір символів, але забороняє перевищувати дозволену довжину. */
    private static TextField createNameField() {
        TextField field = new TextField();
        UnaryOperator<TextFormatter.Change> lengthLimit = change ->
                change.getControlNewText().codePointCount(0, change.getControlNewText().length())
                        <= Neuron.MAX_NAME_LENGTH ? change : null;
        field.setTextFormatter(new TextFormatter<>(lengthLimit));
        field.setPrefColumnCount(10);
        return field;
    }

    /** Створює числове поле, яке відхиляє символи, що не є десятковими цифрами. */
    private static TextField createNumericField() {
        TextField field = new TextField();
        UnaryOperator<TextFormatter.Change> digitsOnly = change ->
                change.getControlNewText().matches("\\d*") ? change : null;
        field.setTextFormatter(new TextFormatter<>(digitsOnly));
        return field;
    }

    /** Перетворює текст на додатне ціле число в межах {@code int}. */
    private static OptionalInt parsePositive(String text) {
        if (text == null || text.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            int value = Integer.parseInt(text);
            return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    /** Перевіряє поріг як додатне число сили або цілий відсоток у межах 0–100. */
    private static OptionalInt parseThreshold(String text, NeuronActivationMode mode) {
        if (mode == null || mode == NeuronActivationMode.SIGNAL_STRENGTH) {
            return parsePositive(text);
        }
        if (text == null || text.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            int value = Integer.parseInt(text);
            return value >= 0 && value <= 100 ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }
}
