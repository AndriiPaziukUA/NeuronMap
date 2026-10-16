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

/** Відображає список збережених проєктів і керує життєвим циклом його рядків. */
public final class ProjectListView extends VBox {

    private final LocalizationService localization;
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();
    private final VBox projectRowsBox = new VBox(6.0);
    private final Label emptyProjectsLabel = new Label();
    private List<SavedProjectRowView> projectRows = List.of();
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
     * @param openAction дія відкриття вибраного проєкту.
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
     * @param openAction дія відкриття вибраного проєкту.
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
                            project, localization, openAction, renameAction, deleteAction, renameCommitted))
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

    /** Звільняє ресурси всіх рядків і очищує контейнер, що зберігає їхні вузли. */
    public void dispose() {
        clear();
        localization.removeListener(localizationListener);
    }

    /**
     * Повертає поточний прокручуваний список для перевірок адаптивного компонування.
     *
     * @return поточний список або {@code null}, якщо сторінку ще не побудовано.
     */
    public ScrollPane scrollPaneForTest() {
        return scrollPane;
    }

    /**
     * Повертає незмінний знімок рядків проєктів для тестів користувацьких сценаріїв.
     *
     * @return поточні рядки проєктів.
     */
    public List<SavedProjectRowView> rowsForTest() {
        return List.copyOf(projectRows);
    }

    /** Звільняє підписки рядків на локалізацію та очищує список. */
    private void clearRows() {
        for (SavedProjectRowView row : projectRows) {
            row.dispose();
        }
        projectRows = List.of();
        projectRowsBox.getChildren().clear();
        if (scrollPane != null) {
            scrollPane.setContent(null);
        }
    }
}
