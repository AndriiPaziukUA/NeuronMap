package com.example.neuronmap.view;

import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Створює головне меню, сторінки збережених проєктів і налаштувань та підтвердження незбережених змін.
 * Розмір панелі підлаштовується до вмісту; сторінка проєктів використовує максимальну висоту за наявності місця.
 */
public final class MainMenuView extends StackPane {

    private static final double MAX_PANEL_WIDTH = 460.0;
    private static final double MAX_PANEL_HEIGHT = 430.0;

    /**
     * Зберігає зворотні виклики навігації меню та операцій над проєктами.
     *
     * @param newProject дія створення проєкту.
     * @param load дія відкриття сторінки збережених проєктів.
     * @param settings дія відкриття сторінки налаштувань.
     * @param exit дія завершення роботи застосунку.
     * @param close дія закриття меню.
     * @param openProject дія відкриття вибраного проєкту.
     * @param renameProject дія перейменування проєкту з поверненням оновленого опису.
     * @param deleteProject дія видалення проєкту.
     * @param back дія повернення на попередню сторінку меню.
     */
    public record Actions(
            Runnable newProject,
            Runnable load,
            Runnable settings,
            Runnable exit,
            Runnable close,
            Consumer<ProjectDescriptor> openProject,
            BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameProject,
            Consumer<ProjectDescriptor> deleteProject,
            Runnable back
    ) {
        /**
         * Перевіряє та зберігає всі зворотні виклики дій меню.
         *
         * @param newProject дія створення проєкту.
         * @param load дія відкриття списку збережених проєктів.
         * @param settings дія відкриття налаштувань.
         * @param exit дія завершення роботи застосунку.
         * @param close дія закриття меню.
         * @param openProject дія відкриття вибраного проєкту.
         * @param renameProject дія перейменування проєкту.
         * @param deleteProject дія видалення проєкту.
         * @param back дія повернення на попередню сторінку.
         */
        public Actions {
            newProject = Objects.requireNonNull(newProject, "newProject");
            load = Objects.requireNonNull(load, "load");
            settings = Objects.requireNonNull(settings, "settings");
            exit = Objects.requireNonNull(exit, "exit");
            close = Objects.requireNonNull(close, "close");
            openProject = Objects.requireNonNull(openProject, "openProject");
            renameProject = Objects.requireNonNull(renameProject, "renameProject");
            deleteProject = Objects.requireNonNull(deleteProject, "deleteProject");
            back = Objects.requireNonNull(back, "back");
        }
    }

    /** Позначає сторінку, яку наразі показує меню. */
    private enum Page {
        MAIN,
        LOAD,
        SETTINGS
    }

    private final LocalizationService localization;
    private final VBox panel = new VBox(14.0);
    private final VBox content = new VBox(10.0);
    private final VBox projectList = new VBox(6.0);
    private final HBox footer = new HBox(10.0);
    private final Region footerSpacer = new Region();
    private final Label titleLabel = new Label();
    private final Label languageLabel = new Label();
    private final Label emptyProjectsLabel = new Label();
    private final Button continueButton = menuButton();
    private final Button newProjectButton = menuButton();
    private final Button loadButton = menuButton();
    private final Button settingsButton = menuButton();
    private final Button exitButton = menuButton();
    private final Button backButton = secondaryButton();
    private final Button saveSettingsButton = menuButton();
    private final ComboBox<SupportedLanguage> languageSelector = new ComboBox<>();
    private final Rectangle backdrop = new Rectangle();
    private final UnsavedChangesView unsavedChangesView;

    private final Consumer<java.util.Locale> localizationListener = ignored -> refreshTexts();
    private Actions actions = new Actions(
            () -> { }, () -> { }, () -> { }, () -> { }, () -> { },
            ignored -> { }, (ignored, ignored2) -> null, ignored -> { }, () -> { }
    );
    private Page page = Page.MAIN;
    private List<SavedProjectRowView> projectRows = List.of();
    private ScrollPane projectScrollPane;
    private SupportedLanguage settingsOriginalLanguage;
    private boolean savingPendingChanges;
    private Consumer<Boolean> visibilityChanged = ignored -> { };
    private Runnable persistLanguageAction = () -> { };

