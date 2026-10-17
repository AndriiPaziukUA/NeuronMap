package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.project.ProjectDescriptor;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Відображає рядок збереженого проєкту: окремий клік вибирає його, подвійний — відкриває.
 * Рядок також керує перейменуванням і видаленням проєкту, не виконуючи операцій сховища самостійно.
 */
public final class SavedProjectRowView extends HBox {

    private static final double MODIFIED_DATE_WIDTH = 92.0;
    private static final double ICON_BUTTON_SIZE = 36.0;

    private final LocalizationService localization;
    private final Consumer<ProjectDescriptor> openAction;
    private final Consumer<SavedProjectRowView> selectAction;
    private ProjectDescriptor project;
    private final BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameAction;
    private final Runnable renameCommitted;

    private final Label modifiedLabel = new Label();
    private final Label nameLabel = new Label();
    private final SVGPath renameIcon = createRenameIcon();
    private final Region deleteIcon = createDeleteIcon();
    private TextField renameField;
    private final Button renameButton = new Button();
    private final Button deleteButton = new Button();
    private final Button cancelButton = new Button();
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();
    private boolean selected;

    /**
     * Створює рядок проєкту зі стандартною поведінкою без зовнішнього менеджера вибору.
     * Цей конструктор зберігає сумісність із автономним використанням рядка; списки зазвичай
     * використовують перевантажений конструктор, щоб підтримувати єдиний вибраний пункт.
     *
     * @param project опис проєкту, який показує цей рядок.
     * @param localization служба локалізації підписів, підказок і дат.
     * @param openAction дія відкриття проєкту після подвійного кліку.
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
        this(project, localization, openAction, renameAction, deleteAction, renameCommitted, ignored -> { });
    }

    /**
     * Створює рядок проєкту та під'єднує callback-и відкриття, вибору й керування проєктом.
     *
     * @param project опис проєкту, який показує цей рядок.
     * @param localization служба локалізації підписів, підказок і дат.
     * @param openAction дія відкриття проєкту після подвійного кліку.
     * @param renameAction дія збереження нової назви з поверненням оновленого опису.
     * @param deleteAction дія видалення проєкту.
     * @param renameCommitted дія, що викликається після підтвердження перейменування.
     * @param selectAction дія вибору рядка; її власник відповідає за скасування попереднього вибору.
     */
    public SavedProjectRowView(
            ProjectDescriptor project,
            LocalizationService localization,
            Consumer<ProjectDescriptor> openAction,
            BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameAction,
            Consumer<ProjectDescriptor> deleteAction,
            Runnable renameCommitted,
            Consumer<SavedProjectRowView> selectAction
    ) {
        this.project = Objects.requireNonNull(project, "project");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.openAction = Objects.requireNonNull(openAction, "openAction");
        this.renameAction = Objects.requireNonNull(renameAction, "renameAction");
        Objects.requireNonNull(deleteAction, "deleteAction");
        this.renameCommitted = Objects.requireNonNull(renameCommitted, "renameCommitted");
        this.selectAction = Objects.requireNonNull(selectAction, "selectAction");

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
        configureIconButton(renameButton, "saved-project-rename-button", renameIcon);
        configureIconButton(deleteButton, "saved-project-delete-button", deleteIcon);
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
            if (isEditing() || isActionTarget(event.getTarget())) {
                return;
            }
            selectAction.accept(this);
            if (event.getClickCount() == 2) {
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

    /**
     * Повертає ознаку постійного вибору рядка у списку.
     *
     * @return {@code true}, якщо рядок вибрано останнім кліком.
     */
    boolean isSelected() {
        return selected;
    }

    /**
     * Змінює стан вибору й оновлює CSS-клас, не втручаючись у наведення миші.
     *
     * @param selected {@code true}, якщо цей рядок має залишатися вибраним.
     */
    void setSelected(boolean selected) {
        this.selected = selected;
        if (selected) {
            if (!getStyleClass().contains("selected")) {
                getStyleClass().add("selected");
            }
        } else {
            getStyleClass().remove("selected");
        }
    }

    /**
     * Обчислює ширину, потрібну для повного розміщення всіх керованих дочірніх елементів рядка.
     *
     * <p>Значення враховує природну ширину текстової мітки або кнопок у поточному режимі, а не лише
     * ширину рядка, яку {@code ScrollPane} міг отримати після стискання до viewport.</p>
     *
     * @return мінімальна бажана ширина рядка для поточного стану інтерфейсу.
     */
    double preferredWidthForLayout() {
        Insets insets = getInsets();
        double requiredWidth = insets.getLeft() + insets.getRight();
        int managedChildren = 0;
        for (Node child : getChildren()) {
            if (!child.isManaged()) {
                continue;
            }
            if (managedChildren > 0) {
                requiredWidth += getSpacing();
            }
            requiredWidth += Math.max(child.minWidth(-1.0), child.prefWidth(-1.0));
            managedChildren++;
        }
        return Math.max(requiredWidth, prefWidth(-1.0));
    }

    /**
     * Визначає, чи належить цільова подія до кнопок або поля редагування рядка.
     * Підняття події від графічної іконки кнопки не має трактуватися як клік по самому проєкту.
     *
     * @param target фактична ціль події миші.
     * @return {@code true}, якщо подію породив елемент керування рядка.
     */
    private boolean isActionTarget(Object target) {
        if (!(target instanceof Node targetNode)) {
            return false;
        }
        Node current = targetNode;
        while (current != null && current != this) {
            if (current == renameButton || current == deleteButton
                    || current == cancelButton || current == renameField) {
                return true;
            }
            current = current.getParent();
        }
        return false;
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
    private static void setManagedAndVisible(Node node, boolean visible) {
        node.setManaged(visible);
        node.setVisible(visible);
    }

    /** Налаштовує спільний стиль кнопки та її повну природну ширину для читабельного підпису. */
    private void configureButton(Button button) {
        button.getStyleClass().add("menu-small-button");
        button.setMinWidth(Region.USE_PREF_SIZE);
    }

    /** Налаштовує компактну кнопку з графічним значком замість видимого підпису. */
    private void configureIconButton(Button button, String styleClass, Node graphic) {
        button.getStyleClass().add(styleClass);
        button.setText("");
        button.setGraphic(graphic);
        button.setMinWidth(ICON_BUTTON_SIZE);
        button.setPrefWidth(ICON_BUTTON_SIZE);
        button.setMaxWidth(ICON_BUTTON_SIZE);
        button.setMinHeight(30.0);
        button.setPrefHeight(30.0);
        button.setMaxHeight(30.0);
    }

    /** Створює контурний значок олівця, типовий для дії редагування назви. */
    private static SVGPath createRenameIcon() {
        SVGPath icon = new SVGPath();
        icon.setContent("M12 20h9 M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4Z");
        icon.getStyleClass().add("saved-project-icon");
        icon.setFill(javafx.scene.paint.Color.TRANSPARENT);
        icon.setStrokeLineCap(StrokeLineCap.ROUND);
        icon.setStrokeLineJoin(StrokeLineJoin.ROUND);
        icon.setStrokeWidth(2.2);
        icon.setMouseTransparent(true);
        return icon;
    }

    /** Створює невеликий прямокутний значок у вигляді товстого горизонтального дефіса. */
    private static Region createDeleteIcon() {
        Region icon = new Region();
        icon.getStyleClass().add("saved-project-delete-icon");
        icon.setMouseTransparent(true);
        return icon;
    }

    /** Оновлює назву, дату, доступні назви кнопок і підказки відповідно до активної мови та стану рядка. */
    private void refreshTexts() {
        nameLabel.setText(project.name());
        modifiedLabel.setText(formatModified(project.modifiedAt(), localization.locale()));

        boolean editing = isEditing();
        String renameText = localization.text("menu.rename");
        String deleteText = localization.text("menu.delete");
        String saveText = localization.text("menu.rename.save");
        String cancelText = localization.text("menu.rename.cancel");

        renameButton.getStyleClass().removeAll("saved-project-rename-button", "saved-project-save-button");
        renameButton.getStyleClass().add(editing ? "saved-project-save-button" : "saved-project-rename-button");
        renameButton.setText(editing ? saveText : "");
        renameButton.setGraphic(editing ? null : renameIcon);
        renameButton.setAccessibleText(editing ? saveText : renameText);
        renameButton.setTooltip(editing ? null : new Tooltip(renameText));
        if (editing) {
            renameButton.setMinWidth(Region.USE_PREF_SIZE);
            renameButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
            renameButton.setMaxWidth(Region.USE_PREF_SIZE);
            cancelButton.setMinWidth(Region.USE_PREF_SIZE);
            cancelButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
            cancelButton.setMaxWidth(Region.USE_PREF_SIZE);
        } else {
            renameButton.setMinWidth(ICON_BUTTON_SIZE);
            renameButton.setPrefWidth(ICON_BUTTON_SIZE);
            renameButton.setMaxWidth(ICON_BUTTON_SIZE);
        }

        deleteButton.setText("");
        deleteButton.setGraphic(deleteIcon);
        deleteButton.setAccessibleText(deleteText);
        deleteButton.setTooltip(new Tooltip(deleteText));

        cancelButton.setText(cancelText);
        cancelButton.setAccessibleText(cancelText);
        cancelButton.setTooltip(null);
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
