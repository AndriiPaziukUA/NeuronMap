package com.example.neuronmap.view.menu;

import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.i18n.LocalizationService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
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
 * Відображає рядок збереженого проєкту та керує його відкриттям, перейменуванням і видаленням.
 */
public final class SavedProjectRowView extends HBox {

    private static final double MODIFIED_DATE_WIDTH = 92.0;

    private final LocalizationService localization;
    private ProjectDescriptor project;
    private final BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameAction;
    private final Runnable renameCommitted;

    private final Label modifiedLabel = new Label();
    private final Label nameLabel = new Label();
    private TextField renameField;
    private final Button renameButton = new Button();
    private final Button deleteButton = new Button();
    private final Button cancelButton = new Button();
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();

    /**
     * Створює рядок проєкту й підключає зворотні виклики доступних операцій.
     *
     * @param project опис проєкту, який показує цей рядок.
     * @param localization служба локалізації підписів, підказок і дат.
     * @param openAction дія відкриття проєкту.
     * @param renameAction дія збереження нової назви з поверненням оновленого опису.
     * @param deleteAction дія видалення проєкту.
     * @param renameCommitted дія, що викликається після підтвердження перейменування.
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
        this.renameAction = Objects.requireNonNull(renameAction, "renameAction");
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

    /** Від'єднує слухача локалізації, коли рядок вилучають зі списку проєктів. */
    public void dispose() {
        localization.removeListener(localizationListener);
    }

    /**
     * Повертає актуальний опис проєкту, який представляє цей рядок.
     *
     * @return поточний опис проєкту.
     */
    public ProjectDescriptor project() {
        return project;
    }

    /**
     * Перевіряє, чи перебуває рядок у режимі перейменування.
     *
     * @return {@code true}, якщо поле введення й елементи редагування видимі.
     */
    public boolean isEditing() {
        return renameField != null && renameField.isVisible();
    }

    /**
     * Перевіряє, чи містить поле редагування незбережену зміну назви.
     *
     * @return {@code true}, якщо введена назва відрізняється від збереженої.
     */
    public boolean hasUnsavedChanges() {
        if (!isEditing()) {
            return false;
        }
        String pending = renameField.getText() == null ? "" : renameField.getText().trim();
        return !pending.equals(project.name());
    }

    /** Зберігає коректну незавершену зміну назви або виходить із режиму редагування без змін. */
    public void savePendingChange() {
        if (!hasUnsavedChanges()) {
            cancelRename();
            return;
        }
        commitRename();
    }

    /** Відхиляє введену назву та повертає рядок до збереженого стану. */
    public void discardPendingChange() {
        cancelRename();
    }

    /** Вмикає режим перейменування для тестів без імітації натискання миші. */
    void beginRenameForTest() {
        beginRename();
    }

    /**
     * Надає поле введення назви для регресійних тестів.
     *
     * @return поле редагування назви проєкту.
     */
    TextField renameFieldForTest() {
        return renameField;
    }

    /**
     * Надає кнопку перейменування для запуску звичайного обробника в UI-тестах.
     *
     * @return кнопка початку або підтвердження перейменування.
     */
    Button renameButtonForTest() {
        return renameButton;
    }

    /**
     * Надає підпис назви проєкту для регресійних тестів.
     *
     * @return мітка з назвою збереженого проєкту.
     */
    Label nameLabelForTest() {
        return nameLabel;
    }

    /**
     * Надає кнопку видалення для регресійних тестів.
     *
     * @return кнопка видалення проєкту.
     */
    Button deleteButtonForTest() {
        return deleteButton;
    }

    /**
     * Надає кнопку скасування для регресійних тестів.
     *
     * @return кнопка скасування поточного перейменування.
     */
    Button cancelButtonForTest() {
        return cancelButton;
    }

    /** Вмикає режим редагування з новим полем введення та залишає видимими лише потрібні елементи керування. */
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
     * Створює стилізоване порожнє поле введення, яке підтверджує перейменування клавішею Enter.
     *
     * @return нове поле введення для поточного перейменування.
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

    /** Застосовує CSS і розміщує нове поле введення в ієрархії сцени, щоб JavaFX коректно обчислив ширину поля. */
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

    /** Оновлює положення каретки після вставлення збереженої назви та застосування стилів. */
    private void synchronizeRenameFieldLayoutAndCaret() {
        if (!isEditing() || renameField == null) {
            return;
        }

        layoutRenameField();
        positionRenameCaretAtEnd();
        renameField.requestLayout();
        renameField.layout();
    }

    /** Планує додаткове оновлення каретки після завершення обробника натискання кнопки перейменування. */
    private void scheduleRenameCaretRestoration() {
        Platform.runLater(() -> {
            if (!isEditing()) {
                return;
            }
            renameField.requestFocus();
            synchronizeRenameFieldLayoutAndCaret();
        });
    }

    /** Прибирає виділення тексту та встановлює каретку в кінець поточної назви проєкту. */
    private void positionRenameCaretAtEnd() {
        if (!isEditing()) {
            return;
        }
        int endPosition = renameField.getLength();
        renameField.selectRange(endPosition, endPosition);
    }

    /** Перевіряє та зберігає введену назву проєкту. */
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

    /** Скасовує режим перейменування без виклику операції каталогу проєктів. */
    private void cancelRename() {
        endRename();
    }

    /** Повертає звичайний макет рядка та оновлює збережену назву й локалізовані підписи. */
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
     * Узгоджує видимість і участь елемента в компонуванні.
     *
     * @param node елемент, стан якого потрібно змінити.
     * @param visible ознака видимості та участі в компонуванні.
     */
    private static void setManagedAndVisible(javafx.scene.Node node, boolean visible) {
        node.setManaged(visible);
        node.setVisible(visible);
    }

    /** Налаштовує спільний стиль і розміри кнопок дій над проєктом. */
    private void configureButton(Button button) {
        button.getStyleClass().add("menu-small-button");
        button.setMinWidth(70.0);
    }

    /** Оновлює назву, дату зміни, підписи дій і підказки відповідно до активної мови. */
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

    /** Форматує час зміни відповідно до локалі інтерфейсу та системного часового поясу. */
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