    /**
     * Створює подання меню та підключає локалізацію й сповіщення про зміну видимості.
     *
     * @param localization служба локалізації текстів і активної мови.
     * @param visibilityChanged зворотний виклик при показі або приховуванні меню; може бути {@code null}.
     */
    public MainMenuView(
            LocalizationService localization,
            Consumer<Boolean> visibilityChanged
    ) {
        this.localization = Objects.requireNonNull(localization, "localization");
        this.unsavedChangesView = new UnsavedChangesView(localization);
        if (visibilityChanged != null) {
            this.visibilityChanged = visibilityChanged;
        }

        setVisible(false);
        setManaged(false);
        setPickOnBounds(true);
        getStyleClass().add("main-menu-overlay");
        URL menuStyles = getClass().getResource("/menu-enhancements.css");
        if (menuStyles != null) {
            getStylesheets().add(menuStyles.toExternalForm());
        }

        backdrop.widthProperty().bind(widthProperty());
        backdrop.heightProperty().bind(heightProperty());
        backdrop.setFill(Color.rgb(0, 0, 0, 0.5));
        backdrop.setOnMouseClicked(event -> {
            actions.close().run();
            event.consume();
        });

        panel.getStyleClass().add("main-menu-panel");
        panel.setMinSize(0.0, 0.0);
        panel.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        panel.setMaxSize(MAX_PANEL_WIDTH, MAX_PANEL_HEIGHT);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setFocusTraversable(true);
        StackPane.setAlignment(panel, Pos.CENTER);

        titleLabel.getStyleClass().add("main-menu-title");
        emptyProjectsLabel.getStyleClass().add("main-menu-hint");
        languageLabel.getStyleClass().add("main-menu-label");
        content.setAlignment(Pos.TOP_CENTER);
        content.setMinHeight(0.0);
        VBox.setVgrow(content, Priority.ALWAYS);
        footer.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(footer, Priority.NEVER);
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);

        backButton.setOnAction(event -> {
            actions.back().run();
            event.consume();
        });
        saveSettingsButton.setOnAction(event -> {
            saveSettings();
            event.consume();
        });

        languageSelector.setMaxWidth(Double.MAX_VALUE);
        languageSelector.setPrefWidth(250.0);
        languageSelector.setCellFactory(list -> languageCell());
        languageSelector.setButtonCell(languageCell());
        languageSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(SupportedLanguage language) {
                return language == null ? "" : localization.displayName(language);
            }

