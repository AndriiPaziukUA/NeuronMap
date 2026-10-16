package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.settings.SettingsEditSession;
import com.example.neuronmap.view.menu.MainMenuView;
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

    /**
     * Створює контролер і пов'язує дії подання з операціями застосунку та єдиною сесією налаштувань.
     *
     * @param view візуальний компонент меню, яким керує контролер.
     * @param localization служба локалізації, яку використовує подання меню.
     * @param projectsSupplier постачальник поточного каталогу збережених проєктів.
     * @param newProjectAction дія створення проєкту.
     * @param openProjectAction дія відкриття проєкту.
     * @param renameProjectAction дія перейменування проєкту.
     * @param deleteProjectAction дія видалення проєкту.
     * @param exitAction дія завершення роботи застосунку.
     * @param pauseForMenuAction дія призупинення роботи редактора на час відкритого меню.
     * @param resumeAfterMenuAction дія відновлення роботи редактора після закриття меню.
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

        view.setLanguageActions(
                this::previewLanguage,
                this::saveSettings,
                settingsEditSession::hasChanges
        );
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
                this::handleBack
        ));
    }

    /** Встановлює обробник клавіатури меню на переданій сцені JavaFX. */
    public void install(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        if (installed) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        installed = true;
    }

    /** Відкриває головне меню та призупиняє редактор, поки меню залишається відкритим. */
    public void open() {
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

    /**
     * Реєструє типізоване налаштування в спільній сесії попереднього перегляду та відкату.
     * Реєструвати налаштування слід до початку його редагування; повторний запис у сховище виконується
     * лише під час підтвердження сесії.
     *
     * @param currentValue постачальник фактично застосованого значення.
     * @param previewAction дія тимчасового застосування значення без постійного збереження.
     * @param persistAction дія постійного збереження переданого значення.
     * @param <T> тип значення налаштування.
     * @return дескриптор зареєстрованого налаштування для викликів {@link #previewSetting}.
     * @throws NullPointerException якщо будь-який callback не задано.
     * @throws IllegalStateException якщо сесія активна.
     */
    public <T> SettingsEditSession.Setting<T> registerSetting(
            Supplier<T> currentValue,
            Consumer<T> previewAction,
            Consumer<T> persistAction
    ) {
        return settingsEditSession.register(currentValue, previewAction, persistAction);
    }

    /**
     * Застосовує тимчасове значення зареєстрованого налаштування в поточній сесії меню.
     * Якщо сесію ще не розпочато, перед знімком початкових значень відновлюється підтверджена мова.
     *
     * @param setting дескриптор налаштування, раніше зареєстрованого цим контролером.
     * @param value значення для попереднього перегляду.
     * @param <T> тип значення налаштування.
     * @throws IllegalStateException якщо налаштування не належить сесії цього контролера.
     */
    public <T> void previewSetting(SettingsEditSession.Setting<T> setting, T value) {
        beginSettingsEditSessionIfNeeded();
        settingsEditSession.preview(setting, value);
        view.refreshSettingsState();
    }

    /**
     * Оновлює видимий список проєктів за найновішими даними каталогу.
     * Оновлення виконується в потоці JavaFX, якщо це необхідно, і пропускається за наявності незбережених змін.
     */
    public void refreshProjects() {
        Runnable refresh = () -> {
            if (!view.isLoadPage() || hasUnsavedChanges()) {
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

    /** Перевіряє, чи відкрито меню. */
    public boolean isOpen() {
        return view.isMenuVisible();
    }

    /**
     * Вилучає обробник клавіатури, відхиляє незбережені зміни й звільняє ресурси подання.
     * Очищення виконується і в разі помилки відкату налаштувань.
     *
     * @param scene сцена, з якої потрібно вилучити обробник; {@code null} дозволено.
     */
    public void dispose(Scene scene) {
        if (scene != null && installed) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        }
        installed = false;
        RuntimeException failure = null;
        try {
            discardUnsavedChanges();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            settingsEditSession.rollback();
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

    /** Починає спільну сесію редагування та відкриває сторінку налаштувань. */
    private void openSettingsPage() {
        beginSettingsEditSessionIfNeeded();
        view.showSettingsPage();
    }

    /** Запускає сесію лише один раз і фіксує підтверджений стан перед першим попереднім переглядом. */
    private void beginSettingsEditSessionIfNeeded() {
        if (settingsEditSession.isActive()) {
            return;
        }
        localization.discardLanguagePreview();
        settingsEditSession.begin();
    }

    /** Передає вибір мови до спільної сесії попереднього перегляду. */
    private void previewLanguage(SupportedLanguage language) {
        if (language != null) {
            previewSetting(languageSetting, language);
        }
    }

    /** Підтверджує та постійно зберігає всі змінені налаштування активної сесії. */
    private void saveSettings() {
        if (!view.isSettingsPage()) {
            return;
        }
        settingsEditSession.commit();
    }

    /**
     * Підтверджує всі незбережені зміни як єдину дію меню.
     * Коміти назв проєктів пригнічують проміжне оновлення списку до завершення пакетної операції.
     */
    private void saveUnsavedChanges() {
        boolean wasSaving = savingPendingProjectChanges;
        RuntimeException failure = null;
        savingPendingProjectChanges = true;
        try {
            view.savePendingProjectChanges();
            if (settingsEditSession.isActive() && settingsEditSession.hasChanges()) {
                settingsEditSession.commit();
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

    /**
     * Відхиляє всі незбережені зміни, намагаючись виконати відкат налаштувань навіть після помилки подання.
     */
    private void discardUnsavedChanges() {
        RuntimeException failure = null;
        try {
            view.discardPendingProjectChanges();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            if (settingsEditSession.isActive()) {
                settingsEditSession.rollback();
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

    /** Додає другу помилку як suppressed, не приховуючи першопричину операції. */
    private static RuntimeException combineFailures(RuntimeException primary, RuntimeException secondary) {
        if (primary == null) {
            return secondary;
        }
        if (primary != secondary) {
            primary.addSuppressed(secondary);
        }
        return primary;
    }

    /** Перевіряє загальний стан незбережених змін, незалежно від поточної сторінки меню. */
    private boolean hasUnsavedChanges() {
        return settingsEditSession.hasChanges() || view.hasUnsavedProjectChanges();
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

    /** Повертається з підсторінки або закриває меню, коли відкрита головна сторінка. */
    private void handleBack() {
        if (view.isLoadPage() || view.isSettingsPage()) {
            requestLeave(() -> {
                settingsEditSession.rollback();
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
                }
        );
    }

    /** Закриває меню після розв'язання незбережених змін і викликає задану дію. */
    private void requestClose(Runnable action) {
        requestLeave(() -> {
            settingsEditSession.rollback();
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
        settingsEditSession.rollback();
        view.hide();
        resumeAfterMenuAction.run();
    }

    /**
     * Обробляє Escape відповідно до поточної сторінки меню та блокує редакторські скорочення в меню.
     *
     * @param event подія натискання клавіші JavaFX.
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
