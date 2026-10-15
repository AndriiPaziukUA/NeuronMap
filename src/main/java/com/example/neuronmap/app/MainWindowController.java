package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.window.WindowState;
import com.example.neuronmap.persistence.window.WindowStateStore;
import com.example.neuronmap.view.window.WindowChromeView;
import com.example.neuronmap.windowing.JavaFxWindowPlatformAdapter;
import com.example.neuronmap.windowing.WindowPlatformAdapter;
import com.example.neuronmap.windowing.WindowPlatformAdapterFactory;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Керує головним JavaFX-вікном, відновленням його геометрії та узгодженим життєвим циклом.
 * Контролер володіє активним платформним адаптером і поданням заголовка та дозволяє показати вікно лише один раз.
 */
public final class MainWindowController {

    private static final Logger LOGGER = Logger.getLogger(MainWindowController.class.getName());

    private final Stage stage;
    private final WindowStateStore windowStateStore;
    private final LocalizationService localization;
    private final Supplier<Optional<WindowPlatformAdapter>> windowPlatformAdapterFactory;
    private final Runnable showWindowAction;
    private final EventHandler<WindowEvent> windowHiddenHandler = event -> disposeWindowResources();
    private WindowLifecycleCoordinator lifecycleCoordinator;
    private WindowChromeView windowChromeView;
    private WindowPlatformAdapter windowPlatformAdapter;
    private boolean configured;
    private boolean savedStateRestored;
    private boolean showAttempted;
    private boolean windowPlatformAdapterInstalled;

    /**
     * Створює контролер вікна зі спільним сховищем геометрії та активною службою локалізації застосунку.
     *
     * @param stage головне JavaFX-вікно.
     * @param windowStateStore сховище геометрії вікна.
     * @param localization служба поточної локалі та перекладів.
     * @throws NullPointerException якщо будь-яку залежність не задано.
     */
    public MainWindowController(
            Stage stage,
            WindowStateStore windowStateStore,
            LocalizationService localization
    ) {
        this(stage, windowStateStore, localization,
                WindowPlatformAdapterFactory::createForCurrentOperatingSystem);
    }

    /**
     * Створює контролер із заданим постачальником платформного адаптера для детермінованої перевірки життєвого циклу.
     * Цей конструктор доступний лише всередині пакета й не змінює публічний контракт запуску застосунку.
     *
     * @param stage головне JavaFX-вікно.
     * @param windowStateStore сховище геометрії вікна.
     * @param localization служба поточної локалі та перекладів.
     * @param windowPlatformAdapterFactory постачальник адаптера для поточної платформи.
     * @throws NullPointerException якщо будь-яку залежність не задано.
     */
    MainWindowController(
            Stage stage,
            WindowStateStore windowStateStore,
            LocalizationService localization,
            Supplier<Optional<WindowPlatformAdapter>> windowPlatformAdapterFactory
    ) {
        this(stage, windowStateStore, localization, windowPlatformAdapterFactory,
                Objects.requireNonNull(stage, "stage")::show);
    }

    /**
     * Створює контролер із можливістю підмінити побічний ефект показу вікна в тестах.
     * У робочому застосунку передається {@link Stage#show()}, а тести можуть перевіряти життєвий цикл
     * без створення нативних вікон операційної системи.
     *
     * @param stage головне JavaFX-вікно.
     * @param windowStateStore сховище геометрії вікна.
     * @param localization служба поточної локалі та перекладів.
     * @param windowPlatformAdapterFactory постачальник платформного адаптера.
     * @param showWindowAction дія показу вікна; у тестах може бути порожньою.
     * @throws NullPointerException якщо будь-яку залежність або дію не задано.
     */
    MainWindowController(
            Stage stage,
            WindowStateStore windowStateStore,
            LocalizationService localization,
            Supplier<Optional<WindowPlatformAdapter>> windowPlatformAdapterFactory,
            Runnable showWindowAction
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.windowStateStore = Objects.requireNonNull(windowStateStore, "windowStateStore");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.windowPlatformAdapterFactory = Objects.requireNonNull(
                windowPlatformAdapterFactory, "windowPlatformAdapterFactory");
        this.showWindowAction = Objects.requireNonNull(showWindowAction, "showWindowAction");
    }

