package com.example.neuronmap.view;

import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.i18n.LocalizationService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Displays one saved project and manages its open, rename, and delete interactions.
 * The project name keeps the available horizontal space, while edit mode hides unrelated controls
 * so that the editor, Save, and Cancel controls remain inside the menu panel.
 */
public final class SavedProjectRowView extends HBox {

    private static final double MODIFIED_DATE_WIDTH = 92.0;

    private final LocalizationService localization;
    private ProjectDescriptor project;
    private final Consumer<ProjectDescriptor> openAction;
    private final BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameAction;
    private final Consumer<ProjectDescriptor> deleteAction;
    private final Runnable renameCommitted;

    private final Label modifiedLabel = new Label();
    private final Label nameLabel = new Label();
    private TextField renameField;
    private final Button renameButton = new Button();
    private final Button deleteButton = new Button();
    private final Button cancelButton = new Button();
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();

    /**
     * Creates a row for a saved project and connects the callbacks for its available operations.
     *
     * @param project descriptor of the project represented by this row.
     * @param localization localization service used for labels, tooltips, and dates.
     * @param openAction callback that opens this project.
     * @param renameAction callback that saves a new project name and returns the updated descriptor.
     * @param deleteAction callback that deletes this project.
     * @param renameCommitted callback invoked after a rename is committed.
     */
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
        setSpacing(8.0);
        setPadding(new Insets(9.0, 10.0, 9.0, 10.0));
        setMaxWidth(Double.MAX_VALUE);

        modifiedLabel.getStyleClass().add("saved-project-date");
        modifiedLabel.setMinWidth(MODIFIED_DATE_WIDTH);
        modifiedLabel.setPrefWidth(MODIFIED_DATE_WIDTH);
        modifiedLabel.setMaxWidth(MODIFIED_DATE_WIDTH);

        nameLabel.getStyleClass().add("saved-project-name");
        nameLabel.setMinWidth(0.0);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        configureButton(renameButton);
        configureButton(deleteButton);
        configureButton(cancelButton);
        cancelButton.setManaged(false);
        cancelButton.setVisible(false);

        getChildren().addAll(
                modifiedLabel,
                nameLabel,
                renameButton,
                cancelButton,
                deleteButton
        );

        setOnMouseClicked(event -> {
            if (!isEditing()
                    && event.getTarget() != renameButton
                    && event.getTarget() != deleteButton
                    && event.getTarget() != cancelButton) {
                openAction.accept(this.project);
            }
        });

        renameButton.setOnAction(event -> {
            if (isEditing()) {
                commitRename();
            } else {
                beginRename();
            }
            event.consume();
        });
        deleteButton.setOnAction(event -> {
            deleteAction.accept(this.project);
            event.consume();
        });

        cancelButton.setOnAction(event -> {
            cancelRename();
            event.consume();
        });

