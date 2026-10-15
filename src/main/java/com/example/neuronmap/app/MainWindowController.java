package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.persistence.window.WindowState;
import com.example.neuronmap.persistence.window.WindowStateStore;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * Керує операціями головного вікна JavaFX і надає спільні команди для системного та майбутнього власного оформлення.
 * Відповідає за початкове налаштування вікна, відновлення геометрії та під'єднання узгодженого життєвого циклу.
 */
public final class MainWindowController {

    private final Stage stage;
    private final WindowStateStore windowStateStore;
    private WindowLifecycleCoordinator lifecycleCoordinator;
    private boolean configured;
    private boolean hasRestoredWindowState;

    /**
     * Створює контролер вікна на основі наявного JavaFX-вікна та спільного сховища його геометрії.
     *
     * @param stage головне вікно JavaFX.
     * @param windowStateStore сховище геометрії, яке вже використовується застосунком.
     * @throws NullPointerException якщо вікно або сховище не задано.
     */
    public MainWindowController(Stage stage, WindowStateStore windowStateStore) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.windowStateStore = Objects.requireNonNull(windowStateStore, "windowStateStore");
    }

    /**
     * Налаштовує головне вікно, відновлює збережену геометрію та під'єднує єдиний обробник завершення.
     * Викликати метод слід один раз до показу вікна.
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

        stage.setTitle(windowConfig.title());
        stage.setScene(scene);
        stage.setWidth(windowConfig.defaultWidth());
        stage.setHeight(windowConfig.defaultHeight());

        WindowState savedState = windowStateStore.load().orElse(null);
        hasRestoredWindowState = savedState != null;
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
     * Показує налаштоване вікно та центрує його лише тоді, коли відновлювати попереднє розташування не потрібно.
     *
     * @throws IllegalStateException якщо вікно ще не налаштовано.
     */
    public void show() {
        requireConfigured();
        stage.show();
        if (!hasRestoredWindowState) {
            stage.centerOnScreen();
        }
    }

    /**
     * Запитує звичайне закриття вікна; подальше збереження та очищення виконує спільний обробник життєвого циклу.
     * Саме цей метод передається кнопці виходу, щоб вона використовувала той самий шлях, що й системна кнопка закриття.
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
     * Згортає головне вікно. Метод призначений для системних або майбутніх власних кнопок керування вікном.
     *
     * @throws IllegalStateException якщо вікно ще не налаштовано.
     */
    public void minimize() {
        requireConfigured();
        stage.setIconified(true);
    }

    /**
     * Перемикає вікно між звичайним і максимізованим станами.
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
     * Завершує життєвий цикл, якщо він ще не був запущений обробником закриття.
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
