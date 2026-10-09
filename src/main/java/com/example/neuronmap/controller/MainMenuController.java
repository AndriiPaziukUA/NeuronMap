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
 * Controls main-menu navigation, saved-project operations, and keyboard shortcuts for the menu.
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
     * Creates the controller and wires view actions to application operations.
     *
     * @param view visual menu component controlled by this controller.
     * @param localization localization service used by the menu view.
     * @param projectsSupplier supplies the current saved-project catalog.
     * @param newProjectAction callback for creating a project.
     * @param openProjectAction callback for opening a project.
     * @param renameProjectAction callback for renaming a project.
     * @param deleteProjectAction callback for deleting a project.
     * @param exitAction callback for exiting the application.
     * @param pauseForMenuAction callback that pauses editor actions while the menu is open.
     * @param resumeAfterMenuAction callback that resumes editor actions after the menu closes.
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

    /** Installs the menu keyboard handler on the supplied JavaFX scene. */
    public void install(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        if (installed) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        installed = true;
    }

    /** Opens the main menu and pauses editor actions while it remains open. */
    public void open() {
        if (view.isMenuVisible()) {
            return;
        }
        pauseForMenuAction.run();
        view.showMainPage();
        view.showMenu();
    }

    /** Closes the menu after resolving any pending changes. */
    public void close() {
        requestCloseMenu();
    }

    /**
     * Refreshes the visible saved-project list from the latest catalog snapshot.
     * The refresh is scheduled on the JavaFX thread when necessary and skipped when edits are pending.
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
     * Reports whether the menu is currently open.
     *
     * @return true when the menu is visible.
     */
    public boolean isOpen() {
        return view.isMenuVisible();
    }

    /** Removes the scene key handler, discards pending edits, and releases view resources. */
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

    /** Opens the selected project, then closes the menu. */
    private void handleOpenProject(ProjectDescriptor project) {
        requestLeave(() -> {
            openProjectAction.accept(project);
            closeMenuImmediately();
        });
    }

    /** Delegates project renaming to the project catalog operation. */
    private ProjectDescriptor handleRenameProject(ProjectDescriptor project, String name) {
        return renameProjectAction.apply(project, name);
    }

    /** Deletes a project and refreshes the saved-project page when the menu remains open. */
    private void handleDeleteProject(ProjectDescriptor project) {
        requestLeave(() -> {
            deleteProjectAction.accept(project);
            if (view.isMenuVisible()) {
                view.showLoadPage(projectsSupplier.get());
            }
        });
    }

    /**
     * Navigates from a submenu back to the main page, or closes the menu when already at the main page.
     */
    private void handleBack() {
        if (view.isLoadPage() || view.isSettingsPage()) {
            requestLeave(view::showMainPage);
        } else {
            requestCloseMenu();
        }
    }

    /** Runs the requested navigation action immediately when clean, or asks how to resolve pending edits. */
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

    /** Closes the menu after resolving pending edits and then invokes the supplied action. */
    private void requestClose(Runnable action) {
        requestLeave(() -> {
            view.hide();
            resumeAfterMenuAction.run();
            action.run();
        });
    }

    /** Requests menu closure while preserving the unsaved-changes workflow. */
    private void requestCloseMenu() {
        if (!view.isMenuVisible()) {
            return;
        }
        requestClose(() -> { });
    }

    /** Immediately closes the menu after a successful project transition. */
    private void closeMenuImmediately() {
        if (!view.isMenuVisible()) {
            return;
        }
        view.hide();
        resumeAfterMenuAction.run();
    }

    /**
     * Routes Escape according to the currently active menu page. Escape backs out of subpages,
     * closes the main page, and continues opening the main menu when used from the editor.
     *
     * @param event JavaFX key event to handle.
     */
    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            if (view.isConfirmingUnsavedChanges()) {
                view.discardAndContinueUnsavedChanges();
                event.consume();
                return;
            }

            if (isOpen()) {
                if (view.isLoadPage() || view.isSettingsPage()) {
                    handleBack();
                } else {
                    requestCloseMenu();
                }
            } else {
                open();
            }
            event.consume();
            return;
        }

        if (isOpen()
                && (event.getCode() == KeyCode.DELETE
                || (event.isControlDown()
                && (event.getCode() == KeyCode.Z || event.getCode() == KeyCode.Y)))) {
            event.consume();
        }
    }
}
