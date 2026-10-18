package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.view.menu.MainMenuView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Відображає налаштування нейрона як внутрішню модальну накладку головного вікна.
 * Оформлення накладки, зокрема кнопок, визначається окремою таблицею стилів.
 */
public final class NeuronSettingsView extends StackPane {

    private static final String STYLESHEET_RESOURCE = "/neuron-settings.css";
    private static final double MAX_PANEL_WIDTH = 920.0;
    private static final double MAX_PANEL_HEIGHT = 430.0;
    private static final double MAX_SETTINGS_VIEWPORT_HEIGHT = 260.0;
    private static final double SCROLLBAR_RESERVED_WIDTH = 9.0;
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
    private final TextField signalField = createNumericField();
    private final TextField thresholdField = createNumericField();

    private StackPane host;
    private Consumer<NeuronParameters> saveAction;

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
        configureSettingsRows();
        configureFooter();
        getChildren().addAll(backdrop, panel);
        localization.addListener(localizationListener);
        refreshTexts();
    }

    /**
     * Додає рядок до прокручуваного списку та оновлює його природну висоту.
     *
     * @param settingRow готовий вузол одного параметра.
     * @throws NullPointerException якщо рядок не задано.
     */
    public void addSetting(Node settingRow) {
        settingsRows.getChildren().add(Objects.requireNonNull(settingRow, "settingRow"));
        updateViewportHeight();
        requestLayout();
    }

    /**
     * Показує накладку в головній сцені та завантажує поточні параметри нейрона.
     *
     * @param applicationRoot кореневий контейнер для внутрішніх накладок.
     * @param neuron нейрон, параметри якого редагують.
     * @param onSave дія застосування підтверджених параметрів.
     * @throws NullPointerException якщо будь-який обов'язковий аргумент не задано.
     * @throws IllegalStateException якщо накладка вже відкрита в іншому контейнері.
     */
    public void showIn(StackPane applicationRoot, Neuron neuron, Consumer<NeuronParameters> onSave) {
        Objects.requireNonNull(applicationRoot, "applicationRoot");
        Objects.requireNonNull(neuron, "neuron");
        Objects.requireNonNull(onSave, "onSave");
        if (host != null) {
            throw new IllegalStateException("Neuron settings view is already showing");
        }

        signalField.setText(Integer.toString(neuron.signalStrength()));
        thresholdField.setText(Integer.toString(neuron.activationThreshold()));
        signalField.deselect();
        thresholdField.deselect();
        signalField.positionCaret(signalField.getLength());
        thresholdField.positionCaret(thresholdField.getLength());
        saveAction = onSave;
        host = applicationRoot;
        refreshTexts();
        validateInput();
        setManaged(true);
        setVisible(true);

        int insertionIndex = applicationRoot.getChildren().size();
        for (int index = 0; index < applicationRoot.getChildren().size(); index++) {
            if (applicationRoot.getChildren().get(index) instanceof MainMenuView) {
                insertionIndex = index;
                break;
            }
        }
        applicationRoot.getChildren().add(insertionIndex, this);
        applyInitialLayout(applicationRoot);
        requestFocus();
    }

    /** Закриває накладку, якщо вона відображається. */
    public void hide() {
        close();
    }

    /** Налаштовує затемнений фон, клацання по якому скасовує редагування. */
    private void configureBackdrop() {
        backdrop.setManaged(false);
        backdrop.widthProperty().bind(widthProperty());
        backdrop.heightProperty().bind(heightProperty());
        backdrop.setFill(Color.rgb(0, 0, 0, 0.5));
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
        panel.setMaxSize(MAX_PANEL_WIDTH, MAX_PANEL_HEIGHT);
        StackPane.setAlignment(panel, Pos.CENTER);

        title.getStyleClass().add("neuron-settings-title");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
    }

    /** Створює два початкові рядки та налаштовує вертикальне прокручування за потреби. */
    private void configureSettingsRows() {
        settingsRows.setMinSize(0.0, 0.0);
        settingsRows.setFillWidth(true);
        addSetting(createSettingRow("neuron.settings.signal_strength", signalField));
        addSetting(createSettingRow("neuron.settings.activation_threshold", thresholdField));

        settingsScroll.setFitToWidth(true);
        settingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        settingsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        settingsScroll.setMinSize(0.0, 0.0);
        settingsScroll.getStyleClass().add("neuron-settings-scroll");
        VBox.setVgrow(settingsScroll, Priority.ALWAYS);

        signalField.textProperty().addListener((observable, oldValue, newValue) -> validateInput());
        thresholdField.textProperty().addListener((observable, oldValue, newValue) -> validateInput());
    }

    /**
     * Створює рядок із гнучким підписом і полем фіксованої ширини для вирівнювання колонок.
     *
     * @param labelKey ключ локалізованого підпису параметра.
     * @param field поле введення параметра.
     * @return підготовлений рядок налаштування.
     */
    private HBox createSettingRow(String labelKey, TextField field) {
        Label label = new Label(localization.text(labelKey));
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

    /** Налаштовує пару кнопок з однаковим окремим стилем і без підказок. */
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
        sizeActionButtonsToLargestContent();
        updateViewportHeight();
        requestLayout();
        applicationRoot.requestLayout();
        applicationRoot.layout();
        updateViewportHeight();
        requestLayout();
        applicationRoot.requestLayout();
        applicationRoot.layout();
    }

    /** Обчислює природну висоту списку, обмежуючи лише viewport, а не рядки. */
    private void updateViewportHeight() {
        Insets insets = settingsRows.getInsets();
        double rowWidth = settingsScroll.getWidth() > 0.0
                ? Math.max(0.0, settingsScroll.getWidth() - SCROLLBAR_RESERVED_WIDTH)
                : Math.max(0.0, MAX_PANEL_WIDTH - panel.getInsets().getLeft()
                        - panel.getInsets().getRight() - SCROLLBAR_RESERVED_WIDTH);
        double contentHeight = insets.getTop() + insets.getBottom();
        int visibleRows = 0;
        for (Node row : settingsRows.getChildren()) {
            if (!row.isManaged()) {
                continue;
            }
            double rowHeight = row instanceof Region region
                    ? Math.max(region.minHeight(rowWidth), region.prefHeight(rowWidth))
                    : row.getLayoutBounds().getHeight();
            if (Double.isFinite(rowHeight) && rowHeight > 0.0) {
                contentHeight += rowHeight;
            }
            visibleRows++;
        }
        if (visibleRows > 1) {
            contentHeight += (visibleRows - 1) * settingsRows.getSpacing();
        }
        contentHeight = Math.max(contentHeight, settingsRows.prefHeight(rowWidth));
        settingsScroll.setPrefViewportHeight(Math.min(MAX_SETTINGS_VIEWPORT_HEIGHT, contentHeight));
    }

    /** Вирівнює розміри двох кнопок за природними розмірами найбільшого підпису після CSS. */
    private void sizeActionButtonsToLargestContent() {
        resetButtonDimensions(saveButton);
        resetButtonDimensions(cancelButton);
        saveButton.applyCss();
        cancelButton.applyCss();

        double width = Math.max(saveButton.prefWidth(-1.0), cancelButton.prefWidth(-1.0));
        double height = Math.max(saveButton.prefHeight(width), cancelButton.prefHeight(width));
        applyButtonDimensions(saveButton, width, height);
        applyButtonDimensions(cancelButton, width, height);
    }

    /** Повертає розміри кнопки до природного розрахунку перед вимірюванням її тексту. */
    private static void resetButtonDimensions(Button button) {
        button.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
    }

    /** Встановлює обом кнопкам єдині розміри, обчислені за більшим природним розміром. */
    private static void applyButtonDimensions(Button button, double width, double height) {
        // Фіксується лише мінімум за найбільшим підписом; preferred/max залишаються обчислюваними.
        button.setMinSize(width, height);
        button.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
    }

    /** Підлаштовує розмір панелі до природного вмісту з обмеженнями адаптивного макета меню. */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (getWidth() <= 0.0 || getHeight() <= 0.0) {
            return;
        }

        double availableWidth = Math.max(0.0, getWidth() - 32.0);
        double availableHeight = Math.max(0.0, getHeight() - 32.0);
        double preferredWidth = preferredWidthForLayout();
        double panelWidth = Math.min(availableWidth, Math.min(MAX_PANEL_WIDTH, preferredWidth));
        double preferredHeight = Math.max(panel.minHeight(panelWidth), panel.prefHeight(panelWidth));
        double panelHeight = Math.min(availableHeight, Math.min(MAX_PANEL_HEIGHT, preferredHeight));
        panel.resizeRelocate(
                Math.max(0.0, (getWidth() - panelWidth) / 2.0),
                Math.max(0.0, (getHeight() - panelHeight) / 2.0),
                panelWidth,
                panelHeight
        );
        panel.layout();
    }

    /** Обчислює ширину за природним розміром вмісту та додатковим місцем для scrollbar. */
    private double preferredWidthForLayout() {
        double preferredWidth = Math.max(panel.minWidth(-1.0), panel.prefWidth(-1.0));
        Insets panelInsets = panel.getInsets();
        Insets rowInsets = settingsRows.getInsets();
        Insets scrollInsets = settingsScroll.getInsets();
        double rowsWidth = Math.max(settingsRows.minWidth(-1.0), settingsRows.prefWidth(-1.0));
        double requiredRowsWidth = rowsWidth
                + rowInsets.getLeft() + rowInsets.getRight()
                + scrollInsets.getLeft() + scrollInsets.getRight()
                + SCROLLBAR_RESERVED_WIDTH;
        double requiredPanelWidth = requiredRowsWidth + panelInsets.getLeft() + panelInsets.getRight();
        return Math.max(preferredWidth, requiredPanelWidth);
    }

    /** Оновлює заголовок, підписи параметрів і кнопки одразу після зміни активної локалі. */
    private void refreshTexts() {
        title.setText(localization.text("neuron.settings.title"));
        settingLabels.forEach((key, label) -> label.setText(localization.text(key)));
        saveButton.setText(localization.text("neuron.settings.save"));
        cancelButton.setText(localization.text("menu.rename.cancel"));
        settingsRows.requestLayout();
        updateViewportHeight();
        if (getScene() != null) {
            sizeActionButtonsToLargestContent();
            requestLayout();
            if (host != null) {
                host.requestLayout();
            }
        }
    }

    /** Перевіряє обидва поля й керує доступністю підтвердження. */
    private void validateInput() {
        saveButton.setDisable(
                parsePositive(signalField.getText()).isEmpty()
                        || parsePositive(thresholdField.getText()).isEmpty()
        );
    }

    /**
     * Створює поле, яке відхиляє символи, що не є десятковими цифрами.
     *
     * @return поле введення з фільтром цифр.
     */
    private static TextField createNumericField() {
        TextField field = new TextField();
        UnaryOperator<TextFormatter.Change> digitsOnly = change ->
                change.getControlNewText().matches("\\d*") ? change : null;
        field.setTextFormatter(new TextFormatter<>(digitsOnly));
        return field;
    }

    /**
     * Перетворює текст поля на додатне ціле число в межах int.
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

    /** Зберігає перевірені параметри й закриває накладку перед передаванням результату. */
    private void save() {
        OptionalInt signalStrength = parsePositive(signalField.getText());
        OptionalInt activationThreshold = parsePositive(thresholdField.getText());
        if (signalStrength.isEmpty() || activationThreshold.isEmpty() || saveAction == null) {
            validateInput();
            return;
        }
        Consumer<NeuronParameters> action = saveAction;
        NeuronParameters parameters = new NeuronParameters(
                signalStrength.getAsInt(),
                activationThreshold.getAsInt()
        );
        close();
        action.accept(parameters);
    }

    /** Прибирає накладку з кореня сцени та звільняє посилання на callback-и. */
    private void close() {
        StackPane previousHost = host;
        host = null;
        saveAction = null;
        setVisible(false);
        setManaged(false);
        if (previousHost != null) {
            previousHost.getChildren().remove(this);
        }
    }

    /** Повертає поле сили сигналу для регресійних тестів. */
    TextField signalFieldForTest() {
        return signalField;
    }

    /** Повертає поле порога активації для регресійних тестів. */
    TextField thresholdFieldForTest() {
        return thresholdField;
    }

    /** Повертає кнопку збереження для перевірок введення та геометрії. */
    Button saveButtonForTest() {
        return saveButton;
    }

    /** Повертає кнопку скасування для перевірок геометрії та закриття. */
    Button cancelButtonForTest() {
        return cancelButton;
    }

    /** Повертає прокручувану область для тестів компонування. */
    ScrollPane settingsScrollForTest() {
        return settingsScroll;
    }

    /** Повертає картку налаштувань для тестів адаптивного компонування. */
    VBox panelForTest() {
        return panel;
    }

    /** Повертає заголовок для перевірки його вирівнювання. */
    Label titleForTest() {
        return title;
    }

    /** Повертає локалізований підпис параметра для перевірки оновлення мови без повторного відкриття.
     *
     * @param key ключ перекладу підпису.
     * @return поточний текст підпису або {@code null}, якщо параметр із таким ключем не створено.
     */
    String settingLabelTextForTest(String key) {
        Label label = settingLabels.get(key);
        return label == null ? null : label.getText();
    }

    /** Повертає максимальну ширину панелі. */
    double panelMaxWidthForTest() {
        return panel.getMaxWidth();
    }

    /** Повертає максимальну висоту панелі. */
    double panelMaxHeightForTest() {
        return panel.getMaxHeight();
    }

    /** Повертає фактичну ширину панелі після компонування. */
    double panelWidthForTest() {
        return panel.getWidth();
    }

    /** Повертає фактичну висоту панелі після компонування. */
    double panelHeightForTest() {
        return panel.getHeight();
    }
}
