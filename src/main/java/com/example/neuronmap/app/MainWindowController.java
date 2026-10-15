package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.persistence.window.WindowState;
import com.example.neuronmap.persistence.window.WindowStateStore;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.window.WindowChromeView;
import com.example.neuronmap.windowing.JavaFxWindowPlatformAdapter;
import com.example.neuronmap.windowing.WindowPlatformAdapter;
import com.example.neuronmap.windowing.WindowPlatformAdapterFactory;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Керує операціями головного вікна JavaFX, відновленням його геометрії та узгодженим життєвим циклом.
 * На Windows під'єднує стилізований заголовок через платформний адаптер, зберігаючи один шлях закриття й очищення.
 */
public final class MainWindowController {

    private static final Logger LOGGER = Logger.getLogger(MainWindowController.class.getName());

    private final Stage stage;
    private final WindowStateStore windowStateStore;
    private final LocalizationService localization;
    private WindowLifecycleCoordinator lifecycleCoordinator;
    private WindowChromeView windowChromeView;
    private WindowPlatformAdapter windowPlatformAdapter;
    private boolean configured;
    private boolean savedStateRestored;

    /**
     * Створює контролер вікна на основі наявного JavaFX-вікна та спільного сховища його геометрії.
     *
     * @param stage головне вікно JavaFX.
     * @param windowStateStore сховище геометрії, яке вже використовується застосунком.
     * @throws NullPointerException якщо вікно або сховище не задано.
     */
    public MainWindowController(Stage stage, WindowStateStore windowStateStore) {
        this(stage, windowStateStore, new LocalizationService(Locale.getDefault()));
    }

    /**
     * Створює контролер вікна зі спільним сховищем геометрії та активною службою локалізації застосунку.
     *
     * @param stage головне вікно JavaFX.
     * @param windowStateStore сховище геометрії вікна.
     * @param localization служба поточної локалі та перекладів.
     * @throws NullPointerException якщо будь-яку залежність не задано.
     */
    public MainWindowController(
            Stage stage,
            WindowStateStore windowStateStore,
            LocalizationService localization
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.windowStateStore = Objects.requireNonNull(windowStateStore, "windowStateStore");
        this.localization = Objects.requireNonNull(localization, "localization");
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
        configured = true;
    }

    /**
     * Показує налаштоване вікно та під'єднує адаптер до створеного HWND.
     * Вікно центрується лише тоді, коли відновлювати попереднє розташування не потрібно.
     *
     * @throws IllegalStateException якщо вікно ще не налаштовано.
     */
    public void show() {
        requireConfigured();
        stage.show();
        installWindowPlatformAdapter();
        if (!savedStateRestored) {
            stage.centerOnScreen();
        }
    }

    /**
     * Запитує звичайне закриття вікна; збереження та очищення виконує спільний обробник життєвого циклу.
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
     * Завершує життєвий цикл, якщо його ще не було запущено обробником закриття.
     * Викликається також із {@code Application.stop()} як резервний шлях завершення.
     */
    public void shutdown() {
        if (lifecycleCoordinator != null) {
            lifecycleCoordinator.shutdown();
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
        Optional<WindowPlatformAdapter> adapter = WindowPlatformAdapterFactory.createForCurrentOperatingSystem();
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
     * Під'єднує платформний адаптер після створення нативного вікна й за помилки Windows використовує JavaFX fallback.
     */
    private void installWindowPlatformAdapter() {
        if (windowPlatformAdapter == null || windowChromeView == null) {
            return;
        }
        try {
            windowPlatformAdapter.install(stage, windowChromeView);
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.log(Level.WARNING,
                    "Нативне оформлення вікна не вдалося під'єднати; використовується резервне керування JavaFX",
                    exception);
            windowPlatformAdapter.dispose();
            WindowPlatformAdapter fallbackAdapter = new JavaFxWindowPlatformAdapter();
            fallbackAdapter.install(stage, windowChromeView);
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
