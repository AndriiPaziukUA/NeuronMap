package com.example.neuronmap.view.menu;

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
import javafx.scene.shape.Rectangle;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Компонує сторінки меню, відображає стан та передає події користувача через callback-и.
 * Послідовністю збереження й відхилення незбережених змін керує {@code MainMenuController}.
 */
public final class MainMenuView extends StackPane {

    private static final double MAX_PANEL_WIDTH = 920.0;
    private static final double MAX_PANEL_HEIGHT = 430.0;
    private static final String MENU_STYLESHEET_RESOURCE = "/menu-enhancements.css";
    private static final String SAVE_BUTTON_STYLESHEET_RESOURCE = "/main-menu-settings-save.css";

    /**
     * Зберігає повний набір обов'язкових зворотних викликів навігації меню та операцій над проєктами.
     * Кожну дію має надати контролер; порожні callback-и не є допустимою заміною інтеграції.
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
    private final FullScreenSettingsView fullScreenSettingsView;
    private final LanguageSettingsView languageSettingsView;
    private final VBox panel = new VBox(14.0);
    private final VBox content = new VBox(10.0);
    private final HBox footer = new HBox(10.0);
    private final Region footerSpacer = new Region();
    private final Label titleLabel = new Label();
    private final Button backButton = secondaryButton();
    private final Button saveSettingsButton = settingsSaveButton();
    private final Rectangle backdrop = new Rectangle();
    private final UnsavedChangesView unsavedChangesView;
    private final Consumer<java.util.Locale> localizationListener = ignored -> refreshTexts();

    private Actions actions;
    private Page page = Page.MAIN;
    private Consumer<Boolean> visibilityChanged = ignored -> { };
    private Consumer<SupportedLanguage> previewLanguageAction = ignored -> { };
    private Runnable commitSettingsAction = () -> { };
    private BooleanSupplier hasUnsavedSettings = () -> false;
    private Runnable projectRenameCommittedAction = () -> { };

    /**
     * Створює контейнер меню, його сторінки та спільний діалог незбережених змін.
     *
     * @param localization служба локалізації текстів і доступних мов.
     * @param visibilityChanged callback зміни видимості меню; {@code null} дозволено.
     * @throws IllegalStateException якщо обов'язкова таблиця стилів меню відсутня в classpath.
     */
    public MainMenuView(LocalizationService localization, Consumer<Boolean> visibilityChanged) {
        this.localization = Objects.requireNonNull(localization, "localization");
        this.hasUnsavedSettings = this.localization::hasUnconfirmedLanguagePreview;
        this.homeView = new MainMenuHomeView(localization);
        configureHomePageActions();
        this.projectListView = new ProjectListView(localization);
        this.fullScreenSettingsView = new FullScreenSettingsView(localization);
        this.languageSettingsView = new LanguageSettingsView(localization);
        this.unsavedChangesView = new UnsavedChangesView(localization);
        if (visibilityChanged != null) {
            this.visibilityChanged = visibilityChanged;
        }

        setVisible(false);
        setManaged(false);
        setPickOnBounds(true);
        getStyleClass().add("main-menu-overlay");
        getStylesheets().add(requireMenuStylesheet().toExternalForm());
        getStylesheets().add(requireSaveButtonStylesheet().toExternalForm());

        configureBackdrop();
        configurePanel();
        configureFooter();
        getChildren().addAll(backdrop, panel, unsavedChangesView);
        localization.addListener(localizationListener);
        refreshTexts();
        showMainPage();
    }

    /**
     * Перевіряє наявність обов'язкової таблиці стилів меню, щоб помилка пакування не маскувалася.
     *
     * @return URL CSS-ресурсу меню.
     * @throws IllegalStateException якщо ресурс не включено до classpath застосунку.
     */
    private URL requireMenuStylesheet() {
        URL stylesheet = getClass().getResource(MENU_STYLESHEET_RESOURCE);
        if (stylesheet == null) {
            throw new IllegalStateException(MENU_STYLESHEET_RESOURCE + " is missing");
        }
        return stylesheet;
    }