        localization.addListener(localizationListener);
        refreshTexts();
    }

    /** Removes the localization listener when this row is discarded from the project list. */
    public void dispose() {
        localization.removeListener(localizationListener);
    }

    /**
     * Returns the latest descriptor represented by this row.
     *
     * @return the current project descriptor.
     */
    public ProjectDescriptor project() {
        return project;
    }

    /**
     * Reports whether this row is currently in rename mode.
     *
     * @return {@code true} when the text field and edit controls are visible.
     */
    public boolean isEditing() {
        return renameField != null && renameField.isVisible();
    }

    /**
     * Reports whether the edit field contains a pending change to the project name.
     *
     * @return {@code true} when the edited name differs from the saved project name.
     */
    public boolean hasUnsavedChanges() {
        if (!isEditing()) {
            return false;
        }
        String pending = renameField.getText() == null ? "" : renameField.getText().trim();
        return !pending.equals(project.name());
    }

    /** Commits the pending rename when valid, or exits edit mode if no change is pending. */
    public void savePendingChange() {
        if (!hasUnsavedChanges()) {
            cancelRename();
            return;
        }
        commitRename();
    }

    /** Discards the pending project name and restores the saved name in the row. */
    public void discardPendingChange() {
        cancelRename();
    }

    /** Enters rename mode for tests without simulating a mouse click. */
    void beginRenameForTest() {
        beginRename();
    }

    /**
     * Exposes the rename input for regression tests.
     *
     * @return the text field used to edit the project name.
     */
    TextField renameFieldForTest() {
        return renameField;
    }

    /**
     * Exposes the Rename button so UI regression tests can trigger the normal action handler.
     *
     * @return the button that starts or commits project renaming.
     */
    Button renameButtonForTest() {
        return renameButton;
    }

    /**
     * Exposes the displayed project name for regression tests.
     *
     * @return the label that displays the saved project name.
     */
    Label nameLabelForTest() {
        return nameLabel;
    }

    /**
     * Exposes the Delete button for regression tests.
     *
     * @return the button that deletes the project.
     */
    Button deleteButtonForTest() {
        return deleteButton;
    }

    /**
     * Exposes the Cancel button for regression tests.
     *
     * @return the button that cancels the current rename operation.
     */
    Button cancelButtonForTest() {
        return cancelButton;
    }

    /**
     * Enters edit mode with a fresh text field and only the relevant controls visible.
     * The field is laid out while empty before the saved name is inserted, avoiding reuse of a JavaFX
     * skin initialized while an old field was unmanaged and had no layout width.
     */
    private void beginRename() {
        int nameIndex = getChildren().indexOf(nameLabel);
        renameField = createRenameField();
        getChildren().set(nameIndex, renameField);
        setManagedAndVisible(modifiedLabel, false);
        setManagedAndVisible(nameLabel, false);
        setManagedAndVisible(deleteButton, false);
        setManagedAndVisible(cancelButton, true);
        refreshTexts();
        requestLayout();

        layoutRenameField();
        renameField.setText(project.name());
        renameField.requestFocus();
        synchronizeRenameFieldLayoutAndCaret();
        scheduleRenameCaretRestoration();
    }

    /**
     * Creates a styled, initially empty text field wired to commit on Enter.
     * The field is created only when rename mode starts so its JavaFX skin can be initialized with a
     * real layout slot before the saved name is inserted and the caret is moved to its end.
     *
     * @return a fresh text field used for the current rename operation.
     */
    private TextField createRenameField() {
        TextField field = new TextField();
        field.getStyleClass().add("saved-project-rename-field");
        field.setMinWidth(0.0);
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setOnAction(event -> {
            commitRename();
            event.consume();
        });
        return field;
    }

    /**
     * Applies CSS and lays out the scene hierarchy and the newly attached rename field.
     * The first layout deliberately happens while the field is empty, so JavaFX creates its text
     * skin with the final row width before any saved-name glyphs or caret geometry are calculated.
     */
    private void layoutRenameField() {
        if (!isEditing() || renameField == null) {
            return;
        }

        Scene scene = getScene();
        if (scene != null) {
            Parent root = scene.getRoot();
            root.applyCss();
            root.requestLayout();
            root.layout();
        }

        applyCss();
        requestLayout();
        layout();

        renameField.applyCss();
        renameField.requestLayout();
        renameField.layout();
    }

    /**
     * Recomputes the text field's rendered caret after the saved name has been inserted and styled.
     */
    private void synchronizeRenameFieldLayoutAndCaret() {
        if (!isEditing() || renameField == null) {
            return;
        }

        layoutRenameField();
        positionRenameCaretAtEnd();
        renameField.requestLayout();
        renameField.layout();
    }

    /**
     * Schedules one additional caret synchronization after the rename-button event has completed.
     * A queued callback alone does not guarantee a JavaFX layout pulse, so the callback explicitly
     * lays out the current scene hierarchy and text field before setting the rendered caret position.
     */
    private void scheduleRenameCaretRestoration() {
        Platform.runLater(() -> {
            if (!isEditing()) {
                return;
            }
            renameField.requestFocus();
            synchronizeRenameFieldLayoutAndCaret();
        });
    }

    /**
     * Clears any selection and places the insertion caret at the end of the current project name.
     */
    private void positionRenameCaretAtEnd() {
        if (!isEditing()) {
            return;
        }
        int endPosition = renameField.getLength();
        renameField.selectRange(endPosition, endPosition);
    }

    /** Validates and commits the name currently entered in the rename field. */
    private void commitRename() {
        String name = renameField.getText() == null ? "" : renameField.getText().trim();
        if (name.isBlank()) {
            renameField.requestFocus();
            return;
        }

        ProjectDescriptor renamed = renameAction.apply(project, name);
        if (renamed != null) {
            project = renamed;
        }
        endRename();
        renameCommitted.run();
    }

    /** Cancels rename mode without invoking the project catalog callback. */
    private void cancelRename() {
        endRename();
    }

    /** Restores the normal row layout and refreshes the saved name and localized labels. */
    private void endRename() {
        TextField completedField = renameField;
        if (completedField != null) {
            int fieldIndex = getChildren().indexOf(completedField);
            if (fieldIndex >= 0) {
                getChildren().set(fieldIndex, nameLabel);
            }
            completedField.setOnAction(null);
            renameField = null;
        }
        setManagedAndVisible(modifiedLabel, true);
        setManagedAndVisible(nameLabel, true);
        setManagedAndVisible(cancelButton, false);
        setManagedAndVisible(deleteButton, true);
        refreshTexts();
        requestLayout();
    }

    /**
     * Applies the same visibility and layout state to a row control.
     *
     * @param node control whose state must be updated.
     * @param visible whether the control should be visible and take up layout space.
     */
    private static void setManagedAndVisible(javafx.scene.Node node, boolean visible) {
        node.setManaged(visible);
        node.setVisible(visible);
    }

    /** Configures the shared visual and sizing rules for small project-action buttons. */
    private void configureButton(Button button) {
        button.getStyleClass().add("menu-small-button");
        button.setMinWidth(70.0);
    }

    /** Refreshes the project name, modified date, action labels, and tooltips for the active language. */
    private void refreshTexts() {
        nameLabel.setText(project.name());
        modifiedLabel.setText(formatModified(project.modifiedAt(), localization.locale()));
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

    /** Formats a modification timestamp using the current UI locale and system time zone. */
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
