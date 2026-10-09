package com.example.neuronmap.view;

import com.example.neuronmap.application.project.ProjectDescriptor;
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
 * Builds the main menu, saved-project page, settings page, and unsaved-changes confirmation.
 * Main and settings pages adapt to their content; the saved-project page uses the maximum panel height
 * whenever the available scene space permits it.
 */
public final class MainMenuView extends StackPane {

    private static final double MAX_PANEL_WIDTH = 460.0;
    private static final double MAX_PANEL_HEIGHT = 430.0;

    /**
     * Holds callbacks for menu navigation and saved-project operations.
     *
     * @param newProject action that creates a project.
     * @param load action that opens the saved-project page.
     * @param settings action that opens the settings page.
     * @param exit action that exits the application.
     * @param close action that closes the menu.
     * @param openProject action that opens a selected project.
     * @param renameProject action that renames a project and returns its updated descriptor.
     * @param deleteProject action that deletes a project.
     * @param back action that navigates to the previous menu page.
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
         * Validates and stores all menu action callbacks.
         *
         * @param newProject action that creates a project.
         * @param load action that opens the saved-project page.
         * @param settings action that opens the settings page.
         * @param exit action that exits the application.
         * @param close action that closes the menu.
         * @param openProject action that opens a selected project.
         * @param renameProject action that renames a project.
         * @param deleteProject action that deletes a project.
         * @param back action that navigates to the previous menu page.
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

    /** Identifies the page currently shown in the menu. */
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

