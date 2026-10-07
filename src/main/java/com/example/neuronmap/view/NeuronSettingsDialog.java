package com.example.neuronmap.view;

import com.example.neuronmap.model.Neuron;
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
 * Dialog for changing user-controlled neuron signal parameters.
 */
public final class NeuronSettingsDialog {

    private NeuronSettingsDialog() {
    }

    public record Values(
            int signalStrength,
            int activationThreshold
    ) {
    }

    public static Optional<Values> show(
            Window owner,
            Neuron neuron
    ) {
        if (neuron == null) {
            return Optional.empty();
        }

        Dialog<Values> dialog = new Dialog<>();
        dialog.setTitle("Налаштування нейрона");
        dialog.setHeaderText(
                "Сила сигналу та поріг активації"
        );

        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType saveButtonType =
                new ButtonType(
                        "Зберегти",
                        ButtonBar.ButtonData.OK_DONE
                );

        dialog.getDialogPane()
                .getButtonTypes()
                .addAll(
                        saveButtonType,
                        ButtonType.CANCEL
                );

        TextField signalField =
                new TextField(
                        Integer.toString(
                                neuron.signalStrength()
                        )
                );

        TextField thresholdField =
                new TextField(
                        Integer.toString(
                                neuron.activationThreshold()
                        )
                );

        signalField.setPromptText(
                "Ціле число > 0"
        );
        thresholdField.setPromptText(
                "Ціле число > 0"
        );

        GridPane content = new GridPane();
        content.setHgap(10);
        content.setVgap(10);
        content.setPadding(
                new Insets(12)
        );

        content.add(
                new Label("Сила сигналу:"),
                0,
                0
        );
        content.add(
                signalField,
                1,
                0
        );

        content.add(
                new Label("Поріг активації:"),
                0,
                1
        );
        content.add(
                thresholdField,
                1,
                1
        );

        dialog.getDialogPane()
                .setContent(content);

        Node saveButton =
                dialog.getDialogPane()
                        .lookupButton(saveButtonType);

        Runnable validate = () -> {
            saveButton.setDisable(
                    parsePositive(signalField.getText())
                            == null
                            || parsePositive(
                            thresholdField.getText()
                    ) == null
            );
        };

        signalField.textProperty().addListener(
                (obs, oldValue, newValue) -> validate.run()
        );

        thresholdField.textProperty().addListener(
                (obs, oldValue, newValue) -> validate.run()
        );

        validate.run();

        dialog.setResultConverter(
                buttonType -> {
                    if (buttonType != saveButtonType) {
                        return null;
                    }

                    Integer signalStrength =
                            parsePositive(
                                    signalField.getText()
                            );
                    Integer activationThreshold =
                            parsePositive(
                                    thresholdField.getText()
                            );

                    if (signalStrength == null
                            || activationThreshold == null) {
                        return null;
                    }

                    return new Values(
                            signalStrength,
                            activationThreshold
                    );
                }
        );

        return dialog.showAndWait();
    }

    private static Integer parsePositive(
            String text
    ) {
        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            int value = Integer.parseInt(text.trim());
            return value > 0
                    ? value
                    : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
