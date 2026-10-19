package com.example.neuronmap.view.menu;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.Objects;

/** Керує полем inline-редагування назви проєкту, його фокусом і компонуванням. */
final class SavedProjectRenameEditor {

    private TextField field;

    /**
     * Починає редагування з наявною назвою та повертає поле для вставлення в макет рядка.
     *
     * @param projectName поточна назва проєкту.
     * @param confirmAction дія власника рядка після натискання Enter.
     * @return створене поле введення.
     * @throws IllegalStateException якщо редактор уже активний.
     * @throws NullPointerException якщо назву або callback не задано.
     */
    TextField begin(String projectName, Runnable confirmAction) {
        if (field != null) {
            throw new IllegalStateException("Project name editor is already active");
        }
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(confirmAction, "confirmAction");
        field = new TextField(projectName);
        field.getStyleClass().add("saved-project-rename-field");
        field.setMinWidth(0.0);
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setOnAction(event -> {
            confirmAction.run();
            event.consume();
        });
        return field;
    }

    /** Повертає поле введення для компонування й точкової перевірки в тестах рядка. */
    TextField field() {
        return field;
    }

    /** Перевіряє, чи існує активне поле редагування. */
    boolean isEditing() {
        return field != null && field.isVisible();
    }

    /** Повертає введений текст або порожній рядок, якщо редагування не розпочато. */
    String text() {
        return field == null || field.getText() == null ? "" : field.getText();
    }

    /**
     * Застосовує стилі та компонування до поля й розміщує каретку в кінці назви.
     *
     * @param row рядок, у якому міститься редактор.
     */
    void synchronizeLayoutAndCaret(SavedProjectRowView row) {
        if (!isEditing()) {
            return;
        }
        layoutEditor(row);
        field.requestFocus();
        positionCaretAtEnd();
        field.requestLayout();
        field.layout();
    }

    /** Планує відновлення фокуса й позиції каретки після завершення поточного циклу подій JavaFX. */
    void scheduleCaretRestoration(SavedProjectRowView row) {
        Platform.runLater(() -> {
            if (!isEditing()) {
                return;
            }
            field.requestFocus();
            synchronizeLayoutAndCaret(row);
        });
    }

    /** Від'єднує callback поля та звільняє посилання на вузол після завершення редагування. */
    void finish() {
        if (field == null) {
            return;
        }
        field.setOnAction(null);
        field = null;
    }

    /** Застосовує CSS і компонування рядка та поля для коректного вимірювання їхньої ширини. */
    private void layoutEditor(SavedProjectRowView row) {
        Scene scene = row.getScene();
        if (scene != null) {
            Parent root = scene.getRoot();
            root.applyCss();
            root.requestLayout();
            root.layout();
        }
        row.applyCss();
        row.requestLayout();
        row.layout();
        field.applyCss();
        field.requestLayout();
        field.layout();
    }

    /** Прибирає виділення введення та встановлює каретку в кінець поточної назви. */
    private void positionCaretAtEnd() {
        int endPosition = field.getLength();
        field.selectRange(endPosition, endPosition);
    }
}
