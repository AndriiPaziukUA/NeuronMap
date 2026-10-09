package com.example.neuronmap.controller;

import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.MainMenuView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Керує сторінками головного меню, переходами між ними та діями зі списком проєктів.
 */
public final class MainMenuController {

    private final MainMenuView view;
    private final Supplier<List<ProjectDescriptor>> projectsSupplier;
    private final Runnable newProjectAction;
    private final Consumer<ProjectDescriptor> openProjectAction;
    private final BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameProjectAction;
    private final Consumer<ProjectDescriptor> deleteProjectAction;
    private final Runnable exitAction;
    private final Runnable pauseForMenuAction;
    private final Runnable resumeAfterMenuAction;

    private final javafx.event.EventHandler<KeyEvent> keyHandler = this::handleKeyPressed;
    private boolean installed;

    /**
     * Повертає результат операції «меню».
     *
     * @param view значення, що визначає відображення для цієї операції.
     *
     * @param localization значення, що визначає локалізація для цієї операції.
     *
     * @param projectsSupplier значення, що визначає проєкти для цієї операції.
     *
     * @param newProjectAction значення, що визначає новий проєкт для цієї операції.
     *
     * @param openProjectAction значення, що визначає відкрити проєкт для цієї операції.
     *
     * @param renameProjectAction значення, що визначає проєкт для цієї операції.
     *
     * @param deleteProjectAction значення, що визначає видалити проєкт для цієї операції.
     *
     * @param exitAction значення, що визначає відповідну операцію для цієї операції.
     *
     * @param pauseForMenuAction значення, що визначає для меню для цієї операції.
     *
     * @param resumeAfterMenuAction значення, що визначає після меню для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public MainMenuController(
            MainMenuView view,
            LocalizationService localization,
            Supplier<List<ProjectDescriptor>> projectsSupplier,
            Runnable newProjectAction,
            Consumer<ProjectDescriptor> openProjectAction,
            BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameProjectAction,
            Consumer<ProjectDescriptor> deleteProjectAction,
            Runnable exitAction,
            Runnable pauseForMenuAction,
            Runnable resumeAfterMenuAction
    ) {
        this.view = Objects.requireNonNull(view, "view");
        Objects.requireNonNull(localization, "localization");
        this.projectsSupplier = Objects.requireNonNull(projectsSupplier, "projectsSupplier");
        this.newProjectAction = Objects.requireNonNull(newProjectAction, "newProjectAction");
        this.openProjectAction = Objects.requireNonNull(openProjectAction, "openProjectAction");
        this.renameProjectAction = Objects.requireNonNull(renameProjectAction, "renameProjectAction");
        this.deleteProjectAction = Objects.requireNonNull(deleteProjectAction, "deleteProjectAction");
        this.exitAction = Objects.requireNonNull(exitAction, "exitAction");
        this.pauseForMenuAction = Objects.requireNonNull(pauseForMenuAction, "pauseForMenuAction");
        this.resumeAfterMenuAction = Objects.requireNonNull(resumeAfterMenuAction, "resumeAfterMenuAction");

        view.setActions(new MainMenuView.Actions(
                () -> requestLeave(() -> {
                    newProjectAction.run();
                    closeMenuImmediately();
                }),
                () -> requestLeave(() -> view.showLoadPage(projectsSupplier.get())),
                () -> requestLeave(view::showSettingsPage),
                () -> requestClose(exitAction),
                this::requestCloseMenu,
                this::handleOpenProject,
                this::handleRenameProject,
                this::handleDeleteProject,
                this::handleBack
        ));
    }

    /**
     * Виконує операцію «відповідну операцію».
     *
     * @param scene значення, що визначає сцена для цієї операції.
     */
    public void install(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        if (installed) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        installed = true;
    }

