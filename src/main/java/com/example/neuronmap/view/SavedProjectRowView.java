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

/**
 * Відображає один збережений проєкт у списку головного меню.
 */
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

    /**
     * Повертає результат операції «збережений проєкт відображення».
     *
     * @param project опис проєкту.
     *
     * @param localization значення, що визначає локалізація для цієї операції.
     *
     * @param openAction значення, що визначає відкрити для цієї операції.
     *
     * @param renameAction значення, що визначає відповідну операцію для цієї операції.
     *
     * @param deleteAction значення, що визначає видалити для цієї операції.
     *
     * @param renameCommitted значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    public void dispose() {
        localization.removeListener(localizationListener);
    }

    /**
     * Повертає результат операції «проєкт».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public ProjectDescriptor project() {
        return project;
    }

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isEditing() {
        return renameField.isVisible();
    }

    /**
     * Перевіряє, чи виконується умова «змінює».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean hasUnsavedChanges() {
        if (!isEditing()) {
            return false;
        }
        String pending = renameField.getText() == null
                ? ""
                : renameField.getText().trim();
        return !pending.equals(project.name());
    }

    /**
     * Зберігає дані, повʼязані з «очікуваний змінити», у відповідному сховищі.
     */
    public void savePendingChange() {
        if (!hasUnsavedChanges()) {
            cancelRename();
            return;
        }
        commitRename();
    }

    /**
     * Видаляє або скидає дані, повʼязані з «очікуваний змінити».
     */
    public void discardPendingChange() {
        cancelRename();
    }

    /**
     * Запускає або планує дію, повʼязану з «для».
     */
    void beginRenameForTest() {
        beginRename();
    }

    /**
     * Повертає результат операції «поле для».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    TextField renameFieldForTest() {
        return renameField;
    }

    /**
     * Запускає або планує дію, повʼязану з «відповідну операцію».
     */
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

    /**
     * Виконує операцію «відповідну операцію».
     */
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

    /**
     * Завершує або скасовує дію, повʼязану з «відповідну операцію».
     */
    private void cancelRename() {
        endRename();
    }

    /**
     * Виконує операцію «відповідну операцію».
     */
    private void endRename() {
        renameField.setManaged(false);
        renameField.setVisible(false);
        nameLabel.setManaged(true);
        nameLabel.setVisible(true);
        getChildren().remove(cancelButton);
        refreshTexts();
    }

    /**
     * Задає або оновлює значення, повʼязані з «кнопка».
     *
     * @param button значення, що визначає кнопка для цієї операції.
     */
    private void configureButton(Button button) {
        button.getStyleClass().add("menu-small-button");
        button.setMinWidth(88.0);
    }

    /**
     * Обробляє «відповідну операцію».
     */
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

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param instant значення, що визначає відповідну операцію для цієї операції.
     *
     * @param locale значення, що визначає відповідну операцію для цієї операції.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
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
