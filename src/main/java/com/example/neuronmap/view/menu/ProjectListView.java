package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.view.support.EditorKeyboardShortcutBoundary;
import com.example.neuronmap.view.support.SceneKeyboardModality;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Відображає список збережених проєктів, керує вибором рядка та локальною клавіатурною навігацією. */
public final class ProjectListView extends VBox {

    /** Ширина вертикальної смуги прокручування, задана стилями списку. */
    private static final double VERTICAL_SCROLL_BAR_WIDTH = 9.0;
    private static final String STYLESHEET_RESOURCE = "/project-list.css";

    private final LocalizationService localization;
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();
    private final EventHandler<KeyEvent> keyHandler = this::handleKeyPressed;
    private final VBox projectRowsBox = new VBox(6.0);
    private final Label emptyProjectsLabel = new Label();
    private List<SavedProjectRowView> projectRows = List.of();
    private SavedProjectRowView selectedRow;
    private ScrollPane scrollPane;

    /**
     * Створює контейнер списку збережених проєктів і встановлює обробник навігації для сфокусованих рядків.
     *
     * @param localization служба локалізації підпису порожнього списку та рядків проєктів.
     * @throws NullPointerException якщо службу локалізації не задано.
     * @throws IllegalStateException якщо таблиця стилів списку відсутня в classpath.
     */
    public ProjectListView(LocalizationService localization) {
        this.localization = Objects.requireNonNull(localization, "localization");
        URL stylesheet = getClass().getResource(STYLESHEET_RESOURCE);
        if (stylesheet == null) {
            throw new IllegalStateException(STYLESHEET_RESOURCE + " is missing");
        }
        getStylesheets().add(stylesheet.toExternalForm());
        setMinSize(0.0, 0.0);
        setFillWidth(true);
        emptyProjectsLabel.getStyleClass().add("project-list-empty-hint");
        projectRowsBox.setMinSize(0.0, 0.0);
        localization.addListener(localizationListener);
        refreshTexts();
    }

    /**
     * Формує список проєктів без окремого callback-а завершення перейменування.
     * Цей варіант призначений для автономного використання сторінки.
     *
     * @param projects описи проєктів; {@code null} означає порожній список.
     * @param openAction дія відкриття проєкту після подвійного кліку або натискання Enter на сфокусованому рядку.
     * @param renameAction дія перейменування з поверненням оновленого опису.
     * @param deleteAction дія видалення проєкту.
     */
    public void showProjects(
            List<ProjectDescriptor> projects,
            Consumer<ProjectDescriptor> openAction,
            BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameAction,
            Consumer<ProjectDescriptor> deleteAction
    ) {
        showProjects(projects, openAction, renameAction, deleteAction, () -> { });
    }

    /**
     * Формує прокручуваний список і передає всі операції над проєктами заданим callback-ам.
     * Рядки отримують фокус після звичайного кліку; стрілки та W/S переміщують його в межах списку.
     *
     * @param projects описи проєктів; {@code null} означає порожній список.
     * @param openAction дія відкриття проєкту після подвійного кліку або натискання Enter на сфокусованому рядку.
     * @param renameAction дія перейменування з поверненням оновленого опису.
     * @param deleteAction дія видалення проєкту.
     * @param renameCommitted callback після підтвердженого перейменування.
     */
    public void showProjects(
            List<ProjectDescriptor> projects,
            Consumer<ProjectDescriptor> openAction,
            BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameAction,
            Consumer<ProjectDescriptor> deleteAction,
            Runnable renameCommitted
    ) {
        Objects.requireNonNull(openAction, "openAction");
        Objects.requireNonNull(renameAction, "renameAction");
        Objects.requireNonNull(deleteAction, "deleteAction");
        Objects.requireNonNull(renameCommitted, "renameCommitted");
        clearRows();
        refreshTexts();

        boolean empty = projects == null || projects.isEmpty();
        if (empty) {
            projectRowsBox.getChildren().setAll(emptyProjectsLabel);
        } else {
            projectRows = projects.stream()
                    .map(project -> new SavedProjectRowView(
                            project,
                            localization,
                            openAction,
                            renameAction,
                            deleteAction,
                            renameCommitted,
                            this::selectRow))
                    .toList();
            projectRows.forEach(row -> row.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler));
            projectRowsBox.getChildren().setAll(projectRows);
        }

