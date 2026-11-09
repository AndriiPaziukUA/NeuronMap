package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.model.SignalType;
import com.example.neuronmap.model.StructureNeuronCreationMode;
import com.example.neuronmap.view.support.SwitchToggleButton;
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
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** Володіє введенням параметрів нейрона, вибором режимів і перевіркою значень форми. */
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
    private final TextField structureMemberCountField = createNumericField();
    private final SwitchToggleButton strongestNeuronOnlyToggle = new SwitchToggleButton(
            "neuron-settings-strongest-graphic", "neuron-settings-strongest-track", "neuron-settings-strongest-thumb");
    private final ComboBox<NeuronActivationMode> activationModeSelector = new ComboBox<>();
    private final ComboBox<SignalType> signalTypeSelector = new ComboBox<>();
    private final ComboBox<StructureNeuronCreationMode> neuronCreationModeSelector = new ComboBox<>();
    private boolean updatingSelection;
    private boolean structureSettings;
    private int minimumStructureMemberCount;
    private String positiveIntegerPrompt = "";
    private String percentagePrompt = "";

    /**
     * Створює форму, налаштовує списки режимів і перевірку введених значень.
     *
     * @param saveButton кнопка збереження, доступністю якої керує форма.
     * @param localization спільна служба локалізації.
     */
    NeuronSettingsForm(Button saveButton, LocalizationService localization) {
        this.saveButton = Objects.requireNonNull(saveButton, "saveButton");
        this.localization = Objects.requireNonNull(localization, "localization");
        signalField.textProperty().addListener((_, _, _) -> validateInput());
        thresholdField.textProperty().addListener((_, _, _) -> validateInput());
        activeTicksField.textProperty().addListener((_, _, _) -> validateInput());
        delayTicksField.textProperty().addListener((_, _, _) -> validateInput());
        structureMemberCountField.textProperty().addListener((_, _, _) -> validateInput());
        strongestNeuronOnlyToggle.getStyleClass().add("neuron-settings-strongest-toggle");
        configureActivationModeSelector();
        configureSignalTypeSelector();
        configureNeuronCreationModeSelector();
        validateInput();
    }

    /** Завантажує параметри нейрона та задає нижню межу кількості учасників структури. */
    void load(Neuron neuron, int currentMemberCount) {
        Objects.requireNonNull(neuron, "neuron");
        structureSettings = neuron.type() == NeuronType.STRUCTURE;
        minimumStructureMemberCount = structureSettings ? Math.max(0, currentMemberCount) : 0;
        nameField.setText(neuron.name() == null ? "" : neuron.name());
        signalField.setText(Integer.toString(neuron.signalStrength()));
        thresholdField.setText(Integer.toString(neuron.activationThreshold()));
        activeTicksField.setText(Integer.toString(neuron.activeTicks()));
        delayTicksField.setText(Integer.toString(neuron.delayTicks()));
        structureMemberCountField.setText(Integer.toString(minimumStructureMemberCount));
        updatingSelection = true;
        try {
            activationModeSelector.setValue(neuron.activationMode());
            signalTypeSelector.setValue(neuron.signalType());
            neuronCreationModeSelector.setValue(neuron.neuronCreationMode());
            strongestNeuronOnlyToggle.setSelected(structureSettings && neuron.strongestNeuronOnly());
        } finally {
            updatingSelection = false;
        }
        for (TextField field : fields()) {
            field.deselect();
            field.positionCaret(field.getLength());
        }
        structureMemberCountField.deselect();
        structureMemberCountField.positionCaret(structureMemberCountField.getLength());
        nameField.deselect();
        nameField.positionCaret(nameField.getLength());
        updateThresholdPrompt();
        validateInput();
    }

    /** Установлює текстові підказки числових полів відповідно до активної локалі. */
    void setPromptText(String positiveIntegerPrompt, String percentagePrompt) {
        this.positiveIntegerPrompt = Objects.requireNonNull(positiveIntegerPrompt, "positiveIntegerPrompt");
        this.percentagePrompt = Objects.requireNonNull(percentagePrompt, "percentagePrompt");
        for (TextField field : fields()) {
            if (field != thresholdField) {
                field.setPromptText(this.positiveIntegerPrompt);
            }
        }
        structureMemberCountField.setPromptText(this.positiveIntegerPrompt);
        updateThresholdPrompt();
    }

    /** Оновлює підписи режимів після перемикання локалі, зберігаючи поточні вибрані значення. */
    void refreshTexts() {
        strongestNeuronOnlyToggle.setAccessibleText(
                localization.textFrom("i18n.neuronSettings", "strongest_neuron"));
        NeuronActivationMode selectedActivationMode = activationModeSelector.getValue();
        SignalType selectedSignalType = signalTypeSelector.getValue();
        StructureNeuronCreationMode selectedCreationMode = neuronCreationModeSelector.getValue();
        updatingSelection = true;
        try {
            activationModeSelector.getItems().setAll(NeuronActivationMode.values());
            activationModeSelector.setValue(selectedActivationMode == null
                    ? NeuronActivationMode.SIGNAL_STRENGTH : selectedActivationMode);
            signalTypeSelector.getItems().setAll(SignalType.values());
            signalTypeSelector.setValue(selectedSignalType == null ? SignalType.STATIC : selectedSignalType);
            neuronCreationModeSelector.getItems().setAll(StructureNeuronCreationMode.values());
            neuronCreationModeSelector.setValue(selectedCreationMode == null
                    ? StructureNeuronCreationMode.NEURON_COUNT : selectedCreationMode);
        } finally {
            updatingSelection = false;
        }
        updateThresholdPrompt();
        validateInput();
    }

    /** Повертає поле назви нейрона. */
    TextField nameField() { return nameField; }

    /** Повертає введену назву або {@code null}, якщо поле порожнє. */
    String name() {
        String value = nameField.getText();
        return value == null || value.isEmpty() ? null : value;
    }

    /** Повертає поле сили сигналу. */
    TextField signalField() { return signalField; }

    /** Повертає поле порога активації. */
    TextField thresholdField() { return thresholdField; }

    /** Повертає поле тривалості активності. */
    TextField activeTicksField() { return activeTicksField; }

    /** Повертає поле затримки перед першим сигналом. */
    TextField delayTicksField() { return delayTicksField; }

    /** Повертає поле кількості безпосередніх учасників структури. */
    TextField structureMemberCountField() { return structureMemberCountField; }

    /** Повертає перемикач конкуренції учасників структури. */
    SwitchToggleButton strongestNeuronOnlyToggle() { return strongestNeuronOnlyToggle; }

    /** Повертає вибране значення перемикача конкуренції. */
    boolean strongestNeuronOnly() {
        return structureSettings && strongestNeuronOnlyToggle.isSelected();
    }

    /** Повертає вибрану умову створення спільного нейрона. */
    StructureNeuronCreationMode neuronCreationMode() {
        return structureSettings ? neuronCreationModeSelector.getValue() : StructureNeuronCreationMode.NEURON_COUNT;
    }

    /** Звільняє графічний перемикач після завершення життєвого циклу форми. */
    void dispose() {
        strongestNeuronOnlyToggle.dispose();
    }

    /** Повертає список способів перевірки порога активації. */
    ComboBox<NeuronActivationMode> activationModeSelector() { return activationModeSelector; }

    /** Повертає список типів вихідного сигналу. */
    ComboBox<SignalType> signalTypeSelector() { return signalTypeSelector; }

    /** Повертає список способів створення спільного нейрона в структурі. */
    ComboBox<StructureNeuronCreationMode> neuronCreationModeSelector() { return neuronCreationModeSelector; }

    /** Повертає параметри лише тоді, коли всі поля форми мають допустимі значення. */
    Optional<NeuronParameters> parameters() {
        OptionalInt signalStrength = parsePositive(signalField.getText());
        OptionalInt activationThreshold = parseThreshold(thresholdField.getText(), activationModeSelector.getValue());
        OptionalInt activeTicks = parsePositive(activeTicksField.getText());
        OptionalInt delayTicks = parsePositive(delayTicksField.getText());
        NeuronActivationMode activationMode = activationModeSelector.getValue();
        SignalType signalType = signalTypeSelector.getValue();
        if (signalStrength.isEmpty() || activationThreshold.isEmpty()
                || activeTicks.isEmpty() || delayTicks.isEmpty()
                || activationMode == null || signalType == null
                || structureSettings && neuronCreationModeSelector.getValue() == null) {
            return Optional.empty();
        }
        return Optional.of(new NeuronParameters(signalStrength.getAsInt(), activationThreshold.getAsInt(),
                activeTicks.getAsInt(), delayTicks.getAsInt(), activationMode, signalType));
    }

    /** Повертає цільову кількість учасників структури, якщо вона не менша за поточну. */
    OptionalInt structureMemberCount() {
        if (!structureSettings) {
            return OptionalInt.empty();
        }
        OptionalInt requestedCount = parseNonNegative(structureMemberCountField.getText());
        if (requestedCount.isEmpty() || requestedCount.getAsInt() < minimumStructureMemberCount) {
            return OptionalInt.empty();
        }
        return requestedCount;
    }

    /** Налаштовує список способів перевірки порога. */
    private void configureActivationModeSelector() {
        configureModeSelector(activationModeSelector, List.of(NeuronActivationMode.values()),
                NeuronActivationMode.SIGNAL_STRENGTH, this::activationModeText, this::updateThresholdPrompt);
    }

    /** Налаштовує список типів сигналу з типовим статичним режимом. */
    private void configureSignalTypeSelector() {
        configureModeSelector(signalTypeSelector, List.of(SignalType.values()),
                SignalType.STATIC, this::signalTypeText, () -> { });
    }

    /** Налаштовує список умови створення спільного нейрона. */
    private void configureNeuronCreationModeSelector() {
        configureModeSelector(neuronCreationModeSelector, List.of(StructureNeuronCreationMode.values()),
                StructureNeuronCreationMode.NEURON_COUNT, this::neuronCreationModeText, () -> { });
    }

    /** Спільно налаштовує локалізацію, відображення й життєвий цикл випадаючого списку. */
    private <T> void configureModeSelector(ComboBox<T> selector, List<T> modes, T defaultMode,
                                           Function<T, String> textProvider, Runnable selectionChanged) {
        selector.getItems().setAll(modes);
        selector.setValue(defaultMode);
        selector.getStyleClass().add("neuron-settings-activation-selector");
        selector.setMaxWidth(Double.MAX_VALUE);
        selector.setCellFactory(_ -> modeCell(selector, true, textProvider));
        selector.setButtonCell(modeCell(selector, false, textProvider));
        selector.sceneProperty().addListener((_, _, currentScene) -> {
            if (currentScene == null) selector.hide();
        });
        selector.setConverter(new StringConverter<>() {
            /** Повертає локалізовану назву вибраного режиму. */
            @Override
            public String toString(T mode) {
                return mode == null ? "" : textProvider.apply(mode);
            }

            /** Текстове введення не перетворюється на значення переліку. */
            @Override
            public T fromString(String value) {
                return null;
            }
        });
        selector.setOnAction(event -> {
            if (!updatingSelection && selector.getValue() != null) {
                selectionChanged.run();
                validateInput();
            }
            event.consume();
        });
    }

    /** Створює комірку списку з темним оформленням і локалізованим значенням. */
    private <T> ListCell<T> modeCell(ComboBox<T> selector, boolean popupOption, Function<T, String> textProvider) {
        ListCell<T> cell = new ListCell<>() {
            /** Оновлює напис комірки після зміни елемента. */
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : textProvider.apply(item));
                if (popupOption) refreshActivationPopupCellStyle(this);
            }
        };
        cell.getStyleClass().add(popupOption
                ? "neuron-settings-activation-option" : "neuron-settings-activation-value");
        if (!popupOption) cell.setStyle(ACTIVATION_VALUE_STYLE);
        if (popupOption) {
            cell.hoverProperty().addListener((_, _, _) -> refreshActivationPopupCellStyle(cell));
            cell.sceneProperty().addListener((_, previousScene, currentScene) -> {
                if (currentScene != null && currentScene != previousScene && currentScene != selector.getScene()) {
                    attachPopupStylesheetToListView(cell);
                }
            });
        }
        return cell;
    }

    /** Застосовує кольори теми до комірки випадаючого списку. */
    private void refreshActivationPopupCellStyle(ListCell<?> cell) {
        if (cell.isEmpty()) {
            cell.setStyle("");
            return;
        }
        cell.setStyle(cell.isHover() ? POPUP_CELL_HOVER_STYLE : POPUP_CELL_NORMAL_STYLE);
    }

    /** Прикріплює таблицю стилів до ListView усередині popup, не змінюючи глобальну сцену. */
    static void attachPopupStylesheetToListView(ListCell<?> popupCell) {
        Objects.requireNonNull(popupCell, "popupCell");
        Parent ancestor = popupCell.getParent();
        while (ancestor != null && !(ancestor instanceof ListView<?>)) ancestor = ancestor.getParent();
        if (!(ancestor instanceof ListView<?> popupList)) return;

        URL resource = NeuronSettingsForm.class.getResource(POPUP_STYLESHEET_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("Не знайдено таблицю стилів popup: " + POPUP_STYLESHEET_RESOURCE);
        }
        if (!popupList.getStyleClass().contains("neuron-settings-activation-popup")) {
            popupList.getStyleClass().add("neuron-settings-activation-popup");
        }
        String stylesheet = resource.toExternalForm();
        if (!popupList.getStylesheets().contains(stylesheet)) popupList.getStylesheets().add(stylesheet);
    }

    /** Повертає локалізовану назву способу перевірки порога. */
    private String activationModeText(NeuronActivationMode mode) {
        return localization.textFrom("i18n.neuronSettings",
                mode == NeuronActivationMode.SIGNAL_STRENGTH ? "activation_mode_strength" : "activation_mode_count");
    }

    /** Повертає локалізовану назву типу вихідного сигналу. */
    private String signalTypeText(SignalType type) {
        return localization.textFrom("i18n.neuronSettings", switch (type) {
            case STATIC -> "signal_type_static";
            case STRONGEST -> "signal_type_strongest";
            case RECEIVED -> "signal_type_received";
        });
    }

    /** Повертає локалізовану назву способу створення спільного нейрона. */
    private String neuronCreationModeText(StructureNeuronCreationMode mode) {
        return localization.textFrom("i18n.neuronSettings", switch (mode) {
            case NEURON_COUNT -> "neuron_creation_mode_neurons";
            case STRUCTURE_COUNT -> "neuron_creation_mode_structures";
        });
    }

    /** Оновлює підказку порога відповідно до вибраного способу активації. */
    private void updateThresholdPrompt() {
        thresholdField.setPromptText(activationModeSelector.getValue() == NeuronActivationMode.SIGNAL_COUNT
                ? percentagePrompt : positiveIntegerPrompt);
    }

    /** Вимикає збереження, якщо будь-яке поле або список режимів містить некоректне значення. */
    private void validateInput() {
        NeuronActivationMode mode = activationModeSelector.getValue();
        boolean invalidNumericInput = fields().stream().anyMatch(field ->
                field == thresholdField
                        ? parseThreshold(field.getText(), mode).isEmpty()
                        : parsePositive(field.getText()).isEmpty());
        boolean invalidStructureCount = structureSettings && structureMemberCount().isEmpty();
        saveButton.setDisable(mode == null || signalTypeSelector.getValue() == null
                || invalidNumericInput || invalidStructureCount);
    }

    /** Повертає числові поля в однаковому порядку для валідації та налаштування підказок. */
    private List<TextField> fields() {
        return List.of(signalField, thresholdField, activeTicksField, delayTicksField);
    }

    /** Створює поле назви з обмеженням кількості Unicode-кодових точок. */
    private static TextField createNameField() {
        TextField field = new TextField();
        UnaryOperator<TextFormatter.Change> lengthLimit = change ->
                change.getControlNewText().codePointCount(0, change.getControlNewText().length())
                        <= Neuron.MAX_NAME_LENGTH ? change : null;
        field.setTextFormatter(new TextFormatter<>(lengthLimit));
        field.setPrefColumnCount(10);
        return field;
    }

    /** Створює числове поле, яке приймає лише десяткові цифри. */
    private static TextField createNumericField() {
        TextField field = new TextField();
        UnaryOperator<TextFormatter.Change> digitsOnly = change ->
                change.getControlNewText().matches("\\d*") ? change : null;
        field.setTextFormatter(new TextFormatter<>(digitsOnly));
        return field;
    }

    /** Перетворює текст на додатне ціле число в межах {@code int}. */
    private static OptionalInt parsePositive(String text) {
        if (text == null || text.isBlank()) return OptionalInt.empty();
        try {
            int value = Integer.parseInt(text);
            return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    /** Перетворює текст на невід'ємне ціле число в межах {@code int}. */
    private static OptionalInt parseNonNegative(String text) {
        if (text == null || text.isBlank()) return OptionalInt.empty();
        try {
            int value = Integer.parseInt(text);
            return value >= 0 ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    /** Перевіряє числовий або відсотковий поріг відповідно до вибраного способу активації. */
    private static OptionalInt parseThreshold(String text, NeuronActivationMode mode) {
        if (mode == null || text == null || text.isBlank()) return OptionalInt.empty();
        try {
            int value = Integer.parseInt(text);
            if (mode == NeuronActivationMode.SIGNAL_COUNT) {
                return value >= 0 && value <= 100 ? OptionalInt.of(value) : OptionalInt.empty();
            }
            return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }
}
