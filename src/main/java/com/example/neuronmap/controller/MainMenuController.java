package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
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

/** Керує навігацією меню, сценаріями незбережених змін, операціями проєктів і сеансом налаштувань. */
public final class MainMenuController {
    private final MainMenuView view;
    private final LocalizationService localization;
    private final Supplier<List<ProjectDescriptor>> projectsSupplier;
    private final Consumer<ProjectDescriptor> openProjectAction;
    private final BiFunction<ProjectDescriptor, String, ProjectDescriptor> renameProjectAction;
    private final Consumer<ProjectDescriptor> deleteProjectAction;
    private final Runnable pauseForMenuAction;
    private final Runnable resumeAfterMenuAction;
    private final SettingsEditSession settingsEditSession;
    private final SettingsEditSession.Setting<SupportedLanguage> languageSetting;
    private final javafx.event.EventHandler<KeyEvent> keyHandler = this::handleKeyPressed;
    private boolean installed;
    private boolean savingPendingProjectChanges;

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
        this.localization = Objects.requireNonNull(localization, "localization");
        this.settingsEditSession = new SettingsEditSession();
        this.languageSetting = settingsEditSession.register(
                localization::language,
                localization::previewLanguage,
                ignored -> localization.persistCurrentLanguage()
        );
        this.projectsSupplier = Objects.requireNonNull(projectsSupplier, "projectsSupplier");
        this.openProjectAction = Objects.requireNonNull(openProjectAction, "openProjectAction");
        this.renameProjectAction = Objects.requireNonNull(renameProjectAction, "renameProjectAction");
        this.deleteProjectAction = Objects.requireNonNull(deleteProjectAction, "deleteProjectAction");
        this.pauseForMenuAction = Objects.requireNonNull(pauseForMenuAction, "pauseForMenuAction");
        this.resumeAfterMenuAction = Objects.requireNonNull(resumeAfterMenuAction, "resumeAfterMenuAction");
        view.setLanguageActions(this::previewLanguage, this::saveSettings, settingsEditSession::hasChanges);
        view.setProjectRenameCommittedAction(this::handleProjectRenameCommitted);
        view.setActions(new MainMenuView.Actions(
                () -> requestLeave(() -> { newProjectAction.run(); closeMenuImmediately(); }),
                () -> requestLeave(() -> view.showLoadPage(projectsSupplier.get())),
                () -> requestLeave(this::openSettingsPage),
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
        if (installed) scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        installed = true;
    }

    public void open() {
        if (view.isMenuVisible()) return;
        pauseForMenuAction.run();
        view.showMainPage();
        view.showMenu();
    }

    public void close() { requestCloseMenu(); }

    public <T> SettingsEditSession.Setting<T> registerSetting(
            Supplier<T> currentValue, Consumer<T> previewAction, Consumer<T> persistAction) {
        return settingsEditSession.register(currentValue, previewAction, persistAction);
    }

    public <T> void previewSetting(SettingsEditSession.Setting<T> setting, T value) {
        beginSettingsEditSessionIfNeeded();
        settingsEditSession.preview(setting, value);
        view.refreshSettingsState();
    }

    public void refreshProjects() {
        Runnable refresh = () -> {
            if (!view.isLoadPage() || hasUnsavedChanges()) return;
            view.showLoadPage(projectsSupplier.get());
        };
        if (Platform.isFxApplicationThread()) refresh.run(); else Platform.runLater(refresh);
    }

    public boolean isOpen() { return view.isMenuVisible(); }

    public void dispose(Scene scene) {
        if (scene != null && installed) scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        installed = false;
        RuntimeException failure = null;
        try { discardUnsavedChanges(); } catch (RuntimeException exception) { failure = exception; }
        try { settingsEditSession.rollback(); } catch (RuntimeException exception) { failure = combineFailures(failure, exception); }
        try { if (view.isMenuVisible()) view.hide(); } catch (RuntimeException exception) { failure = combineFailures(failure, exception); }
        try { view.dispose(); } catch (RuntimeException exception) { failure = combineFailures(failure, exception); }
        if (failure != null) throw failure;
    }

    private void openSettingsPage() {
        beginSettingsEditSessionIfNeeded();
        view.showSettingsPage();
    }

    private void beginSettingsEditSessionIfNeeded() {
        if (settingsEditSession.isActive()) return;
        localization.discardLanguagePreview();
        settingsEditSession.begin();
    }

    private void previewLanguage(SupportedLanguage language) {
        if (language != null) previewSetting(languageSetting, language);
    }

    private void saveSettings() {
        if (!view.isSettingsPage()) return;
        settingsEditSession.commit();
    }

    private void saveUnsavedChanges() {
        boolean wasSaving = savingPendingProjectChanges;
        RuntimeException failure = null;
        savingPendingProjectChanges = true;
        try {
            view.savePendingProjectChanges();
            if (settingsEditSession.isActive() && settingsEditSession.hasChanges()) settingsEditSession.commit();
        } catch (RuntimeException exception) {
            failure = exception;
        } finally {
            savingPendingProjectChanges = wasSaving;
            try { view.refreshSettingsState(); } catch (RuntimeException exception) { failure = combineFailures(failure, exception); }
        }
        if (failure != null) throw failure;
    }

    private void discardUnsavedChanges() {
        RuntimeException failure = null;
        try { view.discardPendingProjectChanges(); } catch (RuntimeException exception) { failure = exception; }
        try { if (settingsEditSession.isActive()) settingsEditSession.rollback(); }
        catch (RuntimeException exception) { failure = combineFailures(failure, exception); }
        try { view.refreshSettingsState(); } catch (RuntimeException exception) { failure = combineFailures(failure, exception); }
        if (failure != null) throw failure;
    }

    private static RuntimeException combineFailures(RuntimeException primary, RuntimeException secondary) {
        if (primary == null) return secondary;
        if (primary != secondary) primary.addSuppressed(secondary);
        return primary;
    }

    private boolean hasUnsavedChanges() {
        return settingsEditSession.hasChanges() || view.hasUnsavedProjectChanges();
    }

    private void handleOpenProject(ProjectDescriptor project) {
        requestLeave(() -> { openProjectAction.accept(project); closeMenuImmediately(); });
    }

    private ProjectDescriptor handleRenameProject(ProjectDescriptor project, String name) {
        return renameProjectAction.apply(project, name);
    }

    private void handleProjectRenameCommitted() {
        if (!savingPendingProjectChanges && view.isLoadPage()) view.showLoadPage(projectsSupplier.get());
    }

    private void handleDeleteProject(ProjectDescriptor project) {
        requestLeave(() -> {
            deleteProjectAction.accept(project);
            if (view.isMenuVisible()) view.showLoadPage(projectsSupplier.get());
        });
    }

    private void handleBack() {
        if (view.isLoadPage() || view.isSettingsPage()) {
            requestLeave(() -> { settingsEditSession.rollback(); view.showMainPage(); });
        } else {
            requestCloseMenu();
        }
    }

    private void requestLeave(Runnable action) {
        if (!hasUnsavedChanges()) { action.run(); return; }
        view.confirmUnsavedChanges(
                () -> {
                    saveUnsavedChanges();
                    if (hasUnsavedChanges()) { requestLeave(action); return; }
                    action.run();
                },
                () -> { discardUnsavedChanges(); action.run(); }
        );
    }

    private void requestClose(Runnable action) {
        requestLeave(() -> {
            settingsEditSession.rollback();
            view.hide();
            resumeAfterMenuAction.run();
            action.run();
        });
    }

    private void requestCloseMenu() {
        if (!view.isMenuVisible()) return;
        requestClose(() -> { });
    }

    private void closeMenuImmediately() {
        if (!view.isMenuVisible()) return;
        settingsEditSession.rollback();
        view.hide();
        resumeAfterMenuAction.run();
    }

    private void handleKeyPressed(KeyEvent event) {
        // Scene-level modality is necessary because the event target can be outside the visible modal overlay.
        if (EditorKeyboardShortcutBoundary.blocksShortcutsFor(event.getTarget())
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
                if (view.isLoadPage() || view.isSettingsPage()) handleBack();
                else requestCloseMenu();
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
}
