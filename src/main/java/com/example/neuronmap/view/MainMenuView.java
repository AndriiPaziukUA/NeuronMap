package com.example.neuronmap.view;

import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** In-window application menu overlay. No separate native window is created. */
public final class MainMenuView extends StackPane {

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

    private enum Page {
        MAIN,
        LOAD,
        SETTINGS
    }

    private final LocalizationService localization;
    private final VBox panel = new VBox(14.0);
    private final VBox content = new VBox(10.0);
    private final VBox projectList = new VBox(6.0);
    private final Label titleLabel = new Label();
    private final Label hintLabel = new Label();
    private final Label languageLabel = new Label();
    private final Label emptyProjectsLabel = new Label();
    private final Button newProjectButton = menuButton();
    private final Button loadButton = menuButton();
    private final Button settingsButton = menuButton();
    private final Button exitButton = menuButton();
    private final Button backButton = secondaryButton();
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
    private SupportedLanguage settingsOriginalLanguage;
    private boolean savingPendingChanges;
    private Consumer<Boolean> visibilityChanged = ignored -> { };

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

        backdrop.widthProperty().bind(widthProperty());
        backdrop.heightProperty().bind(heightProperty());
        backdrop.setFill(Color.rgb(0, 0, 0, 0.5));
        backdrop.setOnMouseClicked(event -> {
            actions.close().run();
            event.consume();
        });

        panel.getStyleClass().add("main-menu-panel");
        panel.setMinSize(420.0, 380.0);
        panel.setPrefSize(460.0, 430.0);
        panel.setMaxSize(620.0, 560.0);
        panel.setAlignment(Pos.TOP_CENTER);
        StackPane.setAlignment(panel, Pos.CENTER);

        titleLabel.getStyleClass().add("main-menu-title");
        hintLabel.getStyleClass().add("main-menu-hint");
        emptyProjectsLabel.getStyleClass().add("main-menu-hint");
        languageLabel.getStyleClass().add("main-menu-label");

        content.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(content, Priority.ALWAYS);

        backButton.setOnAction(event -> {
            actions.back().run();
            event.consume();
        });

        languageSelector.setMaxWidth(Double.MAX_VALUE);
        languageSelector.setPrefWidth(250.0);
        languageSelector.setCellFactory(list -> languageCell());
        languageSelector.setButtonCell(languageCell());
        languageSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(SupportedLanguage object) {
                return object == null ? "" : localization.displayName(object);
            }

