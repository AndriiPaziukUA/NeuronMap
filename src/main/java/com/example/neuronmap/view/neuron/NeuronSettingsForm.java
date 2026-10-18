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

    /**
     * Створює форму та пов'язує валідність введення з доступністю кнопки збереження.
     *
     * @param saveButton кнопка, доступність якої залежить від валідності параметрів.
     * @throws NullPointerException якщо кнопку не задано.
     */
    NeuronSettingsForm(Button saveButton) {
        this.saveButton = Objects.requireNonNull(saveButton, "saveButton");
        signalField.textProperty().addListener((observable, oldValue, newValue) -> validateInput());
        thresholdField.textProperty().addListener((observable, oldValue, newValue) -> validateInput());
        validateInput();
    }

    /** Завантажує з моделі параметри нейрона та встановлює курсор після початкового значення. */
    void load(Neuron neuron) {
        Objects.requireNonNull(neuron, "neuron");
        signalField.setText(Integer.toString(neuron.signalStrength()));
        thresholdField.setText(Integer.toString(neuron.activationThreshold()));
        signalField.deselect();
        thresholdField.deselect();
        signalField.positionCaret(signalField.getLength());
        thresholdField.positionCaret(thresholdField.getLength());
        validateInput();
    }

    /** Встановлює однакову локалізовану підказку формату для обох числових полів. */
    void setPromptText(String promptText) {
        signalField.setPromptText(promptText);
        thresholdField.setPromptText(promptText);
    }

    /** Повертає поле введення сили сигналу для компонування у відповідному рядку. */
    TextField signalField() {
        return signalField;
    }

    /** Повертає поле введення порога активації для компонування у відповідному рядку. */
    TextField thresholdField() {
        return thresholdField;
    }

    /**
     * Повертає готові параметри лише тоді, коли обидва поля містять додатні цілі числа в межах int.
     *
     * @return валідні параметри або порожній результат для невалідного введення.
     */
    Optional<NeuronParameters> parameters() {
        OptionalInt signalStrength = parsePositive(signalField.getText());
        OptionalInt activationThreshold = parsePositive(thresholdField.getText());
        if (signalStrength.isEmpty() || activationThreshold.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new NeuronParameters(
                signalStrength.getAsInt(),
                activationThreshold.getAsInt()
        ));
    }

    /** Оновлює доступність кнопки збереження відповідно до поточного введення. */
    private void validateInput() {
        saveButton.setDisable(
                parsePositive(signalField.getText()).isEmpty()
                        || parsePositive(thresholdField.getText()).isEmpty()
        );
    }

    /**
     * Створює поле, яке відхиляє символи, що не є десятковими цифрами.
     *
     * @return поле введення з числовим фільтром.
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
     * @param text текст, який потрібно перевірити.
     * @return додатне число або порожній результат для некоректного значення.
     */
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
