package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.model.SignalType;
import com.example.neuronmap.model.StructureNeuronCreationMode;
import com.example.neuronmap.view.support.ContentSizedButtonPair;
import com.example.neuronmap.view.support.EditorKeyboardShortcutBoundary;
import com.example.neuronmap.view.support.SceneKeyboardModality;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Відображає налаштування нейрона як внутрішню модальну накладку головного вікна.
 * Тип сигналу спільний для внутрішніх нейронів структури, тоді як персональні сили адаптивних учасників
 * залишаються в моделі кожного нейрона.
 */
public final class NeuronSettingsView extends StackPane implements EditorKeyboardShortcutBoundary {
    private static final String STYLESHEET_RESOURCE = "/neuron-settings.css";
    private static final String LOCALIZATION_BUNDLE = "i18n.neuronSettings";
    private static final String SETTINGS_BUNDLE_KEY_PREFIX = "i18n.neuronSettings.";
    private static final double INPUT_COLUMN_WIDTH = 160.0;
    private static final double NUMERIC_INPUT_COLUMN_WIDTH = INPUT_COLUMN_WIDTH * 2.0 / 3.0;

    /** Callback зі збереженням параметрів для звичайного нейрона або кількості для структури. */
    @FunctionalInterface
    public interface SettingsSaveAction {
        /** Передає перевірені значення власнику операції редагування. */
        void accept(NeuronParameters parameters, String name, int structureMemberCount);
    }

    /** Callback з параметрами структури й перемикачем найсильнішого учасника. */
    @FunctionalInterface
    public interface StructureSettingsSaveAction {
        /** Передає спільні параметри структури після перевірки форми. */
        void accept(NeuronParameters parameters, String name, int structureMemberCount, boolean strongestNeuronOnly);
    }

    /** Callback з усіма параметрами структури, включно з умовою створення спільних нейронів. */
    @FunctionalInterface
    public interface StructureNeuronCreationSettingsSaveAction {
        /** Передає перевірені параметри структури після підтвердження форми. */
        void accept(NeuronParameters parameters, String name, int structureMemberCount,
                    boolean strongestNeuronOnly, StructureNeuronCreationMode neuronCreationMode);
    }

    private final LocalizationService localization;
    private final Map<String, Label> settingLabels = new LinkedHashMap<>();
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();
    private final Rectangle backdrop = new Rectangle();
    private final VBox panel = new VBox(14.0);
    private final Label title = new Label();
    private final VBox settingsRows = new VBox(8.0);
    private final ScrollPane settingsScroll = new ScrollPane(settingsRows);
    private final HBox footer = new HBox(10.0);
    private final Button saveButton = new Button();
    private final Button cancelButton = new Button();
    private final TextField nameField;
    private final TextField signalField;
    private final TextField thresholdField;
    private final TextField activeTicksField;
    private final TextField delayTicksField;
    private final TextField structureMemberCountField;
    private final ComboBox<NeuronActivationMode> activationModeSelector;
    private final ComboBox<SignalType> signalTypeSelector;
    private final ComboBox<StructureNeuronCreationMode> neuronCreationModeSelector;
    private final NeuronSettingsForm settingsForm;
    private final EventHandler<KeyEvent> sceneKeyboardFilter = this::filterOutsideModalKeyboardEvents;
    private final NeuronSettingsLayout settingsLayout;

    private StackPane host;
    private Scene keyboardFilterScene;
    private StructureNeuronCreationSettingsSaveAction saveAction;
    private HBox structureMemberCountRow;
    private HBox strongestNeuronOnlyRow;
    private HBox neuronCreationModeRow;
    private HBox signalTypeRow;
    private boolean structureSettingsTitle;
    private boolean localizationListenerRegistered;
    private boolean saving;
    private boolean disposed;

