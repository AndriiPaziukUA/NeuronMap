package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;

/** Володіє введенням назви й числових параметрів нейрона та перевіряє їх перед збереженням. */
final class NeuronSettingsForm {

    private final Button saveButton;
    private final TextField nameField = createNameField();
    private final TextField signalField = createNumericField();
    private final TextField thresholdField = createNumericField();
    private final TextField activeTicksField = createNumericField();
    private final TextField delayTicksField = createNumericField();

    /**
     * Створює форму та пов'язує валідність числових полів із доступністю кнопки збереження.
     *
     * @param saveButton кнопка збереження, доступністю якої керує форма.
     * @throws NullPointerException якщо кнопку не задано.
     */
    NeuronSettingsForm(Button saveButton) {
        this.saveButton = Objects.requireNonNull(saveButton, "saveButton");
        signalField.textProperty().addListener((_, _, _) -> validateInput());
        thresholdField.textProperty().addListener((_, _, _) -> validateInput());
        activeTicksField.textProperty().addListener((_, _, _) -> validateInput());
        delayTicksField.textProperty().addListener((_, _, _) -> validateInput());
        validateInput();
    }

    /** Завантажує назву й параметри нейрона та розміщує курсори після початкових значень. */
    void load(Neuron neuron) {
        Objects.requireNonNull(neuron, "neuron");
        nameField.setText(neuron.name() == null ? "" : neuron.name());
        signalField.setText(Integer.toString(neuron.signalStrength()));
        thresholdField.setText(Integer.toString(neuron.activationThreshold()));
        activeTicksField.setText(Integer.toString(neuron.activeTicks()));
        delayTicksField.setText(Integer.toString(neuron.delayTicks()));
        for (TextField field : fields()) {
            field.deselect();
            field.positionCaret(field.getLength());
        }
        nameField.deselect();
        nameField.positionCaret(nameField.getLength());
        validateInput();
    }

    /** Установлює однакову локалізовану підказку формату для числових полів. */
    void setPromptText(String promptText) {
        for (TextField field : fields()) {
            field.setPromptText(promptText);
        }
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

    /** Повертає поле введення порога активації. */
    TextField thresholdField() { return thresholdField; }

    /** Повертає поле введення кількості активних тактів. */
    TextField activeTicksField() { return activeTicksField; }

    /** Повертає поле введення затримки перед першим вихідним сигналом. */
    TextField delayTicksField() { return delayTicksField; }

    /** Повертає параметри лише тоді, коли всі числові поля містять додатні цілі числа в межах {@code int}. */
    Optional<NeuronParameters> parameters() {
        OptionalInt signalStrength = parsePositive(signalField.getText());
        OptionalInt activationThreshold = parsePositive(thresholdField.getText());
        OptionalInt activeTicks = parsePositive(activeTicksField.getText());
        OptionalInt delayTicks = parsePositive(delayTicksField.getText());
        if (signalStrength.isEmpty() || activationThreshold.isEmpty()
                || activeTicks.isEmpty() || delayTicks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new NeuronParameters(
                signalStrength.getAsInt(),
                activationThreshold.getAsInt(),
                activeTicks.getAsInt(),
                delayTicks.getAsInt()
        ));
    }

    /** Оновлює доступність кнопки збереження відповідно до валідності всіх числових полів. */
    private void validateInput() {
        saveButton.setDisable(fields().stream().anyMatch(field -> parsePositive(field.getText()).isEmpty()));
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
}