        scrollPane = new ScrollPane(projectRowsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setMinWidth(0.0);
        scrollPane.setMinHeight(0.0);
        scrollPane.getStyleClass().add("main-menu-scroll");
        int visibleRows = empty ? 1 : projects.size();
        scrollPane.setPrefViewportHeight(Math.clamp(visibleRows * 50.0, 58.0, 260.0));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().setAll(scrollPane);
    }

    /** Оновлює локалізований напис для випадку, коли немає збережених проєктів. */
    public void refreshTexts() {
        emptyProjectsLabel.setText(localization.text("menu.no_saved_projects"));
    }

    /**
     * Обчислює ширину списку з урахуванням повної бажаної ширини рядків, а не лише ширини viewport.
     *
     * <p>ScrollPane із ввімкненим {@code fitToWidth} може стискати свій вміст до ширини viewport.
     * Для початкового розміщення меню тому враховується природна ширина рядків разом із додатковою
     * шириною, яку потребує сам ScrollPane.</p>
     *
     * @return бажана ширина сторінки списку до застосування зовнішнього обмеження вікна.
     */
    double preferredWidthForLayout() {
        double preferredWidth = prefWidth(-1.0);
        if (scrollPane == null || projectRows.isEmpty()) {
            return preferredWidth;
        }

        double widestRow = projectRows.stream()
                .mapToDouble(SavedProjectRowView::preferredWidthForLayout)
                .max()
                .orElse(0.0);
        double rowsHorizontalInsets = projectRowsBox.getInsets().getLeft()
                + projectRowsBox.getInsets().getRight();
        double rowsWidth = Math.max(projectRowsBox.prefWidth(-1.0), widestRow + rowsHorizontalInsets);
        double scrollPaneHorizontalInsets = scrollPane.getInsets().getLeft()
                + scrollPane.getInsets().getRight();

        // Смуга резервується також до моменту появи skin-а: довгий список не має стискати його рядки.
        double requiredScrollPaneWidth = rowsWidth + scrollPaneHorizontalInsets + VERTICAL_SCROLL_BAR_WIDTH;
        return Math.max(preferredWidth, Math.max(scrollPane.prefWidth(-1.0), requiredScrollPaneWidth));
    }

    /**
     * Перевіряє, чи мають рядки списку введені, але ще не підтверджені назви.
     *
     * @return {@code true}, якщо хоча б один рядок має незбережену зміну назви.
     */
    public boolean hasUnsavedChanges() {
        return projectRows.stream().anyMatch(SavedProjectRowView::hasUnsavedChanges);
    }

    /** Підтверджує незавершені зміни назв у порядку відображення рядків. */
    public void savePendingChanges() {
        for (SavedProjectRowView row : projectRows) {
            row.savePendingChange();
        }
    }

    /** Відхиляє незавершені зміни назв у всіх рядках списку. */
    public void discardPendingChanges() {
        for (SavedProjectRowView row : projectRows) {
            row.discardPendingChange();
        }
    }

    /** Звільняє слухачів рядків і прибирає вміст сторінки перед переходом до іншої сторінки. */
    public void clear() {
        clearRows();
        getChildren().clear();
        scrollPane = null;
    }

    /** Звільняє ресурси рядків, клавіатурний обробник та слухача локалізації. */
    public void dispose() {
        clear();
        localization.removeListener(localizationListener);
    }

    /**
     * Повертає поточний прокручуваний список для перевірок адаптивного компонування в цьому пакеті.
     *
     * @return поточний список або {@code null}, якщо сторінку ще не побудовано.
     */
    ScrollPane scrollPaneForTest() {
        return scrollPane;
    }

    /**
     * Повертає незмінний знімок рядків проєктів для тестів користувацьких сценаріїв у цьому пакеті.
     *
     * @return незмінний список поточних рядків проєктів.
     */
    List<SavedProjectRowView> rowsForTest() {
        return List.copyOf(projectRows);
    }

    /**
     * Залишає вибраним лише рядок, на який востаннє натиснув користувач або на який перейшов фокус.
     *
     * @param row рядок, який потрібно вибрати.
     */
    private void selectRow(SavedProjectRowView row) {
        if (!projectRows.contains(row)) {
            return;
        }
        if (selectedRow != null && selectedRow != row) {
            selectedRow.setSelected(false);
        }
        selectedRow = row;
        selectedRow.setSelected(true);
    }