    /**
     * Виконує операцію «відкрити».
     */
    public void open() {
        if (view.isMenuVisible()) {
            return;
        }
        pauseForMenuAction.run();
        view.showMainPage();
        view.showMenu();
    }

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    public void close() {
        requestCloseMenu();
    }

/**
 * Обробляє «проєкти».
 */
public void refreshProjects() {
        Runnable refresh = () -> {
            if (!view.isLoadPage() || view.hasUnsavedChanges()) {
                return;
            }
            view.showLoadPage(projectsSupplier.get());
        };

        if (Platform.isFxApplicationThread()) {
            refresh.run();
        } else {
            Platform.runLater(refresh);
        }
    }

    /**
     * Перевіряє, чи виконується умова «відкрити».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isOpen() {
        return view.isMenuVisible();
    }

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     *
     * @param scene значення, що визначає сцена для цієї операції.
     */
    public void dispose(Scene scene) {
        if (scene != null && installed) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        }
        installed = false;
        view.discardUnsavedChanges();
        if (view.isMenuVisible()) {
            view.hide();
        }
        view.dispose();
    }

    /**
     * Обробляє «відкрити проєкт».
     *
     * @param project опис проєкту.
     */
    private void handleOpenProject(ProjectDescriptor project) {
        requestLeave(() -> {
            openProjectAction.accept(project);
            closeMenuImmediately();
        });
    }

    /**
     * Обробляє «проєкт».
     *
     * @param project опис проєкту.
     *
     * @param name назва або текстове імʼя обʼєкта.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private ProjectDescriptor handleRenameProject(
            ProjectDescriptor project,
            String name
    ) {
        return renameProjectAction.apply(project, name);
    }

    /**
     * Обробляє «видалити проєкт».
     *
     * @param project опис проєкту.
     */
    private void handleDeleteProject(ProjectDescriptor project) {
        requestLeave(() -> {
            deleteProjectAction.accept(project);
            if (view.isMenuVisible()) {
                view.showLoadPage(projectsSupplier.get());
            }
        });
    }

    /**
     * Обробляє «відповідну операцію».
     */
    private void handleBack() {
        if (view.isLoadPage() || view.isSettingsPage()) {
            requestLeave(view::showMainPage);
        } else {
            requestCloseMenu();
        }
    }

    /**
     * Виконує операцію «відповідну операцію».
     *
     * @param action дія, яку потрібно виконати.
     */
    private void requestLeave(Runnable action) {
        if (!view.hasUnsavedChanges()) {
            action.run();
            return;
        }

        view.confirmUnsavedChanges(
                () -> {
                    view.saveUnsavedChanges();
                    action.run();
                },
                () -> {
                    view.discardUnsavedChanges();
                    action.run();
                }
        );
    }

    /**
     * Виконує операцію «закриття».
     *
     * @param action дія, яку потрібно виконати.
     */
    private void requestClose(Runnable action) {
        requestLeave(() -> {
            view.hide();
            resumeAfterMenuAction.run();
            action.run();
        });
    }

    /**
     * Виконує операцію «закриття меню».
     */
    private void requestCloseMenu() {
        if (!view.isMenuVisible()) {
            return;
        }
        requestClose(() -> { });
    }

    /**
     * Завершує або скасовує дію, повʼязану з «меню».
     */
    private void closeMenuImmediately() {
        if (!view.isMenuVisible()) {
            return;
        }
        view.hide();
        resumeAfterMenuAction.run();
    }

    /**
     * Обробляє «ключ».
     *
     * @param event подія інтерфейсу.
     */
    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            if (view.isConfirmingUnsavedChanges()) {
                view.discardUnsavedChanges();
                requestCloseMenu();
                event.consume();
                return;
            }

            if (isOpen()) {
                requestCloseMenu();
            } else {
                open();
            }
            event.consume();
            return;
        }

        if (isOpen()
                && (event.getCode() == KeyCode.DELETE
                || (event.isControlDown()
                && (event.getCode() == KeyCode.Z
                || event.getCode() == KeyCode.Y)))) {
            event.consume();
        }
    }
}
