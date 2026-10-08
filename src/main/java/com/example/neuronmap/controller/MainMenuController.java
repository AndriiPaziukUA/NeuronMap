package com.example.neuronmap.controller;

import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.MainMenuView;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Controls the in-window main menu without owning project persistence rules. */
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

    public void install(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        if (installed) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        installed = true;
    }

    public void open() {
        if (view.isMenuVisible()) {
            return;
        }
        pauseForMenuAction.run();
        view.showMainPage();
        view.showMenu();
    }

    public void close() {
        requestCloseMenu();
    }

    public boolean isOpen() {
        return view.isMenuVisible();
    }

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

    private void handleOpenProject(ProjectDescriptor project) {
        requestLeave(() -> {
            openProjectAction.accept(project);
            closeMenuImmediately();
        });
    }

    private ProjectDescriptor handleRenameProject(
            ProjectDescriptor project,
            String name
    ) {
        return renameProjectAction.apply(project, name);
    }

    private void handleDeleteProject(ProjectDescriptor project) {
        requestLeave(() -> {
            deleteProjectAction.accept(project);
            if (view.isMenuVisible()) {
                view.showLoadPage(projectsSupplier.get());
            }
        });
    }

    private void handleBack() {
        if (view.isLoadPage() || view.isSettingsPage()) {
            requestLeave(view::showMainPage);
        } else {
            requestCloseMenu();
        }
    }

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

    private void requestClose(Runnable action) {
        requestLeave(() -> {
            view.hide();
            resumeAfterMenuAction.run();
            action.run();
        });
    }

    private void requestCloseMenu() {
        if (!view.isMenuVisible()) {
            return;
        }
        requestClose(() -> { });
    }

    private void closeMenuImmediately() {
        if (!view.isMenuVisible()) {
            return;
        }
        view.hide();
        resumeAfterMenuAction.run();
    }

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
