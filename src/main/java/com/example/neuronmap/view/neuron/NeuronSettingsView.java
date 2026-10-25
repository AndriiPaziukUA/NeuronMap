package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.view.support.ContentSizedButtonPair;
import com.example.neuronmap.view.support.EditorKeyboardShortcutBoundary;
import com.example.neuronmap.view.support.SceneKeyboardModality;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Відображає налаштування нейрона як внутрішню модальну накладку головного вікна.
 * Подання ізолює власне оформлення та клавіатурні команди редактора, доки воно відкрите.
 */
public final class NeuronSettingsView extends StackPane implements EditorKeyboardShortcutBoundary {

    private static final String STYLESHEET_RESOURCE = "/neuron-settings.css";
    private static final String LOCALIZATION_BUNDLE = "i18n.neuronSettings";
    private static final String SETTINGS_BUNDLE_KEY_PREFIX = "i18n.neuronSettings.";
    private static final double INPUT_COLUMN_WIDTH = 160.0;

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
    private final NeuronSettingsForm settingsForm;
    private final EventHandler<KeyEvent> sceneKeyboardFilter = this::filterOutsideModalKeyboardEvents;
    private final NeuronSettingsLayout settingsLayout;

    private StackPane host;
    private Scene keyboardFilterScene;
    private BiConsumer<NeuronParameters, String> saveAction;
    private boolean localizationListenerRegistered;
    private boolean disposed;

    /**
     * Створює внутрішню накладку налаштувань з незалежною таблицею стилів.
     *
     * @param localization спільна служба локалізації застосунку.
     * @throws NullPointerException якщо службу локалізації не задано.
     * @throws IllegalStateException якщо таблиця стилів відсутня в classpath.
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
        settingsForm = new NeuronSettingsForm(saveButton);
        nameField = settingsForm.nameField();
        signalField = settingsForm.signalField();
        thresholdField = settingsForm.thresholdField();
        activeTicksField = settingsForm.activeTicksField();
        delayTicksField = settingsForm.delayTicksField();
        configureSettingsRows();
        configureFooter();
        getChildren().addAll(backdrop, panel);
        refreshTexts();
    }

    /** Додає рядок до прокручуваного списку та оновлює його природну висоту. */
    void addSetting(Node settingRow) {
        settingsRows.getChildren().add(Objects.requireNonNull(settingRow, "settingRow"));
        settingsLayout.updateViewportHeight();
        requestLayout();
    }

    /**
     * Показує накладку зі збереженням лише числових параметрів.
     *
     * @param applicationRoot кореневий контейнер для внутрішніх накладок.
     * @param neuron нейрон, параметри якого редагують.
     * @param onSave дія застосування підтверджених числових параметрів.
     */
    public void showIn(StackPane applicationRoot, Neuron neuron, Consumer<NeuronParameters> onSave) {
        Objects.requireNonNull(onSave, "onSave");
        showWithNameIn(applicationRoot, neuron, (parameters, ignoredName) -> onSave.accept(parameters));
    }