    /**
     * Обробляє клавіатурні команди лише для сфокусованого рядка, не втручаючись у кнопки та поле перейменування.
     *
     * @param event подія натискання клавіші, що проходить через список проєктів.
     */
    private void handleKeyPressed(KeyEvent event) {
        if (event.isConsumed() || !(event.getTarget() instanceof SavedProjectRowView row)
                || !projectRows.contains(row) || row.isEditing() || hasModifiers(event)
                || EditorKeyboardShortcutBoundary.blocksShortcutsFor(event.getTarget())
                || SceneKeyboardModality.hasActiveBoundaryFor(event.getTarget())) {
            return;
        }

        switch (event.getCode()) {
            case ENTER -> {
                row.openProject();
                event.consume();
            }
            case UP, W -> {
                moveFocus(row, -1);
                event.consume();
            }
            case DOWN, S -> {
                moveFocus(row, 1);
                event.consume();
            }
            default -> {
                // Інші клавіші залишаються доступними стандартній обробці JavaFX.
            }
        }
    }

    /** Перевіряє, чи натиснута клавіша супроводжується модифікатором, що змінює її призначення. */
    private static boolean hasModifiers(KeyEvent event) {
        return event.isShiftDown() || event.isControlDown() || event.isAltDown() || event.isMetaDown();
    }

    /** Переміщує вибір і клавіатурний фокус на сусідній рядок, обмежуючи перехід межами списку. */
    private void moveFocus(SavedProjectRowView currentRow, int direction) {
        int currentIndex = projectRows.indexOf(currentRow);
        if (currentIndex < 0 || projectRows.isEmpty()) {
            return;
        }
        int nextIndex = Math.clamp(currentIndex + direction, 0, projectRows.size() - 1);
        focusRow(projectRows.get(nextIndex));
    }

    /** Встановлює вибір та фокус на рядок і прокручує список лише за потреби. */
    private void focusRow(SavedProjectRowView row) {
        selectRow(row);
        row.requestFocus();
        revealRow(row);
    }

    /** Прокручує viewport так, щоб межі сфокусованого рядка залишалися видимими. */
    private void revealRow(SavedProjectRowView row) {
        if (scrollPane == null || scrollPane.getScene() == null) {
            return;
        }
        scrollPane.applyCss();
        scrollPane.layout();
        projectRowsBox.layout();
        Bounds viewport = scrollPane.getViewportBounds();
        double viewportHeight = viewport.getHeight();
        double contentHeight = projectRowsBox.getLayoutBounds().getHeight();
        double scrollableHeight = contentHeight - viewportHeight;
        double valueRange = scrollPane.getVmax() - scrollPane.getVmin();
        if (viewportHeight <= 0.0 || scrollableHeight <= 0.0 || valueRange <= 0.0) {
            return;
        }

        Bounds rowBounds = row.getBoundsInParent();
        double normalizedValue = (scrollPane.getVvalue() - scrollPane.getVmin()) / valueRange;
        double currentTop = normalizedValue * scrollableHeight;
        double desiredTop = currentTop;
        if (rowBounds.getMinY() < currentTop) {
            desiredTop = rowBounds.getMinY();
        } else if (rowBounds.getMaxY() > currentTop + viewportHeight) {
            desiredTop = rowBounds.getMaxY() - viewportHeight;
        }
        double boundedTop = Math.clamp(desiredTop, 0.0, scrollableHeight);
        if (Double.compare(currentTop, boundedTop) != 0) {
            scrollPane.setVvalue(scrollPane.getVmin() + boundedTop / scrollableHeight * valueRange);
        }
    }

    /** Звільняє ресурси рядків і скидає вибір перед побудовою нового вмісту списку. */
    private void clearRows() {
        if (selectedRow != null) {
            selectedRow.setSelected(false);
        }
        for (SavedProjectRowView row : projectRows) {
            row.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
            row.dispose();
        }
        projectRows = List.of();
        selectedRow = null;
        projectRowsBox.getChildren().clear();
        if (scrollPane != null) {
            scrollPane.setContent(null);
        }
    }
}
