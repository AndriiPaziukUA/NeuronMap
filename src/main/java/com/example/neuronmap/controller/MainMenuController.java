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
 * Керує відкриттям і закриттям головного меню, діями над проєктами та обробкою клавіатурних команд меню.
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
     * Створює екземпляр MainMenuController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param view візуальний компонент, яким керує контролер.
     * @param localization служба локалізації інтерфейсу.
     * @param projectsSupplier функція, що надає поточний перелік проєктів.
     * @param newProjectAction callback для створення проєкту.
     * @param openProjectAction callback для відкриття проєкту.
     * @param renameProjectAction callback для перейменування проєкту.
     * @param deleteProjectAction callback для видалення проєкту.
     * @param exitAction callback для завершення роботи застосунку.
     * @param pauseForMenuAction callback для призупинення симуляції на час відкритого меню.
     * @param resumeAfterMenuAction callback для відновлення симуляції після закриття меню.
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
     * Реєструє обробники клавіатури та взаємодії головного меню на сцені.
     *
     * @param scene сцена JavaFX, до якої приєднують компонент.
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
     * Відкриває головне меню та призупиняє дії редактора, які не повинні працювати за відкритого меню.
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
     * Закриває меню й відновлює пов’язаний із ним стан взаємодії.
     */
    public void close() {
        requestCloseMenu();
    }

/**
 * Оновлює список проєктів у меню з актуального каталогу.
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
     * Перевіряє, чи відкрите головне меню.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean isOpen() {
        return view.isMenuVisible();
    }

    /**
     * Прибирає обробники меню зі сцени та звільняє пов’язані ресурси.
     *
     * @param scene сцена JavaFX, до якої приєднують компонент.
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
     * Викликає дію відкриття вибраного проєкту й закриває меню після успішного переходу.
     *
     * @param project опис проєкту, над яким виконується дія.
     */
    private void handleOpenProject(ProjectDescriptor project) {
        requestLeave(() -> {
            openProjectAction.accept(project);
            closeMenuImmediately();
        });
    }

    /**
     * Передає перейменування проєкту сервісу каталогу й повідомляє інтерфейс про оновлені дані.
     *
     * @param project опис проєкту, над яким виконується дія.
     * @param name назва, яку потрібно перевірити або зберегти.
     */
    private ProjectDescriptor handleRenameProject(
            ProjectDescriptor project,
            String name
    ) {
        return renameProjectAction.apply(project, name);
    }

    /**
     * Передає видалення проєкту до прикладної дії та оновлює список проєктів.
     *
     * @param project опис проєкту, над яким виконується дія.
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
     * Повертає меню з поточної сторінки до головної сторінки.
     *
     * @return меню з поточної сторінки до головної сторінки.
     */
    private void handleBack() {
        if (view.isLoadPage() || view.isSettingsPage()) {
            requestLeave(view::showMainPage);
        } else {
            requestCloseMenu();
        }
    }

    /**
     * Запитує вихід із меню або активного сценарію, враховуючи наявність незбережених змін.
     *
     * @param action функція зворотного виклику для відповідної дії.
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
     * Запитує закриття меню або вікна після перевірки незбережених змін.
     *
     * @param action функція зворотного виклику для відповідної дії.
     */
    private void requestClose(Runnable action) {
        requestLeave(() -> {
            view.hide();
            resumeAfterMenuAction.run();
            action.run();
        });
    }

    /**
     * Запитує закриття меню та викликає відповідну дію після підтвердження.
     */
    private void requestCloseMenu() {
        if (!view.isMenuVisible()) {
            return;
        }
        requestClose(() -> { });
    }

    /**
     * Негайно закриває меню без повторного показу діалогу підтвердження.
     */
    private void closeMenuImmediately() {
        if (!view.isMenuVisible()) {
            return;
        }
        view.hide();
        resumeAfterMenuAction.run();
    }

    /**
     * Обробляє подію «key pressed» і передає її до відповідної операції редактора.
     *
     * @param event подія інтерфейсу, яку потрібно обробити.
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