    /**
     * Показує накладку й завантажує назву та параметри нейрона для редагування.
     *
     * @param applicationRoot кореневий контейнер для внутрішніх накладок.
     * @param neuron нейрон, значення якого редагують.
     * @param onSave дія застосування підтверджених параметрів і назви.
     * @throws NullPointerException якщо будь-який обов'язковий аргумент не задано.
     * @throws IllegalStateException якщо подання звільнено, контейнер не належить сцені або накладка вже відкрита.
     */
    public void showWithNameIn(
            StackPane applicationRoot,
            Neuron neuron,
            BiConsumer<NeuronParameters, String> onSave
    ) {
        ensureNotDisposed();
        Objects.requireNonNull(applicationRoot, "applicationRoot");
        Objects.requireNonNull(neuron, "neuron");
        Objects.requireNonNull(onSave, "onSave");
        Scene scene = applicationRoot.getScene();
        if (scene == null) {
            throw new IllegalStateException("Neuron settings overlay requires an attached JavaFX scene");
        }
        if (host != null) {
            throw new IllegalStateException("Neuron settings view is already showing");
        }

        settingsForm.load(neuron);
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

    /** Закриває накладку, якщо вона відображається, без збереження введених значень. */
    public void hide() {
        close();
    }

    /** Остаточно звільняє тимчасові ресурси; повторний виклик безпечний, повторний показ заборонено. */
    public void dispose() {
        if (disposed) {
            return;
        }
        close();
        disposed = true;
    }

    /** Перевіряє, що подання не було остаточно звільнене. */
    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Neuron settings view is disposed");
        }
    }

    /** Повідомляє маршрутизатору клавіатури, що відкрите подання ізолює глобальні команди редактора. */
    @Override
    public boolean blocksEditorShortcuts() {
        return host != null && isVisible();
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

    /** Встановлює геометрію картки та заголовка незалежно від стилів інших вікон. */
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

    /** Створює локалізовані рядки параметрів у заданому порядку та налаштовує вертикальне прокручування. */
    private void configureSettingsRows() {
        settingsRows.setMinSize(0.0, 0.0);
        settingsRows.setFillWidth(true);
        addSetting(createSettingRow(SETTINGS_BUNDLE_KEY_PREFIX + "name", nameField));
        addSetting(createSettingRow("neuron.settings.signal_strength", signalField));
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

    /** Створює рядок із гнучким підписом і полем введення для вирівнювання колонок налаштувань. */
    private HBox createSettingRow(String labelKey, TextField field) {
        Label label = new Label(labelText(labelKey));
        settingLabels.put(labelKey, label);
        label.getStyleClass().add("neuron-settings-label");
        label.setWrapText(true);
        label.setMinWidth(0.0);
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        field.getStyleClass().add("neuron-settings-field");
        field.setPrefColumnCount(10);
        field.setMinWidth(INPUT_COLUMN_WIDTH);
        field.setPrefWidth(INPUT_COLUMN_WIDTH);
        field.setMaxWidth(INPUT_COLUMN_WIDTH);
        HBox row = new HBox(16.0, label, field);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9.0, 10.0, 9.0, 10.0));
        row.setMinWidth(0.0);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("neuron-settings-row");
        return row;
    }

    /** Отримує текст підпису з виділеного набору рядків налаштувань або зі спільного набору інтерфейсу. */
    private String labelText(String labelKey) {
        if (labelKey.startsWith(SETTINGS_BUNDLE_KEY_PREFIX)) {
            return localization.textFrom(
                    LOCALIZATION_BUNDLE,
                    labelKey.substring(SETTINGS_BUNDLE_KEY_PREFIX.length())
            );
        }
        return localization.text(labelKey);
    }

    /** Налаштовує кнопки підтвердження та скасування з незалежним стилем подання. */
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

    /** Оновлює всі локалізовані тексти під час показу накладки або зміни мови. */
    private void refreshTexts() {
        title.setText(localization.textFrom(LOCALIZATION_BUNDLE, "title"));
        settingLabels.forEach((key, label) -> label.setText(labelText(key)));
        saveButton.setText(localization.text("neuron.settings.save"));
        cancelButton.setText(localization.textFrom(LOCALIZATION_BUNDLE, "cancel"));
        settingsForm.setPromptText(localization.text("neuron.settings.positive_integer"));
        settingsRows.requestLayout();
        settingsLayout.updateViewportHeight();
        if (getScene() != null) {
            ContentSizedButtonPair.sizeToLargestContent(saveButton, cancelButton);
            requestLayout();
            if (host != null) {
                host.requestLayout();
            }
        }
    }

    /** Підлаштовує картку за природним розміром і обмежує її доступною площею. */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        settingsLayout.layoutChildren();
    }

    /** Реєструє слухача локалізації лише на час показу накладки. */
    private void startListeningToLocalization() {
        if (localizationListenerRegistered) {
            return;
        }
        localization.addListener(localizationListener);
        localizationListenerRegistered = true;
    }

    /** Знімає слухача локалізації під час закриття, не утримуючи приховане подання. */
    private void stopListeningToLocalization() {
        if (!localizationListenerRegistered) {
            return;
        }
        localization.removeListener(localizationListener);
        localizationListenerRegistered = false;
    }

    /** Зберігає перевірені значення й закриває накладку перед передаванням результату координатору. */
    private void save() {
        Optional<NeuronParameters> parameters = settingsForm.parameters();
        if (parameters.isEmpty() || saveAction == null) {
            return;
        }
        BiConsumer<NeuronParameters, String> action = saveAction;
        NeuronParameters savedParameters = parameters.orElseThrow();
        String savedName = settingsForm.name();
        close();
        action.accept(savedParameters, savedName);
    }

    /** Закриває накладку, очищує callback та звільняє тимчасові ресурси. */
    private void close() {
        StackPane previousHost = host;
        host = null;
        saveAction = null;
        stopFilteringSceneKeyboard();
        stopListeningToLocalization();
        setVisible(false);
        setManaged(false);
        if (previousHost != null) {
            previousHost.getChildren().remove(this);
        }
    }

    /** Блокує клавіатурну взаємодію поза накладкою; Escape скасовує редагування. */
    private void filterOutsideModalKeyboardEvents(KeyEvent event) {
        if (!blocksEditorShortcuts()) {
            return;
        }
        if (event.getEventType() == KeyEvent.KEY_PRESSED && event.getCode() == KeyCode.ESCAPE) {
            event.consume();
            close();
            return;
        }
        if (EditorKeyboardShortcutBoundary.blocksShortcutsFor(event.getTarget())) {
            return;
        }
        event.consume();
    }

    /** Реєструє фільтр сцени лише на час показу модальної накладки. */
    private void startFilteringSceneKeyboard(Scene scene) {
        if (keyboardFilterScene == scene) {
            return;
        }
        stopFilteringSceneKeyboard();
        if (scene != null) {
            SceneKeyboardModality.register(scene, this);
            scene.addEventFilter(KeyEvent.ANY, sceneKeyboardFilter);
            keyboardFilterScene = scene;
        }
    }

    /** Знімає фільтр сцени, щоб після закриття накладки клавіатура працювала звичайно. */
    private void stopFilteringSceneKeyboard() {
        if (keyboardFilterScene == null) {
            return;
        }
        Scene previousScene = keyboardFilterScene;
        keyboardFilterScene = null;
        previousScene.removeEventFilter(KeyEvent.ANY, sceneKeyboardFilter);
        SceneKeyboardModality.unregister(previousScene, this);
    }

    /** Повертає поле назви для регресійних перевірок обмеження довжини й завантаження значення. */
    TextField nameFieldForTest() { return nameField; }

    /** Повертає поле сили сигналу для регресійних тестів. */
    TextField signalFieldForTest() { return signalField; }

    /** Повертає поле порога активації для регресійних тестів. */
    TextField thresholdFieldForTest() { return thresholdField; }

    /** Повертає поле кількості активних тактів для регресійних тестів. */
    TextField activeTicksFieldForTest() { return activeTicksField; }

    /** Повертає поле затримки для перевірки завантаження й валідації параметра. */
    TextField delayTicksFieldForTest() { return delayTicksField; }

    /** Повертає кнопку збереження для перевірок введення та геометрії. */
    Button saveButtonForTest() { return saveButton; }

    /** Повертає кнопку скасування для перевірок геометрії та закриття. */
    Button cancelButtonForTest() { return cancelButton; }

    /** Повертає прокручувану область для тестів компонування. */
    ScrollPane settingsScrollForTest() { return settingsScroll; }

    /** Повертає картку налаштувань для тестів адаптивного компонування. */
    VBox panelForTest() { return panel; }

    /** Повертає заголовок для перевірки його вирівнювання. */
    Label titleForTest() { return title; }

    /** Повертає затемнений фон для перевірки скасування натисканням поза карткою. */
    Node backdropForTest() { return backdrop; }

    /** Повертає локалізований підпис параметра без повторного відкриття накладки. */
    String settingLabelTextForTest(String key) {
        Label label = settingLabels.get(key);
        return label == null ? null : label.getText();
    }

    /** Повертає максимальну ширину панелі. */
    double panelMaxWidthForTest() { return panel.getMaxWidth(); }

    /** Повертає максимальну висоту панелі. */
    double panelMaxHeightForTest() { return panel.getMaxHeight(); }

    /** Повертає підказку поля сили сигналу. */
    String signalPromptForTest() { return signalField.getPromptText(); }

    /** Повертає підказку поля порога активації. */
    String thresholdPromptForTest() { return thresholdField.getPromptText(); }
}