    /**
     * Налаштовує головне вікно, за потреби встановлює власний заголовок, відновлює геометрію
     * та під'єднує єдиний обробник завершення. Викликати метод слід один раз до показу вікна.
     * Залежності редактора не зберігаються: контролер отримує лише дію його завершення.
     *
     * @param windowConfig перевірені налаштування головного вікна з конфігурації застосунку.
     * @param scene сцена редактора.
     * @param shutdownEditor дія завершення редактора та звільнення його ресурсів.
     * @throws NullPointerException якщо конфігурацію, сцену або дію завершення не задано.
     * @throws IllegalStateException якщо контролер уже налаштовано.
     */
    public void configure(
            AppConfig.Window windowConfig,
            Scene scene,
            Runnable shutdownEditor
    ) {
        if (configured) {
            throw new IllegalStateException("main window is already configured");
        }
        Objects.requireNonNull(windowConfig, "windowConfig");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(shutdownEditor, "shutdownEditor");

        configureWindowChrome(scene);
        stage.setTitle(windowConfig.title());
        stage.setScene(scene);
        stage.setWidth(windowConfig.defaultWidth());
        stage.setHeight(windowConfig.defaultHeight());

        WindowState savedState = windowStateStore.load().orElse(null);
        savedStateRestored = savedState != null;
        if (savedState != null) {
            stage.setWidth(savedState.width());
            stage.setHeight(savedState.height());
            stage.setX(savedState.x());
            stage.setY(savedState.y());
        }

        lifecycleCoordinator = new WindowLifecycleCoordinator(
                () -> windowStateStore.save(currentWindowState()),
                shutdownEditor
        );
        stage.setOnCloseRequest(event -> shutdown());
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, windowHiddenHandler);
        configured = true;
    }

    /**
     * Показує налаштоване вікно та під'єднує активний платформний адаптер до показаного вікна.
     * Центрування виконується лише за відсутності збереженої геометрії. Контролер одноразовий:
     * навіть після невдалого першого показу повторний виклик заборонений, щоб не перевстановлювати обробники.
     *
     * @throws IllegalStateException якщо вікно ще не налаштовано або показ уже було розпочато.
     */
    public void show() {
        requireConfigured();
        if (showAttempted) {
            throw new IllegalStateException("main window can only be shown once");
        }
        showAttempted = true;

        try {
            showWindowAction.run();
            installWindowPlatformAdapter();
            if (!savedStateRestored) {
                stage.centerOnScreen();
            }
        } catch (RuntimeException | Error exception) {
            disposeWindowResources();
            if (stage.isShowing()) {
                stage.hide();
            }
            throw exception;
        }
    }

    /**
     * Запитує звичайне закриття вікна; збереження, завершення редактора та очищення виконує спільний життєвий цикл.
     * Цей самий метод використовується власною кнопкою заголовка й меню виходу.
     *
     * @throws IllegalStateException якщо вікно ще не налаштовано.
     */
    public void close() {
        requireConfigured();
        try {
            shutdown();
        } finally {
            stage.close();
        }
    }

    /**
     * Згортає головне вікно через API Stage, щоб стан залишався узгодженим із системою.
     *
     * @throws IllegalStateException якщо вікно ще не налаштовано.
     */
    public void minimize() {
        requireConfigured();
        stage.setIconified(true);
    }

    /**
     * Перемикає вікно між звичайним і максимізованим станами через API Stage.
     *
     * @throws IllegalStateException якщо вікно ще не налаштовано.
     */
    public void toggleMaximized() {
        requireConfigured();
        stage.setMaximized(!stage.isMaximized());
    }

    /**
     * Повертає, чи перебуває вікно в максимізованому стані.
     *
     * @return {@code true}, якщо вікно максимізоване; інакше {@code false}.
     * @throws IllegalStateException якщо вікно ще не налаштовано.
     */
    public boolean isMaximized() {
        requireConfigured();
        return stage.isMaximized();
    }

    /**
     * Завершує збереження та роботу редактора один раз, а під час кожного виклику повторює очищення ресурсів вікна.
     * Якщо адаптер ще не підтвердив звільнення ресурсів, контролер зберігає його для повторної спроби.
     * Метод також викликається з {@code Application.stop()} як резервний шлях завершення.
     */
    public void shutdown() {
        try {
            if (lifecycleCoordinator != null) {
                lifecycleCoordinator.shutdown();
            }
        } finally {
            disposeWindowResources();
        }
    }

    /**
     * Зчитує поточну геометрію вікна для збереження перед завершенням редактора.
     *
     * @return геометрія вікна в координатах і логічних пікселях JavaFX.
     */
    private WindowState currentWindowState() {
        return new WindowState(stage.getWidth(), stage.getHeight(), stage.getX(), stage.getY());
    }

    /**
     * Застосовує власний заголовок лише там, де є окремий платформний адаптер.
     * Стандартне оформлення непідтримуваних ОС не змінюється.
     *
     * @param scene сцена, корінь якої може бути обгорнуто власним заголовком.
     */
    private void configureWindowChrome(Scene scene) {
        Optional<WindowPlatformAdapter> adapter = Objects.requireNonNull(
                windowPlatformAdapterFactory.get(), "window platform adapter factory result");
        if (adapter.isEmpty()) {
            return;
        }
        stage.initStyle(StageStyle.UNDECORATED);
        windowPlatformAdapter = adapter.orElseThrow();
        windowChromeView = new WindowChromeView(
                stage, localization, this::close, this::minimize, this::toggleMaximized);
        windowChromeView.installInto(scene);
    }

    /**
     * Встановлює активний адаптер один раз; за помилки нативного адаптера зберігає та встановлює JavaFX fallback.
     * Якщо встановлення резервної реалізації також завершується помилкою, звільняє її й передає помилку виклику.
     */
    private void installWindowPlatformAdapter() {
        if (windowPlatformAdapter == null || windowChromeView == null || windowPlatformAdapterInstalled) {
            return;
        }

        WindowPlatformAdapter primaryAdapter = windowPlatformAdapter;
        Throwable primaryFailure;
        try {
            primaryAdapter.install(stage, windowChromeView);
            windowPlatformAdapterInstalled = true;
            return;
        } catch (RuntimeException | LinkageError installationFailure) {
            primaryFailure = installationFailure;
            disposeAfterInstallationFailure(primaryAdapter, installationFailure);
            if (primaryAdapter instanceof JavaFxWindowPlatformAdapter) {
                windowPlatformAdapter = null;
                throw installationFailure;
            }
            LOGGER.log(Level.WARNING,
                    "Нативне оформлення вікна не вдалося під'єднати; використовується резервне керування JavaFX",
                    installationFailure);
        }

        WindowPlatformAdapter fallbackAdapter = new JavaFxWindowPlatformAdapter();
        windowPlatformAdapter = fallbackAdapter;
        try {
            fallbackAdapter.install(stage, windowChromeView);
            windowPlatformAdapterInstalled = true;
        } catch (RuntimeException | LinkageError fallbackFailure) {
            disposeAfterInstallationFailure(fallbackAdapter, fallbackFailure);
            if (fallbackFailure != primaryFailure) {
                fallbackFailure.addSuppressed(primaryFailure);
            }
            windowPlatformAdapter = null;
            throw fallbackFailure;
        }
    }

    /**
     * Звільняє адаптер після невдалого встановлення, не маскуючи першопричину помилкою очищення.
     *
     * @param adapter адаптер, чиє встановлення не завершилося успішно.
     * @param installationFailure первинна помилка встановлення, до якої додаються проблеми очищення.
     */
    private void disposeAfterInstallationFailure(WindowPlatformAdapter adapter, Throwable installationFailure) {
        try {
            adapter.dispose();
        } catch (RuntimeException | LinkageError cleanupFailure) {
            if (installationFailure != cleanupFailure) {
                installationFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    /**
     * Очищує подання та звільняє адаптер. Якщо адаптер повідомляє про незавершене очищення,
     * контролер зберігає його і власний обробник приховування вікна для повторної спроби.
     * Windows-адаптер додатково завершує життєвий цикл callback після підтвердженого WM_NCDESTROY.
     */
    private void disposeWindowResources() {
        WindowPlatformAdapter activeAdapter = windowPlatformAdapter;
        WindowChromeView activeChromeView = windowChromeView;
        boolean adapterDisposed = activeAdapter == null;
        Throwable cleanupFailure = null;

        if (activeAdapter != null) {
            try {
                adapterDisposed = activeAdapter.disposeAndReportCompletion();
            } catch (RuntimeException | LinkageError exception) {
                cleanupFailure = exception;
            }
        }

        if (activeChromeView != null) {
            try {
                activeChromeView.dispose();
            } catch (RuntimeException | LinkageError exception) {
                if (cleanupFailure == null) {
                    cleanupFailure = exception;
                } else if (cleanupFailure != exception) {
                    cleanupFailure.addSuppressed(exception);
                }
            }
        }

        windowChromeView = null;
        windowPlatformAdapterInstalled = false;
        if (adapterDisposed) {
            if (windowPlatformAdapter == activeAdapter) {
                windowPlatformAdapter = null;
            }
            stage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, windowHiddenHandler);
        } else {
            LOGGER.warning("Очищення адаптера вікна не завершено; збережено посилання для повторної спроби");
        }
        if (cleanupFailure != null) {
            LOGGER.log(Level.WARNING, "Не вдалося повністю очистити ресурси заголовка головного вікна", cleanupFailure);
        }
    }

    /**
     * Перевіряє, що перед використанням команд керування вікном виконано одноразове налаштування.
     *
     * @throws IllegalStateException якщо {@link #configure(AppConfig.Window, Scene, Runnable)} ще не викликано.
     */
    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException("main window is not configured");
        }
    }
}
