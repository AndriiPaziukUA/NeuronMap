package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.project.ProjectDescriptor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Відображає список збережених проєктів і керує життєвим циклом його рядків та вибором одного рядка. */
public final class ProjectListView extends VBox {

    /** Ширина вертикальної смуги прокручування, задана стилями списку меню. */
    private static final double VERTICAL_SCROLL_BAR_WIDTH = 9.0;

    private final LocalizationService localization;
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();
    private final VBox projectRowsBox = new VBox(6.0);
    private final Label emptyProjectsLabel = new Label();
    private List<SavedProjectRowView> projectRows = List.of();
    private SavedProjectRowView selectedRow;
    private ScrollPane scrollPane;

    /**
     * Створює контейнер списку збережених проєктів.
     *
     * @param localization служба локалізації підпису порожнього списку та рядків проєктів.
     */
    public ProjectListView(LocalizationService localization) {
        this.localization = Objects.requireNonNull(localization, "localization");
        setMinSize(0.0, 0.0);
        setFillWidth(true);
        emptyProjectsLabel.getStyleClass().add("main-menu-hint");
        projectRowsBox.setMinSize(0.0, 0.0);
        localization.addListener(localizationListener);
        refreshTexts();
    }

    /**
     * Формує список проєктів без окремого callback-а завершення перейменування.
     * Цей варіант призначений для автономного використання сторінки.
     *
     * @param projects описи проєктів; {@code null} означає порожній список.
     * @param openAction дія відкриття вибраного проєкту після подвійного кліку.
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
     *
     * @param projects описи проєктів; {@code null} означає порожній список.
     * @param openAction дія відкриття вибраного проєкту після подвійного кліку.
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
        scrollPane.setPrefViewportHeight(Math.min(260.0, Math.max(58.0, visibleRows * 50.0)));
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

    /** Звільняє ресурси рядків і від'єднує слухача локалізації. */
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
     * Залишає вибраним лише рядок, на який востаннє натиснув користувач.
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

    /** Звільняє ресурси рядків і скидає вибір перед побудовою нового вмісту списку. */
    private void clearRows() {
        if (selectedRow != null) {
            selectedRow.setSelected(false);
        }
        for (SavedProjectRowView row : projectRows) {
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
