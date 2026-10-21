package com.example.neuronmap.controller;

import com.example.neuronmap.coordinator.MainMenuSettingsCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.settings.SettingsEditSession;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.support.EditorKeyboardShortcutBoundary;
import com.example.neuronmap.view.support.SceneKeyboardModality;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Керує навігацією меню, сценаріями незбережених змін і діями над проєктами. */
public final class MainMenuController {

    private final MainMenuView view;
    private final Supplier<List<ProjectDescriptor>> projectsSupplier;
    private final Consumer<ProjectDescriptor> openProjectAction;
    private final BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameProjectAction;
    private final Consumer<ProjectDescriptor> deleteProjectAction;
    private final Runnable pauseForMenuAction;
    private final Runnable resumeAfterMenuAction;
    private final MainMenuSettingsCoordinator settingsCoordinator;
    private final javafx.event.EventHandler<KeyEvent> keyHandler = this::handleKeyPressed;

    private Scene installedScene;
    private boolean savingPendingProjectChanges;
    private boolean disposed;

    /** Створює контролер і пов'язує подання з діями застосунку та координатором налаштувань. */
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
        this.settingsCoordinator = new MainMenuSettingsCoordinator(
                Objects.requireNonNull(localization, "localization"));
        this.projectsSupplier = Objects.requireNonNull(projectsSupplier, "projectsSupplier");
        this.openProjectAction = Objects.requireNonNull(openProjectAction, "openProjectAction");
        this.renameProjectAction = Objects.requireNonNull(renameProjectAction, "renameProjectAction");
        this.deleteProjectAction = Objects.requireNonNull(deleteProjectAction, "deleteProjectAction");
        this.pauseForMenuAction = Objects.requireNonNull(pauseForMenuAction, "pauseForMenuAction");
        this.resumeAfterMenuAction = Objects.requireNonNull(resumeAfterMenuAction, "resumeAfterMenuAction");

