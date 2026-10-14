package com.example.neuronmap.view;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.project.ProjectDescriptor;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Компонує спеціалізовані сторінки меню, спільну панель і діалог незбережених змін.
 * Відображення сторінок доручене окремим компонентам, а операції над проєктами та мовою передаються через callback-и.
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
         * Перевіряє callback-и, щоб подання ніколи не мало неповного набору дій.
         *
         * @param newProject дія створення проєкту.
         * @param load дія відкриття списку проєктів.
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

    /** Позначає сторінку, яку наразі показує контейнер меню. */
    private enum Page {
        MAIN,
        LOAD,
        SETTINGS
    }

    private final LocalizationService localization;
    private final MainMenuHomeView homeView;
    private final ProjectListView projectListView;
    private final LanguageSettingsView languageSettingsView;
    private final VBox panel = new VBox(14.0);
    private final VBox content = new VBox(10.0);
    private final HBox footer = new HBox(10.0);
    private final Region footerSpacer = new Region();
    private final Label titleLabel = new Label();
    private final Button backButton = secondaryButton();
    private final Button saveSettingsButton = menuButton();
    private final Rectangle backdrop = new Rectangle();
    private final UnsavedChangesView unsavedChangesView;
    private final Consumer<java.util.Locale> localizationListener = ignored -> refreshTexts();

    private Actions actions = new Actions(
            () -> { }, () -> { }, () -> { }, () -> { }, () -> { },
            ignored -> { }, (ignored, ignored2) -> null, ignored -> { }, () -> { }
    );
    private Page page = Page.MAIN;
    private boolean savingPendingChanges;
    private Consumer<Boolean> visibilityChanged = ignored -> { };
    private Consumer<SupportedLanguage> previewLanguageAction = ignored -> { };
    private Runnable commitSettingsAction = () -> { };
    private Runnable rollbackSettingsAction = () -> { };
    private BooleanSupplier hasUnsavedSettings;

    /**
     * Створює контейнер меню, його сторінки та спільний діалог незбережених змін.
     *
     * @param localization служба локалізації текстів і доступних мов.
     * @param visibilityChanged callback зміни видимості меню; {@code null} дозволено.
     */
    public MainMenuView(LocalizationService localization, Consumer<Boolean> visibilityChanged) {
        this.localization = Objects.requireNonNull(localization, "localization");
        this.hasUnsavedSettings = this.localization::hasUnconfirmedLanguagePreview;
        this.homeView = new MainMenuHomeView(localization);
        this.projectListView = new ProjectListView(localization);
        this.languageSettingsView = new LanguageSettingsView(localization);
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

        configureBackdrop();
        configurePanel();
        configureFooter();
        getChildren().addAll(backdrop, panel, unsavedChangesView);
        localization.addListener(localizationListener);
        refreshTexts();
        showMainPage();
    }

    /** Встановлює фонову накладку, яка закриває меню за callback-ом контролера. */
    private void configureBackdrop() {
        backdrop.widthProperty().bind(widthProperty());
        backdrop.heightProperty().bind(heightProperty());
        backdrop.setFill(Color.rgb(0, 0, 0, 0.5));
        backdrop.setOnMouseClicked(event -> {
            actions.close().run();
            event.consume();
        });
    }

    /** Налаштовує стиль і розміщення спільної панелі сторінок. */
    private void configurePanel() {
        panel.getStyleClass().add("main-menu-panel");
        panel.setMinSize(0.0, 0.0);
        panel.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        panel.setMaxSize(MAX_PANEL_WIDTH, MAX_PANEL_HEIGHT);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setFocusTraversable(true);
        StackPane.setAlignment(panel, Pos.CENTER);
        titleLabel.getStyleClass().add("main-menu-title");
        content.setAlignment(Pos.TOP_CENTER);
        content.setMinHeight(0.0);
        VBox.setVgrow(content, Priority.ALWAYS);
        footer.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(footer, Priority.NEVER);
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
    }

    /** Підключає спільні кнопки нижньої панелі сторінок. */
    private void configureFooter() {
        backButton.setOnAction(event -> {
            actions.back().run();
            event.consume();
        });
        saveSettingsButton.setOnAction(event -> {
            saveSettings();
            event.consume();
        });
    }

    /** Розміщує накладку меню, підлаштовуючи висоту сторінок до їхнього вмісту. */
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

    /** Замінює callback-и дій, які використовують сторінки меню. */
    public void setActions(Actions actions) {
        this.actions = Objects.requireNonNull(actions, "actions");
        homeView.setActions(
                actions.close(), actions.newProject(), actions.load(), actions.settings(), actions.exit());
    }

    /**
     * Передає обробку мови й перевірку незбереженого стану спільній сесії редагування налаштувань.
     * Подання лише передає події через callback-и, а джерелом істини для стану змін є контролер.
     *
     * @param previewLanguageAction дія попереднього перегляду вибраної мови.
     * @param confirmLanguageAction дія підтвердження й збереження всіх змінених налаштувань.
     * @param discardLanguageAction дія відновлення початкових значень усіх налаштувань.
     * @param hasUnsavedLanguageChanges перевірка наявності змін у спільній сесії налаштувань.
     */
    public void setLanguageActions(
            Consumer<SupportedLanguage> previewLanguageAction,
            Runnable confirmLanguageAction,
            Runnable discardLanguageAction,
            BooleanSupplier hasUnsavedLanguageChanges
    ) {
        this.previewLanguageAction = Objects.requireNonNull(previewLanguageAction, "previewLanguageAction");
        this.commitSettingsAction = Objects.requireNonNull(confirmLanguageAction, "confirmLanguageAction");
        this.rollbackSettingsAction = Objects.requireNonNull(discardLanguageAction, "discardLanguageAction");
        this.hasUnsavedSettings = Objects.requireNonNull(
                hasUnsavedLanguageChanges, "hasUnsavedLanguageChanges");
        languageSettingsView.setLanguageSelectedAction(this::handleLanguageSelected);
    }

    /** Перевіряє, чи відображається накладка меню. */
    public boolean isMenuVisible() {
        return isVisible();
    }

    /** Показує меню та сповіщає власника про відкриття. */
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

    /** Перевіряє наявність незбережених змін налаштувань або назви проєкту. */
    public boolean hasUnsavedChanges() {
        return settingsHaveUnsavedChanges()
                || projectListView.rows().stream().anyMatch(SavedProjectRowView::hasUnsavedChanges);
    }

    /** Перевіряє, чи відображається діалог незбережених змін. */
    public boolean isConfirmingUnsavedChanges() {
        return unsavedChangesView.isShowing();
    }

    /** Показує підтвердження незбережених змін перед дією, що може їх втратити. */
    public void confirmUnsavedChanges(Runnable saveAction, Runnable discardAction) {
        unsavedChangesView.show(saveAction, discardAction);
    }

    /** Вибирає в активному діалозі відхилення змін і продовження дії. */
    public void discardAndContinueUnsavedChanges() {
        unsavedChangesView.discardAndContinue();
    }

    /** Зберігає незавершене перейменування проєктів і підтверджує зміну налаштувань через контролер. */
    public void saveUnsavedChanges() {
        savingPendingChanges = true;
        try {
            for (SavedProjectRowView row : projectListView.rows()) {
                row.savePendingChange();
            }
            if (settingsHaveUnsavedChanges()) {
                saveSettings();
            }
        } finally {
            savingPendingChanges = false;
        }
    }

    /** Відхиляє незавершені зміни та передає скасування налаштувань контролеру. */
    public void discardUnsavedChanges() {
        for (SavedProjectRowView row : projectListView.rows()) {
            row.discardPendingChange();
        }
        if (settingsHaveUnsavedChanges()) {
            rollbackSettingsAction.run();
        }
        unsavedChangesView.hide();
        updateSaveButtonVisibility();
    }

    /** Показує головну сторінку без кнопки повернення. */
    public void showMainPage() {
        page = Page.MAIN;
        clearProjectRows();
        updatePageTitle();
        content.getChildren().setAll(homeView);
        installPanel();
    }

    /**
     * Показує сторінку збережених проєктів; каталог і бізнес-операції залишаються поза поданням.
     *
     * @param projects описи доступних збережених проєктів; {@code null} трактується як порожній список.
     */
    public void showLoadPage(List<ProjectDescriptor> projects) {
        page = Page.LOAD;
        projectListView.showProjects(
                projects,
                actions.openProject(),
                actions.renameProject(),
                actions.deleteProject(),
                this::handleRenameCommitted);
        VBox.setVgrow(projectListView, Priority.ALWAYS);
        updatePageTitle();
        content.getChildren().setAll(projectListView);
        installPanel();
    }

    /** Показує сторінку налаштувань, передаючи вибір мови контролеру. */
    public void showSettingsPage() {
        page = Page.SETTINGS;
        clearProjectRows();
        languageSettingsView.refreshTexts();
        updatePageTitle();
        content.getChildren().setAll(languageSettingsView);
        installPanel();
    }

    /** Перевіряє, чи відкрита сторінка збережених проєктів. */
    public boolean isLoadPage() {
        return page == Page.LOAD;
    }

    /** Перевіряє, чи відкрита сторінка налаштувань. */
    public boolean isSettingsPage() {
        return page == Page.SETTINGS;
    }

    /** Повертає підписи кнопок головної сторінки для регресійних тестів. */
    public List<String> mainButtonTextsForTest() {
        return homeView.buttonTextsForTest();
    }

    /** Надає вибір мови для тестів інтерфейсу. */
    public javafx.scene.control.ComboBox<SupportedLanguage> languageSelectorForTest() {
        return languageSettingsView.languageSelectorForTest();
    }

    /**
     * Повертає головну сторінку для перевірок композиції окремих подань.
     *
     * @return компонент головної сторінки, який використовує контейнер меню.
     */
    MainMenuHomeView homePageForTest() {
        return homeView;
    }

    /**
     * Повертає сторінку списку проєктів для перевірок композиції окремих подань.
     *
     * @return компонент списку збережених проєктів.
     */
    ProjectListView projectListPageForTest() {
        return projectListView;
    }

    /**
     * Повертає сторінку налаштувань для перевірок композиції окремих подань.
     *
     * @return компонент налаштувань мови.
     */
    LanguageSettingsView languageSettingsPageForTest() {
        return languageSettingsView;
    }

    /**
     * Повертає кнопку відхилення змін у діалозі, щоб перевірити сценарій скасування.
     *
     * @return кнопка «Ні» в діалозі незбережених змін.
     */
    Button discardChangesButtonForTest() {
        return unsavedChangesView.discardButtonForTest();
    }

    /**
     * Перевіряє, чи має поточна сторінка налаштувань непідтверджені зміни.
     *
     * @return {@code true}, якщо активна сесія містить непідтверджені зміни.
     */
    public boolean isSettingsDirty() {
        return settingsHaveUnsavedChanges();
    }

    /**
     * Оновлює стан елементів керування налаштуваннями після зміни будь-якого зареєстрованого параметра.
     *
     * <p>Контролер викликає цей метод після попереднього застосування значення, щоб кнопка збереження
     * відображала загальний стан сесії, а не лише зміни мови.</p>
     */
    public void refreshSettingsState() {
        updateSaveButtonVisibility();
    }

    /** Надає кнопку повернення для тестів інтерфейсу. */
    public Button backButtonForTest() {
        return backButton;
    }

    /** Надає кнопку збереження налаштувань для тестів інтерфейсу. */
    public Button saveSettingsButtonForTest() {
        return saveSettingsButton;
    }

    /** Повертає заголовок поточної сторінки для регресійних тестів. */
    public String pageTitleForTest() {
        return titleLabel.getText();
    }

    /** Надає прокручуваний список проєктів для перевірок компонування. */
    ScrollPane projectScrollPaneForTest() {
        return projectListView.scrollPaneForTest();
    }

    /** Надає панель меню для тестів відновлення фокусу. */
    Region menuPanelForTest() {
        return panel;
    }

    /** Повертає максимальну ширину панелі для тестів адаптивного розташування. */
    double panelMaxWidthForTest() {
        return panel.getMaxWidth();
    }

    /** Повертає фактичну ширину панелі після компонування. */
    double panelWidthForTest() {
        return panel.getWidth();
    }

    /** Повертає фактичну висоту панелі після компонування. */
    double panelHeightForTest() {
        return panel.getHeight();
    }

    /** Повертає максимальну висоту панелі для адаптивних тестів. */
    double panelMaxHeightForTest() {
        return panel.getMaxHeight();
    }

    /** Повертає поточні рядки збережених проєктів для тестів інтерфейсу. */
    List<SavedProjectRowView> projectRowsForTest() {
        return projectListView.rowsForTest();
    }

    /** Звільняє ресурси рядків і від'єднує слухача локалізації. */
    public void dispose() {
        homeView.dispose();
        projectListView.dispose();
        languageSettingsView.dispose();
        unsavedChangesView.hide();
        localization.removeListener(localizationListener);
    }

    /** Підтверджує зміни налаштувань через контролер та оновлює індикатор незбережених змін. */
    public void saveSettings() {
        if (!isSettingsPage()) {
            return;
        }
        commitSettingsAction.run();
        updateSaveButtonVisibility();
    }

    /** Передає вибрану мову контролеру та оновлює стан кнопки збереження. */
    private void handleLanguageSelected(SupportedLanguage language) {
        previewLanguageAction.accept(language);
        updateSaveButtonVisibility();
    }

    /** Перевіряє наявність незбережених налаштувань в активній сесії на сторінці налаштувань. */
    private boolean settingsHaveUnsavedChanges() {
        return page == Page.SETTINGS && hasUnsavedSettings.getAsBoolean();
    }

    /** Оновлює список після перейменування поза пакетним збереженням змін. */
    private void handleRenameCommitted() {
        if (!savingPendingChanges && isLoadPage()) {
            actions.load().run();
        }
    }

    /** Перевстановлює вміст панелі відповідно до поточної сторінки. */
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

    /** Відновлює клавіатурний фокус панелі після переходу між сторінками. */
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

    /** Звільняє рядки проєктів та очищує вміст попередньої сторінки. */
    private void clearProjectRows() {
        projectListView.clear();
        content.getChildren().clear();
    }

    /** Оновлює переклади контейнера та передає оновлення спеціалізованим сторінкам. */
    private void refreshTexts() {
        updatePageTitle();
        backButton.setText(localization.text("menu.back"));
        saveSettingsButton.setText(localization.text("common.save"));
        unsavedChangesView.refreshTexts();
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

    /** Показує або приховує кнопку збереження залежно від загального стану сесії налаштувань. */
    private void updateSaveButtonVisibility() {
        boolean showSave = settingsHaveUnsavedChanges();
        saveSettingsButton.setVisible(showSave);
        saveSettingsButton.setManaged(showSave);
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
