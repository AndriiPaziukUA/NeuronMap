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
        validateInput();
    }

    /**
     * Завантажує назву й параметри нейрона та встановлює курсори після початкових значень.
     *
     * @param neuron нейрон, значення якого потрібно показати у формі.
     * @throws NullPointerException якщо нейрон не задано.
     */
    void load(Neuron neuron) {
        Objects.requireNonNull(neuron, "neuron");
        nameField.setText(neuron.name() == null ? "" : neuron.name());
        signalField.setText(Integer.toString(neuron.signalStrength()));
        thresholdField.setText(Integer.toString(neuron.activationThreshold()));
        activeTicksField.setText(Integer.toString(neuron.activeTicks()));
        for (TextField field : fields()) {
            field.deselect();
            field.positionCaret(field.getLength());
        }
        nameField.deselect();
        nameField.positionCaret(nameField.getLength());
        validateInput();
    }

    /**
     * Встановлює однакову локалізовану підказку формату для числових полів.
     *
     * @param promptText текст підказки числових полів.
     */
    void setPromptText(String promptText) {
        for (TextField field : fields()) {
            field.setPromptText(promptText);
        }
    }

    /** Повертає поле назви, яке дозволяє довільні символи в межах установленого ліміту. */
    TextField nameField() {
        return nameField;
    }

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

    /**
     * Повертає готові параметри, лише якщо всі числові поля містять додатні цілі числа в межах int.
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

    /** Оновлює доступність кнопки збереження відповідно до введення в усіх числових полях. */
    private void validateInput() {
        saveButton.setDisable(fields().stream().anyMatch(field -> parsePositive(field.getText()).isEmpty()));
    }

    /** Повертає числові поля у стабільному порядку для спільної валідації та налаштування. */
    private List<TextField> fields() {
        return List.of(signalField, thresholdField, activeTicksField);
    }

    /**
     * Створює поле назви, яке не обмежує набір символів, але забороняє перевищувати дозволену довжину.
     *
     * @return налаштоване поле назви нейрона.
     */
    private static TextField createNameField() {
        TextField field = new TextField();
        UnaryOperator<TextFormatter.Change> lengthLimit = change ->
                change.getControlNewText().codePointCount(0, change.getControlNewText().length()) <= Neuron.MAX_NAME_LENGTH ? change : null;
        field.setTextFormatter(new TextFormatter<>(lengthLimit));
        field.setPrefColumnCount(10);
        return field;
    }

    /**
     * Створює числове поле, яке відхиляє символи, що не є десятковими цифрами.
     *
     * @return налаштоване поле числового параметра.
     */
    private static TextField createNumericField() {
        TextField field = new TextField();
        UnaryOperator<TextFormatter.Change> digitsOnly = change ->
                change.getControlNewText().matches("\\d*") ? change : null;
        field.setTextFormatter(new TextFormatter<>(digitsOnly));
        return field;
    }

    /**
     * Перетворює текст на додатне ціле число в межах int.
     *
     * @param text текст, що перевіряється.
     * @return додатне число або порожній результат, якщо ввід некоректний.
     */
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
