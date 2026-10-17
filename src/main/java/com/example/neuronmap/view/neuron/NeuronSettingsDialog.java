package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Показує діалог редагування параметрів сигналу нейрона й перевіряє введені числа.
 */
public final class NeuronSettingsDialog {

    /**
     * Забороняє створення екземплярів класу, оскільки всі операції діалогу статичні.
     */
    private NeuronSettingsDialog() {
    }

    /**
     * Показує діалог редагування параметрів нейрона з типовою українською локалізацією.
     *
     * @param owner вікно, над яким показують діалог.
     * @param neuron нейрон, параметри якого редагують.
     * @return нові параметри, якщо користувач підтвердив зміни; порожній результат у разі скасування.
     */
    public static Optional<NeuronParameters> show(Window owner, Neuron neuron) {
        return show(
                owner,
                neuron,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Показує діалог редагування параметрів нейрона та повертає єдине значення параметрів після підтвердження.
     *
     * @param owner вікно, над яким показують діалог.
     * @param neuron нейрон, параметри якого редагують.
     * @param localization служба локалізації інтерфейсу.
     * @return нові параметри, якщо користувач підтвердив зміни; порожній результат для відсутнього нейрона або скасування.
     */
    public static Optional<NeuronParameters> show(
            Window owner,
            Neuron neuron,
            LocalizationService localization
    ) {
        if (neuron == null) {
            return Optional.empty();
        }

        Dialog<NeuronParameters> dialog = new Dialog<>();
        dialog.setTitle(localization.text("neuron.settings.title"));
        dialog.setHeaderText(localization.text("neuron.settings.header"));
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType saveButtonType = new ButtonType(
                localization.text("neuron.settings.save"),
                ButtonBar.ButtonData.OK_DONE
        );
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField signalField = new TextField(Integer.toString(neuron.signalStrength()));
        TextField thresholdField = new TextField(Integer.toString(neuron.activationThreshold()));
        signalField.setPromptText(localization.text("neuron.settings.positive_integer"));
        thresholdField.setPromptText(localization.text("neuron.settings.positive_integer"));

        GridPane content = new GridPane();
        content.setHgap(10);
        content.setVgap(10);
        content.setPadding(new Insets(12));
        content.add(new Label(localization.text("neuron.settings.signal_strength")), 0, 0);
        content.add(signalField, 1, 0);
        content.add(new Label(localization.text("neuron.settings.activation_threshold")), 0, 1);
        content.add(thresholdField, 1, 1);
        dialog.getDialogPane().setContent(content);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        Runnable validate = () -> saveButton.setDisable(
                parsePositive(signalField.getText()) == null
                        || parsePositive(thresholdField.getText()) == null
        );
        signalField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        thresholdField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveButtonType) {
                return null;
            }
            Integer signalStrength = parsePositive(signalField.getText());
            Integer activationThreshold = parsePositive(thresholdField.getText());
            if (signalStrength == null || activationThreshold == null) {
                return null;
            }
            return new NeuronParameters(signalStrength, activationThreshold);
        });
        return dialog.showAndWait();
    }

    /**
     * Перетворює введений текст на додатне ціле число або повертає {@code null} для некоректного значення.
     *
     * @param text текст, введений користувачем.
     * @return додатне ціле число або {@code null}, якщо значення не можна прийняти.
     */
    private static Integer parsePositive(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
