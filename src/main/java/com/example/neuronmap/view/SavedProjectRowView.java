package com.example.neuronmap.view;

import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.i18n.LocalizationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** One saved-project entry with open, rename and delete actions. */
public final class SavedProjectRowView extends HBox {

    private final LocalizationService localization;
    private ProjectDescriptor project;
    private final Consumer<ProjectDescriptor> openAction;
    private final BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameAction;
    private final Consumer<ProjectDescriptor> deleteAction;
    private final Runnable renameCommitted;

    private final Label modifiedLabel = new Label();
    private final Label nameLabel = new Label();
    private final TextField renameField = new TextField();
    private final Button renameButton = new Button();
    private final Button deleteButton = new Button();
    private final Button cancelButton = new Button();
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();

    public SavedProjectRowView(
            ProjectDescriptor project,
            LocalizationService localization,
            Consumer<ProjectDescriptor> openAction,
            BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameAction,
            Consumer<ProjectDescriptor> deleteAction,
            Runnable renameCommitted
    ) {
        this.project = Objects.requireNonNull(project, "project");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.openAction = Objects.requireNonNull(openAction, "openAction");
        this.renameAction = Objects.requireNonNull(renameAction, "renameAction");
        this.deleteAction = Objects.requireNonNull(deleteAction, "deleteAction");
        this.renameCommitted = Objects.requireNonNull(renameCommitted, "renameCommitted");

        getStyleClass().add("saved-project-row");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10.0);
        setPadding(new Insets(9, 10, 9, 10));
        setMaxWidth(Double.MAX_VALUE);

        modifiedLabel.getStyleClass().add("saved-project-date");
        modifiedLabel.setMinWidth(125.0);
        modifiedLabel.setPrefWidth(125.0);

        nameLabel.getStyleClass().add("saved-project-name");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        renameField.setManaged(false);
        renameField.setVisible(false);
        renameField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(renameField, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        configureButton(renameButton);
        configureButton(deleteButton);
        configureButton(cancelButton);

        getChildren().addAll(
                modifiedLabel,
                nameLabel,
                renameField,
                spacer,
                renameButton,
                deleteButton
        );

        setOnMouseClicked(event -> {
            if (!renameField.isVisible()
                    && event.getTarget() != renameButton
                    && event.getTarget() != deleteButton
                    && event.getTarget() != cancelButton) {
                openAction.accept(project);
            }
        });

        renameButton.setOnAction(event -> {
            if (renameField.isVisible()) {
                commitRename();
            } else {
                beginRename();
            }
            event.consume();
        });

        deleteButton.setOnAction(event -> {
            deleteAction.accept(project);
            event.consume();
        });

        cancelButton.setOnAction(event -> {
            cancelRename();
            event.consume();
        });

        renameField.setOnAction(event -> {
            commitRename();
            event.consume();
        });

        localization.addListener(localizationListener);
        refreshTexts();
    }

    public void dispose() {
        localization.removeListener(localizationListener);
    }

    public ProjectDescriptor project() {
        return project;
    }

    public boolean isEditing() {
        return renameField.isVisible();
    }

    public boolean hasUnsavedChanges() {
        if (!isEditing()) {
            return false;
        }
        String pending = renameField.getText() == null
                ? ""
                : renameField.getText().trim();
        return !pending.equals(project.name());
    }

    public void savePendingChange() {
        if (!hasUnsavedChanges()) {
            cancelRename();
            return;
        }
        commitRename();
    }

    public void discardPendingChange() {
        cancelRename();
    }

    void beginRenameForTest() {
        beginRename();
    }

    TextField renameFieldForTest() {
        return renameField;
    }

    private void beginRename() {
        renameField.setText(project.name());
        renameField.setManaged(true);
        renameField.setVisible(true);
        nameLabel.setManaged(false);
        nameLabel.setVisible(false);
        getChildren().remove(cancelButton);
        getChildren().add(cancelButton);
        renameField.requestFocus();
        renameField.selectAll();
    }

    private void commitRename() {
        String name = renameField.getText() == null
                ? ""
                : renameField.getText().trim();
        if (name.isBlank()) {
            return;
        }

        ProjectDescriptor renamed = renameAction.apply(project, name);
        if (renamed != null) {
            project = renamed;
        }
        endRename();
        renameCommitted.run();
    }

    private void cancelRename() {
        endRename();
    }

    private void endRename() {
        renameField.setManaged(false);
        renameField.setVisible(false);
        nameLabel.setManaged(true);
        nameLabel.setVisible(true);
        getChildren().remove(cancelButton);
        refreshTexts();
    }

    private void configureButton(Button button) {
        button.getStyleClass().add("menu-small-button");
        button.setMinWidth(88.0);
    }

    private void refreshTexts() {
        nameLabel.setText(project.name());
        modifiedLabel.setText(formatModified(
                project.modifiedAt(),
                localization.locale()
        ));

        renameButton.setText(
                isEditing()
                        ? localization.text("menu.rename.save")
                        : localization.text("menu.rename")
        );
        deleteButton.setText(localization.text("menu.delete"));
        cancelButton.setText(localization.text("menu.rename.cancel"));

        renameButton.setTooltip(new Tooltip(localization.text("menu.rename_hint")));
        deleteButton.setTooltip(new Tooltip(localization.text("menu.delete_hint")));
        cancelButton.setTooltip(new Tooltip(localization.text("menu.rename.cancel")));
    }

    private static String formatModified(java.time.Instant instant, Locale locale) {
        if (instant == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }
}
