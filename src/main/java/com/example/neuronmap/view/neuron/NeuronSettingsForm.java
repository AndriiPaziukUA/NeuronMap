package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;

/** Володіє числовим введенням параметрів нейрона та їхньою валідацією перед збереженням. */
final class NeuronSettingsForm {

    private final Button saveButton;
    private final TextField signalField = createNumericField();
    private final TextField thresholdField = createNumericField();
    private final TextField activeTicksField = createNumericField();

    /** Створює форму та пов'язує валідність усіх полів із доступністю кнопки збереження. */
    NeuronSettingsForm(Button saveButton) {
        this.saveButton = Objects.requireNonNull(saveButton, "saveButton");
        signalField.textProperty().addListener((observable, oldValue, newValue) -> validateInput());
        thresholdField.textProperty().addListener((observable, oldValue, newValue) -> validateInput());
        activeTicksField.textProperty().addListener((observable, oldValue, newValue) -> validateInput());
        validateInput();
    }

    /** Завантажує параметри нейрона та встановлює курсор після кожного початкового значення. */
    void load(Neuron neuron) {
        Objects.requireNonNull(neuron, "neuron");
        signalField.setText(Integer.toString(neuron.signalStrength()));
        thresholdField.setText(Integer.toString(neuron.activationThreshold()));
        activeTicksField.setText(Integer.toString(neuron.activeTicks()));
        for (TextField field : fields()) {
            field.deselect();
            field.positionCaret(field.getLength());
        }
        validateInput();
    }

    /** Встановлює однакову локалізовану підказку формату для числових полів. */
    void setPromptText(String promptText) {
        for (TextField field : fields()) field.setPromptText(promptText);
    }

    /** Повертає поле введення сили сигналу. */
    TextField signalField() { return signalField; }

    /** Повертає поле введення порога активації. */
    TextField thresholdField() { return thresholdField; }

    /** Повертає поле введення кількості активних тактів. */
    TextField activeTicksField() { return activeTicksField; }

    /**
     * Повертає готові параметри, лише якщо всі поля містять додатні цілі числа в межах int.
     *
     * @return валідні параметри або порожній результат для невалідного введення.
     */
    Optional<NeuronParameters> parameters() {
        OptionalInt signalStrength = parsePositive(signalField.getText());
        OptionalInt activationThreshold = parsePositive(thresholdField.getText());
        OptionalInt activeTicks = parsePositive(activeTicksField.getText());
        if (signalStrength.isEmpty() || activationThreshold.isEmpty() || activeTicks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new NeuronParameters(
                signalStrength.getAsInt(), activationThreshold.getAsInt(), activeTicks.getAsInt()));
    }

    /** Оновлює доступність кнопки збереження відповідно до введення в усіх полях. */
    private void validateInput() {
        saveButton.setDisable(fields().stream().anyMatch(field -> parsePositive(field.getText()).isEmpty()));
    }

    /** Повертає поля у стабільному порядку для спільної валідації та налаштування. */
    private java.util.List<TextField> fields() {
        return java.util.List.of(signalField, thresholdField, activeTicksField);
    }

    /** Створює поле, яке відхиляє символи, що не є десятковими цифрами. */
    private static TextField createNumericField() {
        TextField field = new TextField();
        UnaryOperator<TextFormatter.Change> digitsOnly = change ->
                change.getControlNewText().matches("\\d*") ? change : null;
        field.setTextFormatter(new TextFormatter<>(digitsOnly));
        return field;
    }

    /** Перетворює текст на додатне ціле число в межах int. */
    private static OptionalInt parsePositive(String text) {
        if (text == null || text.isBlank()) return OptionalInt.empty();
        try {
            int value = Integer.parseInt(text);
            return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }
}
