package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronParameters;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;

/** Володіє введенням параметрів нейрона, вибором режиму активації та перевіркою значень перед збереженням. */
final class NeuronSettingsForm {

    private static final String POPUP_STYLESHEET_RESOURCE = "/neuron-settings-popup.css";
    private static final String POPUP_CELL_NORMAL_STYLE = "-fx-background: #282d35; "
            + "-fx-background-color: #282d35; -fx-background-insets: 0; -fx-text-fill: #edf0f3;";
    private static final String POPUP_CELL_HOVER_STYLE = "-fx-background: #343a43; "
            + "-fx-background-color: #343a43; -fx-background-insets: 0; -fx-text-fill: #ffffff;";
    private static final String ACTIVATION_VALUE_STYLE = "-fx-background: #282d35; "
            + "-fx-background-color: #282d35; -fx-background-insets: 0; -fx-text-fill: #edf0f3;";

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

    /** Налаштовує селектор режиму без примусового компонування всієї сцени під час відкриття popup. */
    private void configureActivationModeSelector() {
        activationModeSelector.getItems().setAll(NeuronActivationMode.values());
        activationModeSelector.setValue(NeuronActivationMode.SIGNAL_STRENGTH);
        activationModeSelector.getStyleClass().add("neuron-settings-activation-selector");
        activationModeSelector.setMaxWidth(Double.MAX_VALUE);
        activationModeSelector.setCellFactory(_ -> activationModeCell(true));
        activationModeSelector.setButtonCell(activationModeCell(false));
        /*
         * Закриваємо popup до від'єднання власника від Scene, щоб ComboBox не зберігав
         * застарілий ланцюжок styleable-parent після закриття накладки налаштувань.
         */
        activationModeSelector.sceneProperty().addListener((_, _, currentScene) -> {
            if (currentScene == null) {
                activationModeSelector.hide();
            }
        });
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
        activationModeSelector.setOnAction(event -> {
            if (!updatingSelection && activationModeSelector.getValue() != null) {
                updateThresholdPrompt();
                validateInput();
            }
            event.consume();
        });
    }

    /** Створює локалізовану комірку та підключає popup-стилі лише до її окремого дерева. 
     *
     * @param popupOption {@code true}, якщо комірка відображається у випадаючому списку;
     *                    {@code false} для комірки вибраного значення в самому ComboBox.
     * @return комірка з локалізованим текстом і відповідним CSS-класом.
     */
    private ListCell<NeuronActivationMode> activationModeCell(boolean popupOption) {
        ListCell<NeuronActivationMode> cell = new ListCell<>() {
            /** Оновлює текст комірки для поточного елемента списку. */
            @Override
            protected void updateItem(NeuronActivationMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : activationModeText(item));
                if (popupOption) {
                    refreshActivationPopupCellStyle(this);
                }
            }
        };
        cell.getStyleClass().add(popupOption
                ? "neuron-settings-activation-option"
                : "neuron-settings-activation-value");
        if (!popupOption) {
            // The skin's display cell does not consistently resolve the view-scoped CSS text-fill
            // before the first CSS pulse in headless tests, so keep its value appearance explicit.
            cell.setStyle(ACTIVATION_VALUE_STYLE);
        }
        if (popupOption) {
            cell.hoverProperty().addListener((_, _, _) -> refreshActivationPopupCellStyle(cell));
            cell.sceneProperty().addListener((_, previousScene, currentScene) -> {
                if (currentScene != null && currentScene != previousScene
                        && currentScene != activationModeSelector.getScene()) {
                    attachPopupStylesheetToListView(cell);
                }
            });
        }
        return cell;
    }


    /** Застосовує inline-колір до popup-комірки, щоб стандартний синій selected Modena не перекривав тему. */
    private void refreshActivationPopupCellStyle(ListCell<?> cell) {
        if (cell.isEmpty()) {
            cell.setStyle("");
            return;
        }
        cell.setStyle(cell.isHover() ? POPUP_CELL_HOVER_STYLE : POPUP_CELL_NORMAL_STYLE);
    }

    /**
     * Прикріплює таблицю стилів безпосередньо до ListView усередині popup.
     * Список стилів Scene/кореня PopupControl може замінюватися під час показу popup;
     * stylesheet самого ListView залишається в його дереві й стилізує його комірки.
     */
    static void attachPopupStylesheetToListView(ListCell<?> popupCell) {
        Objects.requireNonNull(popupCell, "popupCell");
        Parent ancestor = popupCell.getParent();
        while (ancestor != null && !(ancestor instanceof ListView<?>)) {
            ancestor = ancestor.getParent();
        }
        if (!(ancestor instanceof ListView<?> popupList)) {
            return;
        }

        URL resource = NeuronSettingsForm.class.getResource(POPUP_STYLESHEET_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("Не знайдено таблицю стилів popup: " + POPUP_STYLESHEET_RESOURCE);
        }
        // A component-owner class keeps popup rules isolated from unrelated ListViews.
        if (!popupList.getStyleClass().contains("neuron-settings-activation-popup")) {
            popupList.getStyleClass().add("neuron-settings-activation-popup");
        }
        String stylesheet = resource.toExternalForm();
        if (!popupList.getStylesheets().contains(stylesheet)) {
            popupList.getStylesheets().add(stylesheet);
        }
    }

    /** Повертає локалізовану назву конкретного способу активації. */
    private String activationModeText(NeuronActivationMode mode) {
        return localization.textFrom("i18n.neuronSettings", mode == NeuronActivationMode.SIGNAL_STRENGTH
                ? "activation_mode_strength"
                : "activation_mode_count");
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