            @Override
            public SupportedLanguage fromString(String value) {
                return null;
            }
        });
        languageSelector.setOnAction(event -> {
            SupportedLanguage selected = languageSelector.getValue();
            if (selected != null && selected != localization.language()) {
                languageSelector.hide();
                localization.previewLanguage(selected);
                updateSaveButtonVisibility();
            }
            event.consume();
        });

        continueButton.setOnAction(event -> {
            actions.close().run();
            event.consume();
        });
        newProjectButton.setOnAction(event -> {
            actions.newProject().run();
            event.consume();
        });
        loadButton.setOnAction(event -> {
            actions.load().run();
            event.consume();
        });
        settingsButton.setOnAction(event -> {
            actions.settings().run();
            event.consume();
        });
        exitButton.setOnAction(event -> {
            actions.exit().run();
            event.consume();
        });

        getChildren().addAll(backdrop, panel, unsavedChangesView);
        localization.addListener(localizationListener);
        refreshTexts();
        showMainPage();
    }

    /**
     * Замінює зворотні виклики, які використовують елементи меню.
     *
     * @param actions зворотні виклики навігації, завершення роботи та операцій із проєктами.
     */
    public void setActions(Actions actions) {
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    /**
     * Встановлює дію збереження мови, яку виконує контролер застосунку.
     *
     * @param action дія збереження поточної мови.
     */
    public void setPersistLanguageAction(Runnable action) {
        this.persistLanguageAction = Objects.requireNonNull(action, "action");
    }

    /**
     * Розміщує накладку меню, підлаштовуючи висоту сторінок до їхнього вмісту й зберігаючи потрібний розмір списку проєктів.
     */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (getWidth() <= 0.0 || getHeight() <= 0.0) {
            return;
        }

        double availableWidth = Math.max(0.0, getWidth() - 32.0);
        double availableHeight = Math.max(0.0, getHeight() - 32.0);
        double preferredWidth = Math.max(panel.minWidth(-1.0), panel.prefWidth(-1.0));
        double panelWidth = Math.min(availableWidth, Math.min(MAX_PANEL_WIDTH, preferredWidth));
        double preferredHeight = Math.max(panel.minHeight(panelWidth), panel.prefHeight(panelWidth));
        double panelHeight = page == Page.LOAD
                ? Math.min(availableHeight, MAX_PANEL_HEIGHT)
                : Math.min(availableHeight, Math.min(MAX_PANEL_HEIGHT, preferredHeight));

        panel.resizeRelocate(
                Math.max(0.0, (getWidth() - panelWidth) / 2.0),
                Math.max(0.0, (getHeight() - panelHeight) / 2.0),
                panelWidth,
                panelHeight
        );
        panel.layout();
    }

    /**
     * Перевіряє, чи відображається накладка меню.
     *
     * @return {@code true}, якщо меню видиме.
     */
    public boolean isMenuVisible() {
        return isVisible();
    }

    /** Показує меню та сповіщає власника про його відкриття. */
    public void showMenu() {
        setVisible(true);
        setManaged(true);
        visibilityChanged.accept(true);
        restoreMenuPanelFocus();
    }

    /** Приховує меню й діалог підтвердження та сповіщає власника про закриття. */
    public void hide() {
        if (!isVisible()) {
            return;
        }
        unsavedChangesView.hide();
        setVisible(false);
        setManaged(false);
        visibilityChanged.accept(false);
    }

    /**
     * Перевіряє наявність незбережених змін на сторінках меню.
     *
     * @return {@code true}, якщо змінилися налаштування мови або назва проєкту.
     */
    public boolean hasUnsavedChanges() {
        return settingsHaveUnsavedChanges()
                || projectRows.stream().anyMatch(SavedProjectRowView::hasUnsavedChanges);
    }

    /**
     * Перевіряє, чи показано підтвердження незбережених змін.
     *
     * @return {@code true}, якщо діалог підтвердження видимий.
     */
    public boolean isConfirmingUnsavedChanges() {
        return unsavedChangesView.isShowing();
    }

    /**
     * Показує підтвердження незбережених змін перед дією, яка може їх втратити.
     *
     * @param saveAction дія після збереження незавершених змін.
     * @param discardAction дія після відхилення незавершених змін.
     */
    public void confirmUnsavedChanges(Runnable saveAction, Runnable discardAction) {
        unsavedChangesView.show(saveAction, discardAction);
    }

    /** Вибирає варіант відхилення змін і продовження дії в активному діалозі. */
    public void discardAndContinueUnsavedChanges() {
        unsavedChangesView.discardAndContinue();
    }

    /** Зберігає незавершені зміни назв проєктів і попередньо вибрану мову. */
    public void saveUnsavedChanges() {
        savingPendingChanges = true;
        try {
            for (SavedProjectRowView row : List.copyOf(projectRows)) {
                row.savePendingChange();
            }
            if (settingsHaveUnsavedChanges()) {
                saveSettings();
            }
        } finally {
            savingPendingChanges = false;
        }
    }

    /** Відхиляє незавершені зміни назв проєктів і повертає мову до збереженого стану. */
    public void discardUnsavedChanges() {
        for (SavedProjectRowView row : projectRows) {
            row.discardPendingChange();
        }
        if (settingsHaveUnsavedChanges() && settingsOriginalLanguage != null) {
            localization.previewLanguage(settingsOriginalLanguage);
        }
        settingsOriginalLanguage = localization.language();
        unsavedChangesView.hide();
        updateSaveButtonVisibility();
    }

    /** Показує головну сторінку меню без кнопки повернення. */
    public void showMainPage() {
        page = Page.MAIN;
        clearProjectRows();
        updatePageTitle();
        content.getChildren().setAll(
                continueButton,
                newProjectButton,
                loadButton,
                settingsButton,
                exitButton
        );
        installPanel();
    }

    /**
     * Показує сторінку збережених проєктів і створює прокручуваний список.
     *
     * @param projects описи доступних збережених проєктів; {@code null} трактується як порожній список.
     */
    public void showLoadPage(List<ProjectDescriptor> projects) {
        page = Page.LOAD;
        settingsOriginalLanguage = null;
        clearProjectRows();
        updatePageTitle();

        projectScrollPane = new ScrollPane(projectList);
        projectScrollPane.setFitToWidth(true);
        projectScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        projectScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        projectScrollPane.setMinWidth(0.0);
        projectScrollPane.setMinHeight(0.0);
        projectScrollPane.getStyleClass().add("main-menu-scroll");
        int visibleRows = projects == null || projects.isEmpty() ? 1 : projects.size();
        projectScrollPane.setPrefViewportHeight(Math.min(260.0, Math.max(58.0, visibleRows * 50.0)));
        VBox.setVgrow(projectScrollPane, Priority.ALWAYS);

        if (projects == null || projects.isEmpty()) {
            projectList.getChildren().setAll(emptyProjectsLabel);
        } else {
            projectRows = projects.stream()
                    .map(project -> new SavedProjectRowView(
                            project,
                            localization,
                            actions.openProject(),
                            actions.renameProject(),
                            actions.deleteProject(),
                            this::handleRenameCommitted
                    ))
                    .toList();
            projectList.getChildren().setAll(projectRows);
        }
        content.getChildren().setAll(projectScrollPane);
        installPanel();
    }

    /** Показує сторінку налаштувань і запам'ятовує мову для можливого скасування змін. */
    public void showSettingsPage() {
        page = Page.SETTINGS;
        clearProjectRows();
        updatePageTitle();
        settingsOriginalLanguage = localization.language();
        reloadLanguageSelector();

        HBox languageRow = new HBox(12.0, languageLabel, languageSelector);
        languageRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(languageSelector, Priority.ALWAYS);
        content.getChildren().setAll(languageRow);
        installPanel();
    }

    /**
     * Перевіряє, чи активна сторінка збережених проєктів.
     *
     * @return {@code true}, якщо меню показує збережені проєкти.
     */
    public boolean isLoadPage() {
        return page == Page.LOAD;
    }

    /**
     * Перевіряє, чи активна сторінка налаштувань.
     *
     * @return {@code true}, якщо меню показує налаштування.
     */
    public boolean isSettingsPage() {
        return page == Page.SETTINGS;
    }

    /**
     * Повертає підписи кнопок головної сторінки для регресійних тестів інтерфейсу.
     *
     * @return підписи кнопок у відображуваному порядку.
     */
    public List<String> mainButtonTextsForTest() {
        return List.of(
                continueButton.getText(),
                newProjectButton.getText(),
                loadButton.getText(),
                settingsButton.getText(),
                exitButton.getText()
        );
    }

    /**
     * Надає вибір мови для тестів інтерфейсу.
     *
     * @return список вибору мови на сторінці налаштувань.
     */
    public ComboBox<SupportedLanguage> languageSelectorForTest() {
        return languageSelector;
    }

    /**
     * Надає кнопку повернення для тестів інтерфейсу.
     *
     * @return кнопка, яка додається лише внизу підсторінок меню.
     */
    public Button backButtonForTest() {
        return backButton;
    }

    /**
     * Надає кнопку збереження налаштувань для тестів інтерфейсу.
     *
     * @return кнопка збереження налаштувань.
     */
    public Button saveSettingsButtonForTest() {
        return saveSettingsButton;
    }

    /**
     * Повертає заголовок поточної сторінки для регресійних тестів.
     *
     * @return текст у верхній частині активної сторінки меню.
     */
    public String pageTitleForTest() {
        return titleLabel.getText();
    }

    /**
     * Надає прокручуваний список проєктів для тестів інтерфейсу.
     *
     * @return прокручуваний список або {@code null}, якщо сторінку проєктів ще не створено.
     */
    ScrollPane projectScrollPaneForTest() {
        return projectScrollPane;
    }

    /**
     * Надає панель меню для тестів відновлення фокусу.
     *
     * @return контейнер панелі, який може отримувати фокус.
     */
    Region menuPanelForTest() {
        return panel;
    }

    /**
     * Повертає максимальну ширину панелі для тестів адаптивного розташування.
     *
     * @return налаштована максимальна ширина панелі в пікселях.
     */
    double panelMaxWidthForTest() {
        return panel.getMaxWidth();
    }

    /**
     * Повертає поточну фактичну ширину панелі для регресійних тестів.
     *
     * @return ширина панелі в пікселях.
     */
    double panelWidthForTest() {
        return panel.getWidth();
    }

    /**
     * Повертає поточну фактичну висоту панелі для регресійних тестів.
     *
     * @return висота панелі в пікселях.
     */
    double panelHeightForTest() {
        return panel.getHeight();
    }

    /**
     * Повертає максимальну висоту панелі для адаптивних тестів.
     *
     * @return налаштована максимальна висота панелі в пікселях.
     */
    double panelMaxHeightForTest() {
        return panel.getMaxHeight();
    }

    /**
     * Повертає рядки збережених проєктів для тестів інтерфейсу.
     *
     * @return незмінна копія поточного списку рядків проєктів.
     */
    List<SavedProjectRowView> projectRowsForTest() {
        return List.copyOf(projectRows);
    }

    /** Звільняє ресурси рядків проєктів і від'єднує слухача локалізації. */
    public void dispose() {
        clearProjectRows();
        unsavedChangesView.hide();
        localization.removeListener(localizationListener);
    }

    /**
     * Зберігає вибрану мову через callback контролера та оновлює базовий стан налаштувань.
     */
    public void saveSettings() {
        if (!isSettingsPage()) {
            return;
        }
        persistLanguageAction.run();
        settingsOriginalLanguage = localization.language();
        updateSaveButtonVisibility();
    }

    /**
     * Перевіряє, чи відрізняється вибрана мова від збереженого стану.
     *
     * @return {@code true}, якщо мову вибрано, але ще не збережено.
     */
    private boolean settingsHaveUnsavedChanges() {
        return page == Page.SETTINGS
                && settingsOriginalLanguage != null
                && settingsOriginalLanguage != localization.language();
    }

    /** Оновлює список проєктів після підтвердженого перейменування поза пакетним збереженням змін. */
    private void handleRenameCommitted() {
        if (!savingPendingChanges && isLoadPage()) {
            actions.load().run();
        }
    }

    /** Формує нижню панель сторінки та розміщує елементи відповідно до активної сторінки. */
    private void installPanel() {
        if (page == Page.MAIN) {
            footer.getChildren().clear();
            panel.getChildren().setAll(titleLabel, new Separator(), content);
        } else {
            footer.getChildren().setAll(backButton, footerSpacer, saveSettingsButton);
            panel.getChildren().setAll(titleLabel, new Separator(), content, footer);
        }
        updateSaveButtonVisibility();
        content.requestLayout();
        panel.requestLayout();
        requestLayout();
        restoreMenuPanelFocus();
    }

    /**
     * Переносить клавіатурний фокус із попередньої кнопки на панель після переходу між сторінками.
     */
    private void restoreMenuPanelFocus() {
        if (!isMenuVisible() || panel.getScene() == null) {
            return;
        }
        panel.requestFocus();
        Platform.runLater(() -> {
            if (isMenuVisible() && panel.getScene() != null) {
                panel.requestFocus();
            }
        });
    }

    /** Звільняє слухачів рядків і очищує поточний список проєктів. */
    private void clearProjectRows() {
        for (SavedProjectRowView row : projectRows) {
            row.dispose();
        }
        projectRows = List.of();
        projectList.getChildren().clear();
        if (projectScrollPane != null) {
            projectScrollPane.setContent(null);
        }
        content.getChildren().clear();
        projectScrollPane = null;
    }

    /** Перезавантажує список мов і вибирає активну мову. */
    private void reloadLanguageSelector() {
        SupportedLanguage current = localization.language();
        languageSelector.getItems().setAll(localization.supportedLanguagesInDisplayOrder());
        languageSelector.setValue(current);
    }

    /** Оновлює видимий текст та узгоджує кнопку збереження зі станом незбережених змін. */
    private void refreshTexts() {
        updatePageTitle();
        continueButton.setText(localization.text("menu.continue"));
        newProjectButton.setText(localization.text("menu.new_project"));
        loadButton.setText(localization.text("menu.load"));
        settingsButton.setText(localization.text("menu.settings"));
        exitButton.setText(localization.text("menu.exit"));
        backButton.setText(localization.text("menu.back"));
        saveSettingsButton.setText(localization.text("common.save"));
        emptyProjectsLabel.setText(localization.text("menu.no_saved_projects"));
        languageLabel.setText(localization.text("menu.language"));
        unsavedChangesView.refreshTexts();
        reloadLanguageSelector();
        updateSaveButtonVisibility();
    }

    /** Оновлює заголовок відповідно до поточної сторінки меню. */
    private void updatePageTitle() {
        String title = switch (page) {
            case MAIN -> "NeuronMap";
            case LOAD -> localization.text("menu.saved_projects");
            case SETTINGS -> localization.text("menu.settings");
        };
        titleLabel.setText(title);
    }

    /** Показує або приховує кнопку збереження залежно від наявності змін у налаштуваннях. */
    private void updateSaveButtonVisibility() {
        boolean showSave = settingsHaveUnsavedChanges();
        saveSettingsButton.setVisible(showSave);
        saveSettingsButton.setManaged(showSave);
    }

    /** Створює елемент списку мов із локалізованою назвою кожної мови. */
    private ListCell<SupportedLanguage> languageCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SupportedLanguage item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : localization.displayName(item));
            }
        };
    }

    /** Створює основну кнопку меню на всю доступну ширину. */
    private static Button menuButton() {
        Button button = new Button();
        button.getStyleClass().add("main-menu-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(46.0);
        button.setPrefHeight(46.0);
        button.setMaxHeight(46.0);
        return button;
    }

    /** Створює допоміжну кнопку повернення для підсторінок меню. */
    private static Button secondaryButton() {
        Button button = new Button();
        button.getStyleClass().add("main-menu-back-button");
        button.setMinHeight(38.0);
        button.setPrefHeight(38.0);
        return button;
    }
}