        view.setLanguageActions(settingsCoordinator::previewLanguage,
                this::saveSettings, settingsCoordinator::hasChanges);
        view.setProjectRenameCommittedAction(this::handleProjectRenameCommitted);
        view.setActions(new MainMenuView.Actions(
                () -> requestLeave(() -> {
                    newProjectAction.run();
                    closeMenuImmediately();
                }),
                () -> requestLeave(() -> view.showLoadPage(projectsSupplier.get())),
                () -> requestLeave(this::openSettingsPage),
                () -> requestClose(exitAction),
                this::requestCloseMenu,
                this::handleOpenProject,
                this::handleRenameProject,
                this::handleDeleteProject,
                this::handleBack));
    }

    /** Під'єднує клавіатурний фільтр до сцени; при перенесенні меню від'єднує попередню сцену. */
    public void install(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        ensureNotDisposed();
        if (scene == installedScene) {
            return;
        }
        detachKeyboardHandler(installedScene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        installedScene = scene;
    }

    /** Відкриває головне меню та призупиняє редактор, доки меню залишається відкритим. */
    public void open() {
        ensureNotDisposed();
        if (view.isMenuVisible()) {
            return;
        }
        pauseForMenuAction.run();
        view.showMainPage();
        view.showMenu();
    }

    /** Закриває меню після розв'язання всіх незбережених змін. */
    public void close() {
        requestCloseMenu();
    }

    /** Реєструє типізоване налаштування в координаторі сеансу попереднього перегляду. */
    public <T> SettingsEditSession.Setting<T> registerSetting(
            Supplier<T> currentValue,
            Consumer<T> previewAction,
            Consumer<T> persistAction
    ) {
        ensureNotDisposed();
        return settingsCoordinator.register(currentValue, previewAction, persistAction);
    }

    /** Застосовує тимчасове значення зареєстрованого налаштування та оновлює стан подання. */
    public <T> void previewSetting(SettingsEditSession.Setting<T> setting, T value) {
        ensureNotDisposed();
        settingsCoordinator.preview(setting, value);
        view.refreshSettingsState();
    }

    /** Оновлює видимий список проєктів за найновішими даними каталогу. */
    public void refreshProjects() {
        if (disposed) {
            return;
        }
        Runnable refresh = () -> {
            if (disposed || !view.isLoadPage() || hasUnsavedChanges()) {
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

    /** Перевіряє, чи відображається головне меню. */
    public boolean isOpen() {
        return view.isMenuVisible();
    }

    /** Звільняє клавіатурну реєстрацію та ресурси подання; метод є ідемпотентним. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        detachKeyboardHandler(installedScene);
        installedScene = null;
        RuntimeException failure = null;
        try {
            discardUnsavedChanges();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            settingsCoordinator.rollback();
        } catch (RuntimeException exception) {
            failure = combineFailures(failure, exception);
        }
        try {
            if (view.isMenuVisible()) {
                view.hide();
            }
        } catch (RuntimeException exception) {
            failure = combineFailures(failure, exception);
        }
        try {
            view.dispose();
        } catch (RuntimeException exception) {
            failure = combineFailures(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Зберігає сумісність із наявними викликами й ігнорує передану сцену. */
    public void dispose(Scene ignoredScene) {
        dispose();
    }

    /** Починає сеанс редагування та відкриває сторінку налаштувань. */
    private void openSettingsPage() {
        settingsCoordinator.beginIfNeeded();
        view.showSettingsPage();
    }

    /** Підтверджує й постійно зберігає налаштування, лише коли відкрита їхня сторінка. */
    private void saveSettings() {
        if (!view.isSettingsPage()) {
            return;
        }
        settingsCoordinator.commit();
    }

    /** Підтверджує всі незбережені зміни як одну дію меню. */
    private void saveUnsavedChanges() {
        boolean wasSaving = savingPendingProjectChanges;
        RuntimeException failure = null;
        savingPendingProjectChanges = true;
        try {
            view.savePendingProjectChanges();
            if (settingsCoordinator.isActive() && settingsCoordinator.hasChanges()) {
                settingsCoordinator.commit();
            }
        } catch (RuntimeException exception) {
            failure = exception;
        } finally {
            savingPendingProjectChanges = wasSaving;
            try {
                view.refreshSettingsState();
            } catch (RuntimeException exception) {
                failure = combineFailures(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Відхиляє незбережені зміни, намагаючись завершити відкат навіть після помилки подання. */
    private void discardUnsavedChanges() {
        RuntimeException failure = null;
        try {
            view.discardPendingProjectChanges();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            if (settingsCoordinator.isActive()) {
                settingsCoordinator.rollback();
            }
        } catch (RuntimeException exception) {
            failure = combineFailures(failure, exception);
        }
        try {
            view.refreshSettingsState();
        } catch (RuntimeException exception) {
            failure = combineFailures(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Додає вторинну помилку до основної, не приховуючи першопричину операції. */
    private static RuntimeException combineFailures(RuntimeException primary, RuntimeException secondary) {
        if (primary == null) {
            return secondary;
        }
        if (primary != secondary) {
            primary.addSuppressed(secondary);
        }
        return primary;
    }

    /** Перевіряє загальний стан незбережених змін незалежно від поточної сторінки меню. */
    private boolean hasUnsavedChanges() {
        return settingsCoordinator.hasChanges() || view.hasUnsavedProjectChanges();
    }

    /** Відкриває вибраний проєкт і після цього закриває меню. */
    private void handleOpenProject(ProjectDescriptor project) {
        requestLeave(() -> {
            openProjectAction.accept(project);
            closeMenuImmediately();
        });
    }

    /** Передає перейменування проєкту до відповідної операції каталогу. */
    private ProjectDescriptor handleRenameProject(ProjectDescriptor project, String name) {
        return renameProjectAction.apply(project, name);
    }

    /** Оновлює список після перейменування, якщо воно не є частиною пакетного збереження. */
    private void handleProjectRenameCommitted() {
        if (!savingPendingProjectChanges && view.isLoadPage()) {
            view.showLoadPage(projectsSupplier.get());
        }
    }

    /** Видаляє проєкт і оновлює список, якщо меню лишається відкритим. */
    private void handleDeleteProject(ProjectDescriptor project) {
        requestLeave(() -> {
            deleteProjectAction.accept(project);
            if (view.isMenuVisible()) {
                view.showLoadPage(projectsSupplier.get());
            }
        });
    }

    /** Повертається з підсторінки або закриває меню, якщо відкрита головна сторінка. */
    private void handleBack() {
        if (view.isLoadPage() || view.isSettingsPage()) {
            requestLeave(() -> {
                settingsCoordinator.rollback();
                view.showMainPage();
            });
        } else {
            requestCloseMenu();
        }
    }

    /** Виконує дію відразу або відкриває підтвердження незбережених змін. */
    private void requestLeave(Runnable action) {
        if (!hasUnsavedChanges()) {
            action.run();
            return;
        }
        view.confirmUnsavedChanges(
                () -> {
                    saveUnsavedChanges();
                    if (hasUnsavedChanges()) {
                        requestLeave(action);
                        return;
                    }
                    action.run();
                },
                () -> {
                    discardUnsavedChanges();
                    action.run();
                });
    }

    /** Закриває меню після розв'язання незбережених змін і викликає задану дію. */
    private void requestClose(Runnable action) {
        requestLeave(() -> {
            settingsCoordinator.rollback();
            view.hide();
            resumeAfterMenuAction.run();
            action.run();
        });
    }

    /** Запитує закриття меню, зберігаючи спільний сценарій обробки незбережених змін. */
    private void requestCloseMenu() {
        if (!view.isMenuVisible()) {
            return;
        }
        requestClose(() -> { });
    }

    /** Негайно закриває меню після успішного переходу між проєктами. */
    private void closeMenuImmediately() {
        if (!view.isMenuVisible()) {
            return;
        }
        settingsCoordinator.rollback();
        view.hide();
        resumeAfterMenuAction.run();
    }

    /** Обробляє Escape й блокує редакторські скорочення в меню. */
    private void handleKeyPressed(KeyEvent event) {
        if (disposed || event.isConsumed()
                || EditorKeyboardShortcutBoundary.blocksShortcutsFor(event.getTarget())
                || SceneKeyboardModality.hasActiveBoundaryFor(event.getTarget())) {
            return;
        }
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
        if (isOpen() && (event.getCode() == KeyCode.DELETE
                || (event.isControlDown() && (event.getCode() == KeyCode.Z || event.getCode() == KeyCode.Y)))) {
            event.consume();
        }
    }

    /** Від'єднує клавіатурний фільтр саме від сцени, якій він належить. */
    private void detachKeyboardHandler(Scene scene) {
        if (scene != null) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        }
    }

    /** Забороняє повторно використовувати контролер після звільнення його ресурсів. */
    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Main menu controller is disposed");
        }
    }
}