    /**
     * Creates the menu view and connects it to localization and visibility-state notifications.
     *
     * @param localization service that provides localized text and manages the active language.
     * @param visibilityChanged callback notified when the menu becomes visible or hidden; may be null.
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
     * Replaces the callbacks used by menu controls.
     *
     * @param actions callbacks for navigation, application exit, and project actions.
     */
    public void setActions(Actions actions) {
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    /**
     * Lays out the overlay, adapting the main/settings pages to their content while keeping the
     * saved-project page at the maximum panel height when the available scene size permits it.
     * The backdrop and confirmation layer still fill the whole scene.
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
     * Returns whether the menu overlay is currently visible.
     *
     * @return true when the menu is visible.
     */
    public boolean isMenuVisible() {
        return isVisible();
    }

    /** Shows the menu overlay and notifies the owner that it is active. */
    public void showMenu() {
        setVisible(true);
        setManaged(true);
        visibilityChanged.accept(true);
        restoreMenuPanelFocus();
    }

    /** Hides the menu and its confirmation dialog, then notifies the owner. */
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
     * Reports whether any visible menu page contains uncommitted changes.
     *
     * @return true if settings or a saved-project row has pending changes.
     */
    public boolean hasUnsavedChanges() {
        return settingsHaveUnsavedChanges()
                || projectRows.stream().anyMatch(SavedProjectRowView::hasUnsavedChanges);
    }

    /**
     * Reports whether the unsaved-changes confirmation is visible.
     *
     * @return true when the confirmation dialog is showing.
     */
    public boolean isConfirmingUnsavedChanges() {
        return unsavedChangesView.isShowing();
    }

    /**
     * Displays the unsaved-changes confirmation before a potentially destructive navigation action.
     *
     * @param saveAction callback run after the pending changes are saved.
     * @param discardAction callback run after the pending changes are discarded.
     */
    public void confirmUnsavedChanges(Runnable saveAction, Runnable discardAction) {
        unsavedChangesView.show(saveAction, discardAction);
    }

    /**
     * Chooses the discard-and-continue option of an active confirmation, such as when Escape is pressed.
     * Does nothing when no confirmation is visible.
     */
    public void discardAndContinueUnsavedChanges() {
        unsavedChangesView.discardAndContinue();
    }

    /** Saves all pending project edits and persists a previewed settings language. */
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

    /** Discards pending project edits and restores the settings language selected before editing. */
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

    /** Shows the main menu page, with Continue as its first action and no Back button. */
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
     * Shows the saved-project page and builds a scrollable list of the supplied projects.
     *
     * @param projects descriptors for available saved projects; null is treated as an empty list.
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

    /** Shows the settings page and records the language to which unsaved changes can be reverted. */
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
     * Reports whether the saved-project page is active.
     *
     * @return true when the menu displays saved projects.
     */
    public boolean isLoadPage() {
        return page == Page.LOAD;
    }

    /**
     * Reports whether the settings page is active.
     *
     * @return true when the menu displays settings.
     */
    public boolean isSettingsPage() {
        return page == Page.SETTINGS;
    }

    /**
     * Returns the localized labels of main-page actions for UI regression tests.
     *
     * @return button labels in displayed order.
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
     * Exposes the language selector for UI tests.
     *
     * @return the settings language selector.
     */
    public ComboBox<SupportedLanguage> languageSelectorForTest() {
        return languageSelector;
    }

    /**
     * Exposes the Back button for UI tests.
     *
     * @return the Back button, which is attached only to submenu footers.
     */
    public Button backButtonForTest() {
        return backButton;
    }

    /**
     * Exposes the settings Save button for UI tests.
     *
     * @return the settings Save button.
     */
    public Button saveSettingsButtonForTest() {
        return saveSettingsButton;
    }

    /**
     * Exposes the current page title for regression tests.
     *
     * @return the text displayed at the top of the active menu page.
     */
    public String pageTitleForTest() {
        return titleLabel.getText();
    }

    /**
     * Exposes the current saved-project scroll pane for UI tests.
     *
     * @return the current project list scroll pane, or null before the project page is created.
     */
    ScrollPane projectScrollPaneForTest() {
        return projectScrollPane;
    }

    /**
     * Exposes the menu panel for focus-restoration regression tests.
     *
     * @return the focus-traversable container that receives focus after menu-page transitions.
     */
    Region menuPanelForTest() {
        return panel;
    }

    /**
     * Exposes the menu panel's maximum width for responsive-layout regression tests.
     *
     * @return the configured maximum panel width in pixels.
     */
    double panelMaxWidthForTest() {
        return panel.getMaxWidth();
    }

    /**
     * Exposes the actual menu panel width for responsive-layout regression tests.
     *
     * @return the panel's current width in pixels.
     */
    double panelWidthForTest() {
        return panel.getWidth();
    }

    /**
     * Exposes the actual menu panel height for responsive-layout regression tests.
     *
     * @return the panel's current height in pixels.
     */
    double panelHeightForTest() {
        return panel.getHeight();
    }

    /**
     * Exposes the menu panel's maximum height for responsive-layout regression tests.
     *
     * @return the configured maximum panel height in pixels.
     */
    double panelMaxHeightForTest() {
        return panel.getMaxHeight();
    }

    /**
     * Exposes saved-project rows for UI tests.
     *
     * @return an immutable snapshot of current project rows.
     */
    List<SavedProjectRowView> projectRowsForTest() {
        return List.copyOf(projectRows);
    }

    /** Releases row listeners and detaches the localization listener. */
    public void dispose() {
        clearProjectRows();
        unsavedChangesView.hide();
        localization.removeListener(localizationListener);
    }

    /**
     * Persists the current previewed language and hides the Save button by resetting the baseline.
     */
    public void saveSettings() {
        if (!isSettingsPage()) {
            return;
        }
        localization.persistCurrentLanguage();
        settingsOriginalLanguage = localization.language();
        updateSaveButtonVisibility();
    }

    /**
     * Checks whether the selected settings language differs from the saved baseline.
     *
     * @return true when a new language has been previewed but not saved.
     */
    private boolean settingsHaveUnsavedChanges() {
        return page == Page.SETTINGS
                && settingsOriginalLanguage != null
                && settingsOriginalLanguage != localization.language();
    }

    /** Refreshes the project list after a rename is committed outside a bulk save operation. */
    private void handleRenameCommitted() {
        if (!savingPendingChanges && isLoadPage()) {
            actions.load().run();
        }
    }

    /**
     * Builds the page footer and lays out the panel without forcing it to its previous fixed size.
     */
    private void installPanel() {
        if (page == Page.MAIN) {
            // A detached footer still owns its child nodes; clear it so Back is truly absent on Main.
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
     * Moves keyboard focus away from the previously activated control after a menu-page transition.
     * A second request after the current layout pass prevents JavaFX from auto-focusing the first
     * button again when controls are detached and reattached to the panel.
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

    /** Releases row-specific listeners and clears the current project list. */
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

    /** Reloads supported languages and selects the active language. */
    private void reloadLanguageSelector() {
        SupportedLanguage current = localization.language();
        languageSelector.getItems().setAll(localization.supportedLanguagesInDisplayOrder());
        languageSelector.setValue(current);
    }

    /** Updates visible text and synchronizes the Save button with the current dirty state. */
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


    /** Updates the title to match the currently visible menu page. */
    private void updatePageTitle() {
        String title = switch (page) {
            case MAIN -> "NeuronMap";
            case LOAD -> localization.text("menu.saved_projects");
            case SETTINGS -> localization.text("menu.settings");
        };
        titleLabel.setText(title);
    }

    /** Shows or hides the Save button according to whether settings changes need to be persisted. */
    private void updateSaveButtonVisibility() {
        boolean showSave = settingsHaveUnsavedChanges();
        saveSettingsButton.setVisible(showSave);
        saveSettingsButton.setManaged(showSave);
    }

    /** Creates a language list cell that displays the localized name of each supported language. */
    private ListCell<SupportedLanguage> languageCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SupportedLanguage item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : localization.displayName(item));
            }
        };
    }

    /** Creates a full-width primary button used by the menu. */
    private static Button menuButton() {
        Button button = new Button();
        button.getStyleClass().add("main-menu-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(46.0);
        button.setPrefHeight(46.0);
        button.setMaxHeight(46.0);
        return button;
    }

    /** Creates the secondary Back button used on submenu pages. */
    private static Button secondaryButton() {
        Button button = new Button();
        button.getStyleClass().add("main-menu-back-button");
        button.setMinHeight(38.0);
        button.setPrefHeight(38.0);
        return button;
    }
}
