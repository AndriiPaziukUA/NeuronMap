package com.example.neuronmap.app;

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
    private boolean savedStateRestored;

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
     * @param title назва головного вікна.
     * @param scene сцена редактора.
     * @param defaultWidth типова ширина вікна, якщо збережений стан відсутній.
     * @param defaultHeight типова висота вікна, якщо збережений стан відсутній.
     * @param editor фасад редактора, ресурси якого потрібно звільнити під час завершення.
     * @throws NullPointerException якщо назву, сцену або фасад не задано.
     * @throws IllegalArgumentException якщо назва порожня або розміри не є скінченними додатними числами.
     * @throws IllegalStateException якщо контролер уже налаштовано.
     */
    public void configure(
            String title,
            Scene scene,
            double defaultWidth,
            double defaultHeight,
            NeuronMapEditor editor
    ) {
        if (configured) {
            throw new IllegalStateException("main window is already configured");
        }
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(editor, "editor");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (!Double.isFinite(defaultWidth) || defaultWidth <= 0.0
                || !Double.isFinite(defaultHeight) || defaultHeight <= 0.0) {
            throw new IllegalArgumentException("default window dimensions must be finite and positive");
        }

        stage.setTitle(title);
        stage.setScene(scene);
        stage.setWidth(defaultWidth);
        stage.setHeight(defaultHeight);

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
                editor::shutdown
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
        if (!savedStateRestored) {
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
     * @throws IllegalStateException якщо {@link #configure(String, Scene, double, double, NeuronMapEditor)} ще не викликано.
     */
    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException("main window is not configured");
        }
    }
}