            @Override
            public SupportedLanguage fromString(String string) {
                return null;
            }
        });
        languageSelector.setOnAction(event -> {
            SupportedLanguage selected = languageSelector.getValue();
            if (selected != null) {
                languageSelector.hide();
                localization.previewLanguage(selected);
            }
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

    public void setActions(Actions actions) {
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    public boolean isMenuVisible() {
        return isVisible();
    }

    public void showMenu() {
        setVisible(true);
        setManaged(true);
        visibilityChanged.accept(true);
        requestFocus();
    }

    public void hide() {
        if (!isVisible()) {
            return;
        }
        unsavedChangesView.hide();
        setVisible(false);
        setManaged(false);
        visibilityChanged.accept(false);
    }

    public boolean hasUnsavedChanges() {
        return settingsHaveUnsavedChanges()
                || projectRows.stream().anyMatch(SavedProjectRowView::hasUnsavedChanges);
    }

    public boolean isConfirmingUnsavedChanges() {
        return unsavedChangesView.isShowing();
    }

    public void confirmUnsavedChanges(
            Runnable saveAction,
            Runnable discardAction
    ) {
        unsavedChangesView.show(saveAction, discardAction);
    }

    public void saveUnsavedChanges() {
        savingPendingChanges = true;
        try {
            for (SavedProjectRowView row : List.copyOf(projectRows)) {
                row.savePendingChange();
            }
            if (settingsHaveUnsavedChanges()) {
                localization.persistCurrentLanguage();
                settingsOriginalLanguage = localization.language();
            }
        } finally {
            savingPendingChanges = false;
        }
    }

    public void discardUnsavedChanges() {
        for (SavedProjectRowView row : projectRows) {
            row.discardPendingChange();
        }
        if (settingsHaveUnsavedChanges()
                && settingsOriginalLanguage != null) {
            localization.previewLanguage(settingsOriginalLanguage);
        }
        settingsOriginalLanguage = localization.language();
        unsavedChangesView.hide();
    }

    public void showMainPage() {
        page = Page.MAIN;
        clearProjectRows();
        content.getChildren().setAll(
                newProjectButton,
                loadButton,
                settingsButton,
                exitButton
        );
        installPanel();
    }

    public void showLoadPage(List<ProjectDescriptor> projects) {
        page = Page.LOAD;
        settingsOriginalLanguage = null;
        clearProjectRows();

        ScrollPane scrollPane = new ScrollPane(projectList);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("main-menu-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

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

        content.getChildren().setAll(
                hintLabel,
                scrollPane
        );
        installPanel();
    }

    public void showSettingsPage() {
        page = Page.SETTINGS;
        clearProjectRows();
        settingsOriginalLanguage = localization.language();
        reloadLanguageSelector();

        HBox languageRow = new HBox(12.0, languageLabel, languageSelector);
        languageRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(languageSelector, Priority.ALWAYS);

        content.getChildren().setAll(languageRow);
        installPanel();
    }

    public boolean isLoadPage() {
        return page == Page.LOAD;
    }

    public boolean isSettingsPage() {
        return page == Page.SETTINGS;
    }

    public List<String> mainButtonTextsForTest() {
        return List.of(
                newProjectButton.getText(),
                loadButton.getText(),
                settingsButton.getText(),
                exitButton.getText()
        );
    }

    public ComboBox<SupportedLanguage> languageSelectorForTest() {
        return languageSelector;
    }

    public Button backButtonForTest() {
        return backButton;
    }

    List<SavedProjectRowView> projectRowsForTest() {
        return List.copyOf(projectRows);
    }

    public void dispose() {
        clearProjectRows();
        unsavedChangesView.hide();
        localization.removeListener(localizationListener);
    }

    private boolean settingsHaveUnsavedChanges() {
        return page == Page.SETTINGS
                && settingsOriginalLanguage != null
                && settingsOriginalLanguage != localization.language();
    }

    private void handleRenameCommitted() {
        if (!savingPendingChanges && isLoadPage()) {
            actions.load().run();
        }
    }

    private void installPanel() {
        HBox footer = new HBox(backButton);
        footer.setAlignment(Pos.BOTTOM_LEFT);
        VBox.setVgrow(footer, Priority.NEVER);

        panel.getChildren().setAll(
                titleLabel,
                new Separator(),
                content,
                footer
        );
    }

    private void clearProjectRows() {
        for (SavedProjectRowView row : projectRows) {
            row.dispose();
        }
        projectRows = List.of();
        projectList.getChildren().clear();
    }

    private void reloadLanguageSelector() {
        SupportedLanguage current = localization.language();
        languageSelector.getItems().setAll(
                localization.supportedLanguagesInDisplayOrder()
        );
        languageSelector.setValue(current);
    }

    private void refreshTexts() {
        titleLabel.setText(
                page == Page.MAIN
                        ? "NeuronMap"
                        : page == Page.LOAD
                        ? localization.text("menu.saved_projects")
                        : localization.text("menu.settings")
        );
        newProjectButton.setText(localization.text("menu.new_project"));
        loadButton.setText(localization.text("menu.load"));
        settingsButton.setText(localization.text("menu.settings"));
        exitButton.setText(localization.text("menu.exit"));
        backButton.setText(localization.text("menu.back"));
        hintLabel.setText(localization.text("menu.open_hint"));
        emptyProjectsLabel.setText(localization.text("menu.no_saved_projects"));
        languageLabel.setText(localization.text("menu.language"));
        unsavedChangesView.refreshTexts();
        reloadLanguageSelector();
    }

    private ListCell<SupportedLanguage> languageCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SupportedLanguage item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null
                        ? null
                        : localization.displayName(item));
            }
        };
    }

    private static Button menuButton() {
        Button button = new Button();
        button.getStyleClass().add("main-menu-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(46.0);
        button.setPrefHeight(46.0);
        button.setMaxHeight(46.0);
        return button;
    }

    private static Button secondaryButton() {
        Button button = new Button();
        button.getStyleClass().add("main-menu-back-button");
        button.setMinHeight(38.0);
        button.setPrefHeight(38.0);
        return button;
    }
}