    /**
     * Створює внутрішню накладку налаштувань з власною таблицею стилів.
     *
     * @param localization спільна служба локалізації.
     */
    public NeuronSettingsView(LocalizationService localization) {
        this.localization = Objects.requireNonNull(localization, "localization");
        URL stylesheet = getClass().getResource(STYLESHEET_RESOURCE);
        if (stylesheet == null) {
            throw new IllegalStateException(STYLESHEET_RESOURCE + " is missing");
        }
        getStylesheets().add(stylesheet.toExternalForm());
        setManaged(false);
        setVisible(false);
        setPickOnBounds(true);
        setFocusTraversable(true);
        getStyleClass().add("neuron-settings-overlay");
        setMinSize(0.0, 0.0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        configureBackdrop();
        configurePanel();
        settingsLayout = new NeuronSettingsLayout(this, panel, settingsRows, settingsScroll);
        settingsForm = new NeuronSettingsForm(saveButton, localization);
        nameField = settingsForm.nameField();
        signalField = settingsForm.signalField();
        thresholdField = settingsForm.thresholdField();
        activeTicksField = settingsForm.activeTicksField();
        delayTicksField = settingsForm.delayTicksField();
        structureMemberCountField = settingsForm.structureMemberCountField();
        activationModeSelector = settingsForm.activationModeSelector();
        signalTypeSelector = settingsForm.signalTypeSelector();
        neuronCreationModeSelector = settingsForm.neuronCreationModeSelector();
        configureSettingsRows();
        configureFooter();
        getChildren().addAll(backdrop, panel);
        refreshTexts();
    }

    /** Додає рядок до прокручуваного списку та оновлює його виміряну висоту. */
    void addSetting(Node settingRow) {
        settingsRows.getChildren().add(Objects.requireNonNull(settingRow, "settingRow"));
        settingsLayout.updateViewportHeight();
        requestLayout();
    }

    /** Показує налаштування нейрона без редагування його назви. */
    public void showIn(StackPane applicationRoot, Neuron neuron, Consumer<NeuronParameters> onSave) {
        Objects.requireNonNull(onSave, "onSave");
        showWithNameIn(applicationRoot, neuron, (parameters, ignoredName) -> onSave.accept(parameters));
    }

    /** Показує налаштування нейрона та його назви. */
    public void showWithNameIn(StackPane applicationRoot, Neuron neuron,
                               BiConsumer<NeuronParameters, String> onSave) {
        Objects.requireNonNull(onSave, "onSave");
        showWithStructureSettingsIn(applicationRoot, neuron, 0,
                (parameters, name, ignoredCount, ignoredStrongest) -> onSave.accept(parameters, name));
    }

    /** Показує налаштування й передає поточну кількість учасників структури. */
    public void showWithStructureCountIn(StackPane applicationRoot, Neuron neuron, int currentMemberCount,
                                         SettingsSaveAction onSave) {
        Objects.requireNonNull(onSave, "onSave");
        showWithStructureSettingsIn(applicationRoot, neuron, currentMemberCount,
                (parameters, name, count, ignoredStrongest) -> onSave.accept(parameters, name, count));
    }

    /** Показує налаштування структури зі збереженням сумісності callback-а без режиму створення нейронів. */
    public void showWithStructureSettingsIn(StackPane applicationRoot, Neuron neuron, int currentMemberCount,
                                            StructureSettingsSaveAction onSave) {
        Objects.requireNonNull(onSave, "onSave");
        showWithStructureSettingsIn(applicationRoot, neuron, currentMemberCount,
                (parameters, name, count, strongest, ignoredMode) ->
                        onSave.accept(parameters, name, count, strongest), false);
    }

    /** Показує налаштування структури з підтримкою вибору режиму створення нейронів. */
    public void showWithStructureSettingsIn(StackPane applicationRoot, Neuron neuron, int currentMemberCount,
                                            StructureNeuronCreationSettingsSaveAction onSave) {
        showWithStructureSettingsIn(applicationRoot, neuron, currentMemberCount, onSave, true);
    }

    /** Відкриває накладку та встановлює її callback збереження. */
    private void showWithStructureSettingsIn(StackPane applicationRoot, Neuron neuron, int currentMemberCount,
                                             StructureNeuronCreationSettingsSaveAction onSave,
                                             boolean creationModeEditable) {
        ensureNotDisposed();
        Objects.requireNonNull(applicationRoot, "applicationRoot");
        Objects.requireNonNull(neuron, "neuron");
        Objects.requireNonNull(onSave, "onSave");
        if (neuron.type() == NeuronType.STRUCTURE && currentMemberCount < 0) {
            throw new IllegalArgumentException("Structure member count must not be negative");
        }
        Scene scene = applicationRoot.getScene();
        if (scene == null) {
            throw new IllegalStateException("Neuron settings overlay requires an attached JavaFX scene");
        }
        if (host != null) {
            throw new IllegalStateException("Neuron settings view is already showing");
        }
        structureSettingsTitle = neuron.type() == NeuronType.STRUCTURE;
        setRowVisibility(structureMemberCountRow, structureSettingsTitle);
        setRowVisibility(strongestNeuronOnlyRow, structureSettingsTitle);
        setRowVisibility(neuronCreationModeRow, structureSettingsTitle && creationModeEditable);
        settingsForm.load(neuron, currentMemberCount);
        saveAction = onSave;
        host = applicationRoot;
        startFilteringSceneKeyboard(scene);
        startListeningToLocalization();
        refreshTexts();
        setManaged(true);
        setVisible(true);
        applicationRoot.getChildren().add(this);
        applyInitialLayout(applicationRoot);
        requestFocus();
    }

    /** Закриває накладку без збереження введених значень. */
    public void hide() {
        close();
    }

    /** Остаточно звільняє ресурси подання; повторний виклик безпечний. */
    public void dispose() {
        if (disposed) {
            return;
        }
        close();
        settingsForm.dispose();
        disposed = true;
    }

    /** Перевіряє, чи подання ще можна відкривати. */
    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Neuron settings view is disposed");
        }
    }

    /** Повідомляє маршрутизатору клавіатури, що накладка блокує глобальні команди редактора. */
    @Override
    public boolean blocksEditorShortcuts() {
        return host != null && isVisible();
    }

    /** Показує, чи відбулося закриття саме внаслідок збереження параметрів. */
    public boolean isSaving() {
        return saving;
    }

    /** Налаштовує затемнений фон, клацання по якому скасовує редагування. */
    private void configureBackdrop() {
        backdrop.setManaged(false);
        backdrop.getStyleClass().add("neuron-settings-backdrop");
        backdrop.widthProperty().bind(widthProperty());
        backdrop.heightProperty().bind(heightProperty());
        backdrop.setOnMouseClicked(event -> {
            close();
            event.consume();
        });
    }

    /** Налаштовує геометрію картки та заголовка. */
    private void configurePanel() {
        panel.getStyleClass().add("neuron-settings-panel");
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setMinSize(0.0, 0.0);
        panel.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        panel.setMaxSize(NeuronSettingsLayout.MAX_PANEL_WIDTH, NeuronSettingsLayout.MAX_PANEL_HEIGHT);
        StackPane.setAlignment(panel, Pos.CENTER);
        title.getStyleClass().add("neuron-settings-title");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
    }

    /** Створює рядки параметрів у потрібному порядку та налаштовує прокручування. */
    private void configureSettingsRows() {
        settingsRows.setMinSize(0.0, 0.0);
        settingsRows.setFillWidth(true);
        addSetting(createSettingRow(SETTINGS_BUNDLE_KEY_PREFIX + "name", nameField));
        structureMemberCountRow = createSettingRow(
                SETTINGS_BUNDLE_KEY_PREFIX + "structure_neuron_count", structureMemberCountField);
        setRowVisibility(structureMemberCountRow, false);
        addSetting(structureMemberCountRow);
        strongestNeuronOnlyRow = createSettingRow(
                SETTINGS_BUNDLE_KEY_PREFIX + "strongest_neuron", settingsForm.strongestNeuronOnlyToggle());
        setRowVisibility(strongestNeuronOnlyRow, false);
        addSetting(strongestNeuronOnlyRow);
        neuronCreationModeRow = createSettingRow(
                SETTINGS_BUNDLE_KEY_PREFIX + "neuron_creation_mode", neuronCreationModeSelector);
        setRowVisibility(neuronCreationModeRow, false);
        addSetting(neuronCreationModeRow);
        signalTypeRow = createSettingRow(SETTINGS_BUNDLE_KEY_PREFIX + "signal_type", signalTypeSelector);
        addSetting(signalTypeRow);
        addSetting(createSettingRow("neuron.settings.signal_strength", signalField));
        addSetting(createSettingRow(SETTINGS_BUNDLE_KEY_PREFIX + "activation_by",
                localization.textFrom(LOCALIZATION_BUNDLE, "activation_by"), activationModeSelector));
        addSetting(createSettingRow("neuron.settings.activation_threshold", thresholdField));
        addSetting(createSettingRow(SETTINGS_BUNDLE_KEY_PREFIX + "active_ticks", activeTicksField));
        addSetting(createSettingRow(SETTINGS_BUNDLE_KEY_PREFIX + "delay", delayTicksField));
        settingsScroll.setFitToWidth(true);
        settingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        settingsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        settingsScroll.setMinSize(0.0, 0.0);
        settingsScroll.getStyleClass().add("neuron-settings-scroll");
        settingsScroll.widthProperty().addListener((_, _, _) -> settingsLayout.updateViewportHeight());
        VBox.setVgrow(settingsScroll, Priority.ALWAYS);
    }

    /** Вмикає або вимикає керування рядком, узгоджуючи обидві JavaFX-ознаки видимості. */
    private static void setRowVisibility(HBox row, boolean visible) {
        row.setManaged(visible);
        row.setVisible(visible);
    }

    /** Створює рядок з локалізованим підписом та переданим полем або списком. */
    private HBox createSettingRow(String labelKey, Node control) {
        return createSettingRow(labelKey, labelText(labelKey), control);
    }

    /** Створює рядок із гнучким підписом та елементом керування сталої ширини. */
    private HBox createSettingRow(String labelKey, String initialLabelText, Node control) {
        Label label = new Label(initialLabelText);
        settingLabels.put(labelKey, label);
        label.getStyleClass().add("neuron-settings-label");
        label.setWrapText(true);
        label.setMinWidth(0.0);
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        if (control instanceof TextField textField) {
            textField.getStyleClass().add("neuron-settings-field");
            textField.setPrefColumnCount(10);
        }
        if (control instanceof Region region && control != settingsForm.strongestNeuronOnlyToggle()) {
            double controlWidth = control instanceof TextField textField && textField != nameField
                    ? NUMERIC_INPUT_COLUMN_WIDTH : INPUT_COLUMN_WIDTH;
            region.setMinWidth(controlWidth);
            region.setPrefWidth(controlWidth);
            region.setMaxWidth(controlWidth);
        }
        HBox row = new HBox(16.0, label, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9.0, 10.0, 9.0, 10.0));
        row.setMinWidth(0.0);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("neuron-settings-row");
        return row;
    }

    /** Отримує локалізований підпис із тематичного або спільного набору перекладів. */
    private String labelText(String labelKey) {
        if (labelKey.startsWith(SETTINGS_BUNDLE_KEY_PREFIX)) {
            return localization.textFrom(LOCALIZATION_BUNDLE,
                    labelKey.substring(SETTINGS_BUNDLE_KEY_PREFIX.length()));
        }
        return localization.text(labelKey);
    }

    /** Налаштовує кнопки підтвердження та скасування. */
    private void configureFooter() {
        saveButton.getStyleClass().add("neuron-settings-button");
        cancelButton.getStyleClass().add("neuron-settings-button");
        saveButton.setOnAction(event -> {
            save();
            event.consume();
        });
        cancelButton.setOnAction(event -> {
            close();
            event.consume();
        });
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getChildren().addAll(cancelButton, saveButton);
        panel.getChildren().addAll(title, settingsScroll, footer);
    }

    /** Застосовує CSS і повторно вимірює вміст після додавання накладки до сцени. */
    private void applyInitialLayout(StackPane applicationRoot) {
        applicationRoot.applyCss();
        applyCss();
        panel.applyCss();
        settingsRows.applyCss();
        stabilizeActivationSelectorHeight();
        ContentSizedButtonPair.sizeToLargestContent(saveButton, cancelButton);
        settingsLayout.updateViewportHeight();
        requestLayout();
        applicationRoot.requestLayout();
        applicationRoot.layout();
        settingsLayout.updateViewportHeight();
        requestLayout();
        applicationRoot.requestLayout();
        applicationRoot.layout();
    }

    /** Фіксує висоту випадаючих списків, щоб зміна вибраного режиму не змінювала геометрію рядка. */
    private void stabilizeActivationSelectorHeight() {
        stabilizeSelectorHeight(activationModeSelector);
        stabilizeSelectorHeight(signalTypeSelector);
        stabilizeSelectorHeight(neuronCreationModeSelector);
    }

    /** Фіксує обчислену CSS-висоту одного селектора. */
    private void stabilizeSelectorHeight(ComboBox<?> selector) {
        selector.applyCss();
        double preferredHeight = selector.prefHeight(-1.0);
        if (Double.isFinite(preferredHeight) && preferredHeight > 0.0) {
            selector.setMinHeight(preferredHeight);
            selector.setPrefHeight(preferredHeight);
            selector.setMaxHeight(preferredHeight);
        }
    }

    /** Оновлює локалізовані тексти й розміри картки після зміни мови. */
    private void refreshTexts() {
        title.setText(structureSettingsTitle
                ? localization.textFrom(LOCALIZATION_BUNDLE, "structure_title")
                : localization.textFrom(LOCALIZATION_BUNDLE, "title"));
        settingLabels.forEach((key, label) -> label.setText(labelText(key)));
        saveButton.setText(localization.text("neuron.settings.save"));
        cancelButton.setText(localization.textFrom(LOCALIZATION_BUNDLE, "cancel"));
        settingsForm.refreshTexts();
        settingsForm.setPromptText(
                localization.text("neuron.settings.positive_integer"),
                localization.textFrom(LOCALIZATION_BUNDLE, "percentage"));
        if (getScene() != null) ContentSizedButtonPair.sizeToLargestContent(saveButton, cancelButton);
        settingsRows.requestLayout();
        settingsLayout.invalidatePreferredSize();
        settingsLayout.updateViewportHeight();
        if (getScene() != null) {
            requestLayout();
            if (host != null) host.requestLayout();
        }
    }

    /** Підлаштовує картку за природним розміром та доступною площею вікна. */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        settingsLayout.layoutChildren();
    }

    /** Реєструє слухача локалізації лише на час показу накладки. */
    private void startListeningToLocalization() {
        if (localizationListenerRegistered) return;
        localization.addListener(localizationListener);
        localizationListenerRegistered = true;
    }

    /** Знімає слухача локалізації після закриття накладки. */
    private void stopListeningToLocalization() {
        if (!localizationListenerRegistered) return;
        localization.removeListener(localizationListener);
        localizationListenerRegistered = false;
    }

    /** Зберігає перевірені параметри й передає їх власнику операції. */
    private void save() {
        Optional<NeuronParameters> parameters = settingsForm.parameters();
        OptionalInt memberCount = structureSettingsTitle
                ? settingsForm.structureMemberCount() : OptionalInt.of(-1);
        if (parameters.isEmpty() || memberCount.isEmpty() || saveAction == null) return;
        StructureNeuronCreationSettingsSaveAction action = saveAction;
        NeuronParameters savedParameters = parameters.orElseThrow();
        String savedName = settingsForm.name();
        int savedMemberCount = memberCount.getAsInt();
        boolean savedStrongestNeuronOnly = settingsForm.strongestNeuronOnly();
        StructureNeuronCreationMode savedNeuronCreationMode = settingsForm.neuronCreationMode();
        saving = true;
        try {
            close();
        } finally {
            saving = false;
        }
        action.accept(savedParameters, savedName, savedMemberCount,
                savedStrongestNeuronOnly, savedNeuronCreationMode);
    }

    /** Закриває накладку, очищує callback та знімає всі тимчасові підписки. */
    private void close() {
        StackPane previousHost = host;
        host = null;
        saveAction = null;
        stopFilteringSceneKeyboard();
        stopListeningToLocalization();
        setVisible(false);
        setManaged(false);
        if (previousHost != null) previousHost.getChildren().remove(this);
    }

    /** Блокує клавіатурні команди поза формою та закриває її клавішею Escape. */
    private void filterOutsideModalKeyboardEvents(KeyEvent event) {
        if (!blocksEditorShortcuts()) return;
        if (event.getEventType() == KeyEvent.KEY_PRESSED && event.getCode() == KeyCode.ESCAPE) {
            event.consume();
            close();
            return;
        }
        if (EditorKeyboardShortcutBoundary.blocksShortcutsFor(event.getTarget())) return;
        event.consume();
    }

    /** Реєструє фільтр клавіатурних подій сцени на час відкриття модальної накладки. */
    private void startFilteringSceneKeyboard(Scene scene) {
        if (keyboardFilterScene == scene) return;
        stopFilteringSceneKeyboard();
        if (scene != null) {
            SceneKeyboardModality.register(scene, this);
            scene.addEventFilter(KeyEvent.ANY, sceneKeyboardFilter);
            keyboardFilterScene = scene;
        }
    }

    /** Знімає фільтр клавіатурних подій, щоб після закриття накладки клавіатура працювала звичайно. */
    private void stopFilteringSceneKeyboard() {
        if (keyboardFilterScene == null) return;
        Scene previousScene = keyboardFilterScene;
        keyboardFilterScene = null;
        previousScene.removeEventFilter(KeyEvent.ANY, sceneKeyboardFilter);
        SceneKeyboardModality.unregister(previousScene, this);
    }

    /** Повертає поле назви для перевірок введення та завантаження значення. */
    TextField nameFieldForTest() { return nameField; }

    /** Повертає поле сили сигналу для перевірок введення. */
    TextField signalFieldForTest() { return signalField; }

    /** Повертає поле порога активації для перевірок введення. */
    TextField thresholdFieldForTest() { return thresholdField; }

    /** Повертає список способів перевірки порога для перевірок режимів. */
    ComboBox<NeuronActivationMode> activationModeSelectorForTest() { return activationModeSelector; }

    /** Повертає список типів сигналу для перевірок нового режиму. */
    ComboBox<SignalType> signalTypeSelectorForTest() { return signalTypeSelector; }

    /** Повертає поле кількості активних тактів для перевірок введення. */
    TextField activeTicksFieldForTest() { return activeTicksField; }

    /** Повертає поле затримки для перевірок введення. */
    TextField delayTicksFieldForTest() { return delayTicksField; }

    /** Повертає поле кількості учасників структури для перевірок валідації. */
    TextField structureMemberCountFieldForTest() { return structureMemberCountField; }

    /** Повертає рядок кількості учасників структури. */
    HBox structureMemberCountRowForTest() { return structureMemberCountRow; }

    /** Повертає рядок вибору типу сигналу для перевірки його порядку та видимості. */
    HBox signalTypeRowForTest() { return signalTypeRow; }

    /** Повертає рядок перемикача найсильнішого нейрона. */
    HBox strongestNeuronOnlyRowForTest() { return strongestNeuronOnlyRow; }

    /** Повертає рядок вибору умови створення нейрона. */
    HBox neuronCreationModeRowForTest() { return neuronCreationModeRow; }

    /** Повертає список способів створення спільного нейрона. */
    ComboBox<StructureNeuronCreationMode> neuronCreationModeSelectorForTest() { return neuronCreationModeSelector; }

    /** Повертає перемикач конкуренції учасників структури. */
    javafx.scene.control.ToggleButton strongestNeuronOnlyToggleForTest() {
        return settingsForm.strongestNeuronOnlyToggle();
    }

    /** Повертає кнопку збереження. */
    Button saveButtonForTest() { return saveButton; }

    /** Повертає кнопку скасування. */
    Button cancelButtonForTest() { return cancelButton; }

    /** Повертає прокручувану область форми. */
    ScrollPane settingsScrollForTest() { return settingsScroll; }

    /** Повертає картку форми для перевірок адаптивного компонування. */
    VBox panelForTest() { return panel; }

    /** Повертає заголовок форми. */
    Label titleForTest() { return title; }

    /** Повертає затемнений фон форми. */
    Node backdropForTest() { return backdrop; }

    /** Повертає локалізований підпис заданого параметра. */
    String settingLabelTextForTest(String key) {
        Label label = settingLabels.get(key);
        return label == null ? null : label.getText();
    }

    /** Повертає максимальну ширину картки. */
    double panelMaxWidthForTest() { return panel.getMaxWidth(); }

    /** Повертає максимальну висоту картки. */
    double panelMaxHeightForTest() { return panel.getMaxHeight(); }

    /** Повертає підказку поля сили сигналу. */
    String signalPromptForTest() { return signalField.getPromptText(); }

    /** Повертає підказку поля порога активації. */
    String thresholdPromptForTest() { return thresholdField.getPromptText(); }
}