    /**
     * Перевіряє окрему таблицю стилів кнопки збереження, не використовуючи стиль решти меню.
     *
     * @return URL ізольованого CSS-ресурсу кнопки.
     * @throws IllegalStateException якщо CSS-ресурс не включено до classpath застосунку.
     */
    private URL requireSaveButtonStylesheet() {
        URL stylesheet = getClass().getResource(SAVE_BUTTON_STYLESHEET_RESOURCE);
        if (stylesheet == null) {
            throw new IllegalStateException(SAVE_BUTTON_STYLESHEET_RESOURCE + " is missing");
        }
        return stylesheet;
    }

    /**
     * Налаштовує фон меню; колір і прозорість визначаються лише таблицею стилів головного меню.
     * Геометрію фону визначає розмір самої накладки, а не її дочірні вузли.
     */
    private void configureBackdrop() {
        backdrop.getStyleClass().add("main-menu-backdrop");
        backdrop.setManaged(false);
        backdrop.widthProperty().bind(widthProperty());
        backdrop.heightProperty().bind(heightProperty());
        backdrop.setOnMouseClicked(event -> {
            requireActions().close().run();
            event.consume();
        });
    }

    /** Налаштовує розміщення спільної панелі сторінок. */
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
            requireActions().back().run();
            event.consume();
        });
        saveSettingsButton.setOnAction(event -> {
            handleSaveSettingsRequested();
            event.consume();
        });
    }

    /** Підключає кнопки головної сторінки до перевіреного набору дій контролера. */
    private void configureHomePageActions() {
        homeView.setActions(
                () -> requireActions().close().run(),
                () -> requireActions().newProject().run(),
                () -> requireActions().load().run(),
                () -> requireActions().settings().run(),
                () -> requireActions().exit().run()
        );
    }

    /**
     * Повертає налаштовані дії або явно сигналізує про порушення порядку ініціалізації.
     *
     * @return повний набір дій меню, переданий контролером.
     * @throws IllegalStateException якщо контролер ще не підключив обов'язкові дії.
     */
    private Actions requireActions() {
        if (actions == null) {
            throw new IllegalStateException(
                    "Дії головного меню не налаштовано. Викличте setActions(...) до показу меню або виконання дій.");
        }
        return actions;
    }

    /** Розміщує накладку меню, підлаштовуючи розмір панелі до вмісту поточної сторінки. */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (getWidth() <= 0.0 || getHeight() <= 0.0) {
            return;
        }

        double availableWidth = Math.max(0.0, getWidth() - 32.0);
        double availableHeight = Math.max(0.0, getHeight() - 32.0);
        double preferredWidth = Math.max(panel.minWidth(-1.0), panel.prefWidth(-1.0));
        if (page == Page.LOAD) {
            double panelHorizontalInsets = panel.getInsets().getLeft() + panel.getInsets().getRight();
            preferredWidth = Math.max(
                    preferredWidth,
                    projectListView.preferredWidthForLayout() + panelHorizontalInsets);
        }
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
     * Підключає повний набір обов'язкових дій після створення контролера меню.
     * Цей другий крок потрібен через циклічну композицію контролера та його подання.
     *
     * @param actions повний набір callback-ів меню.
     * @throws NullPointerException якщо набір дій не задано.
     */
    public void setActions(Actions actions) {
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    /**
     * Передає вибір мови, підтвердження налаштувань і перевірку їхнього стану контролеру.
     *
     * @param previewLanguageAction дія попереднього перегляду вибраної мови.
     * @param confirmLanguageAction дія підтвердження всіх змінених налаштувань.
     * @param hasUnsavedLanguageChanges перевірка наявності змін у спільній сесії налаштувань.
     */
    public void setLanguageActions(
            Consumer<SupportedLanguage> previewLanguageAction,
            Runnable confirmLanguageAction,
            BooleanSupplier hasUnsavedLanguageChanges
    ) {
        this.previewLanguageAction = Objects.requireNonNull(previewLanguageAction, "previewLanguageAction");
        this.commitSettingsAction = Objects.requireNonNull(confirmLanguageAction, "confirmLanguageAction");
        this.hasUnsavedSettings = Objects.requireNonNull(
                hasUnsavedLanguageChanges, "hasUnsavedLanguageChanges");
        languageSettingsView.setLanguageSelectedAction(this::handleLanguageSelected);
    }

    /**
     * Підключає джерело поточного стану та callback попереднього перегляду повноекранного режиму.
     *
     * @param currentValue постачальник фактично активного стану режиму.
     * @param previewAction дія тимчасового застосування вибраного стану.
     */
    public void setFullScreenActions(Supplier<Boolean> currentValue, Consumer<Boolean> previewAction) {
        fullScreenSettingsView.setActions(currentValue, previewAction);
    }

    /**
     * Передає контролеру повідомлення про завершене перейменування рядка.
     *
     * @param action сповіщення про підтвердження назви проєкту.
     * @throws NullPointerException якщо callback не задано.
     */
    public void setProjectRenameCommittedAction(Runnable action) {
        projectRenameCommittedAction = Objects.requireNonNull(action, "action");
    }

    /** Перевіряє, чи відображається накладка меню. */
    public boolean isMenuVisible() {
        return isVisible();
    }

    /** Показує меню та сповіщає власника про відкриття. */
    public void showMenu() {
        requireActions();
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

    /** Повертає наявність незбережених змін налаштувань або введеної назви проєкту. */
    public boolean hasUnsavedChanges() {
        return settingsHaveUnsavedChanges() || projectListView.hasUnsavedChanges();
    }

    /** Перевіряє наявність незбережених змін назви у поточному списку проєктів. */
    public boolean hasUnsavedProjectChanges() {
        return projectListView.hasUnsavedChanges();
    }

    /** Передає списку проєктів запит зберегти введені назви. */
    public void savePendingProjectChanges() {
        projectListView.savePendingChanges();
    }

    /** Передає списку проєктів запит відхилити введені назви. */
    public void discardPendingProjectChanges() {
        projectListView.discardPendingChanges();
    }

    /** Перевіряє, чи відображається діалог підтвердження. */
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

    /** Показує головну сторінку меню. */
    public void showMainPage() {
        page = Page.MAIN;
        clearProjectRows();
        updatePageTitle();
        content.getChildren().setAll(homeView);
        installPanel();
    }

    /** Показує сторінку збережених проєктів; каталог і бізнес-операції залишаються поза поданням. */
    public void showLoadPage(List<ProjectDescriptor> projects) {
        page = Page.LOAD;
        projectListView.showProjects(
                projects,
                project -> requireActions().openProject().accept(project),
                (project, name) -> requireActions().renameProject().apply(project, name),
                project -> requireActions().deleteProject().accept(project),
                projectRenameCommittedAction);
        VBox.setVgrow(projectListView, Priority.ALWAYS);
        updatePageTitle();
        content.getChildren().setAll(projectListView);
        installPanel();
    }

    /** Показує налаштування, розміщуючи повноекранний режим безпосередньо над вибором мови. */
    public void showSettingsPage() {
        page = Page.SETTINGS;
        clearProjectRows();
        fullScreenSettingsView.refreshValue();
        languageSettingsView.refreshTexts();
        updatePageTitle();
        content.getChildren().setAll(fullScreenSettingsView, languageSettingsView);
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

    /** Повертає підписи кнопок головної сторінки для регресійних тестів у цьому пакеті. */
    List<String> mainButtonTextsForTest() {
        return homeView.buttonTextsForTest();
    }

    /** Надає компонент вибору мови для UI-тестів. */
    javafx.scene.control.ComboBox<SupportedLanguage> languageSelectorForTest() {
        return languageSettingsView.languageSelectorForTest();
    }

    /** Повертає головну сторінку для перевірок композиції. */
    MainMenuHomeView homePageForTest() {
        return homeView;
    }

    /** Повертає список проєктів для перевірок композиції. */
    ProjectListView projectListPageForTest() {
        return projectListView;
    }

    /** Повертає налаштування мови для перевірок композиції. */
    LanguageSettingsView languageSettingsPageForTest() {
        return languageSettingsView;
    }

    /** Повертає налаштування повноекранного режиму для перевірок композиції. */
    FullScreenSettingsView fullScreenSettingsPageForTest() {
        return fullScreenSettingsView;
    }

    /** Повертає кнопку відхилення змін для тестування скасування. */
    Button discardChangesButtonForTest() {
        return unsavedChangesView.discardButtonForTest();
    }

    /** Повертає кнопку підтвердження змін для тестування збереження. */
    Button saveChangesButtonForTest() {
        return unsavedChangesView.saveButtonForTest();
    }

    /** Перевіряє наявність непідтверджених змін налаштувань. */
    public boolean isSettingsDirty() {
        return settingsHaveUnsavedChanges();
    }

    /** Оновлює відображення кнопки збереження після зміни будь-якого параметра налаштувань. */
    public void refreshSettingsState() {
        updateSaveButtonVisibility();
    }

    /** Синхронізує положення перемикача з фактичним станом після підтвердження або відкату налаштувань. */
    public void refreshFullScreenValue() {
        fullScreenSettingsView.refreshValue();
    }

    /** Надає кнопку повернення для UI-тестів. */
    Button backButtonForTest() {
        return backButton;
    }

    /** Надає кнопку підтвердження налаштувань для UI-тестів. */
    Button saveSettingsButtonForTest() {
        return saveSettingsButton;
    }

    /** Повертає заголовок поточної сторінки. */
    String pageTitleForTest() {
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

    /** Повертає максимальну ширину панелі для адаптивних тестів. */
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

    /** Повертає рядки збережених проєктів для тестів інтерфейсу. */
    List<SavedProjectRowView> projectRowsForTest() {
        return projectListView.rowsForTest();
    }

    /** Звільняє дочірні подання та від'єднує слухача локалізації. */
    public void dispose() {
        homeView.dispose();
        projectListView.dispose();
        fullScreenSettingsView.dispose();
        languageSettingsView.dispose();
        unsavedChangesView.hide();
        localization.removeListener(localizationListener);
    }

    /** Передає запит збереження налаштувань контролеру та оновлює відображений стан. */
    private void handleSaveSettingsRequested() {
        if (isSettingsPage()) {
            commitSettingsAction.run();
            updateSaveButtonVisibility();
        }
    }

    /** Передає вибрану мову контролеру та оновлює стан кнопки збереження. */
    private void handleLanguageSelected(SupportedLanguage language) {
        previewLanguageAction.accept(language);
        updateSaveButtonVisibility();
    }

    /** Перевіряє, чи має поточна сторінка непідтверджені налаштування. */
    private boolean settingsHaveUnsavedChanges() {
        return page == Page.SETTINGS && hasUnsavedSettings.getAsBoolean();
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

    /** Відновлює клавіатурний фокус панелі після переходу між сторінками меню. */
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

    /** Показує або приховує кнопку збереження залежно від спільного стану сесії налаштувань. */
    private void updateSaveButtonVisibility() {
        boolean showSave = settingsHaveUnsavedChanges();
        saveSettingsButton.setVisible(showSave);
        saveSettingsButton.setManaged(showSave);
    }

    /** Створює кнопку збереження з природними розмірами та власним стилем. */
    private static Button settingsSaveButton() {
        Button button = new Button();
        button.getStyleClass().add("main-menu-settings-save-button");
        button.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        return button;
    }

    /** Створює допоміжну кнопку повернення з розмірами, що визначаються таблицею стилів. */
    private static Button secondaryButton() {
        Button button = new Button();
        button.getStyleClass().add("main-menu-back-button");
        button.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        return button;
    }
}
