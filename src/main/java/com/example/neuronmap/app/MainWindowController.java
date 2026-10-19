package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.window.WindowState;
import com.example.neuronmap.persistence.window.WindowStateStore;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Керує головним JavaFX-вікном, відновленням його геометрії та життєвим циклом редактора.
 * Віконне оформлення делегується єдиному механізму {@link WindowChromeLifecycle} на базі JavaFX.
 */
public final class MainWindowController {

    private final Stage stage;
    private final WindowStateStore windowStateStore;
    private final Runnable showWindowAction;
    private final WindowChromeLifecycle windowChromeLifecycle;
    private WindowLifecycleCoordinator lifecycleCoordinator;
    private boolean configured;
    private boolean savedStateRestored;
    private boolean showAttempted;

    /**
     * Створює контролер головного вікна зі спільним сховищем геометрії та службою локалізації.
     * Під час налаштування буде перевірено підтримку розширеного заголовка поточним JavaFX toolkit.
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
        this(
                stage,
                windowStateStore,
                localization,
                Objects.requireNonNull(stage, "stage")::show,
                () -> Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW)
        );
    }

    /**
     * Створює контролер із керованими показом вікна та перевіркою можливості JavaFX для автоматизованих тестів.
     * У production використовується публічний конструктор, який перевіряє реальну можливість toolkit.
     *
     * @param stage головне JavaFX-вікно.
     * @param windowStateStore сховище геометрії вікна.
     * @param localization спільна служба локалізації застосунку.
     * @param showWindowAction дія показу вікна.
     * @param extendedWindowSupport перевірка підтримки {@link ConditionalFeature#EXTENDED_WINDOW}.
     * @throws NullPointerException якщо будь-яку залежність або дію не задано.
     */
    MainWindowController(
            Stage stage,
            WindowStateStore windowStateStore,
            LocalizationService localization,
            Runnable showWindowAction,
            BooleanSupplier extendedWindowSupport
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.windowStateStore = Objects.requireNonNull(windowStateStore, "windowStateStore");
        this.showWindowAction = Objects.requireNonNull(showWindowAction, "showWindowAction");
        this.windowChromeLifecycle = new WindowChromeLifecycle(
                this.stage,
                Objects.requireNonNull(localization, "localization"),
                Objects.requireNonNull(extendedWindowSupport, "extendedWindowSupport")
        );
    }

    /**
     * Налаштовує вікно, відновлює геометрію та під'єднує спільний шлях завершення редактора.
     * Викликати метод слід один раз до показу Stage.
     *
     * @param windowConfig перевірені налаштування головного вікна.
     * @param scene сцена редактора.
     * @param shutdownEditor дія завершення редактора та звільнення його ресурсів.
     * @throws NullPointerException якщо конфігурацію, сцену або дію завершення не задано.
     * @throws IllegalStateException якщо контролер уже налаштовано.
     * @throws UnsupportedOperationException якщо JavaFX toolkit не підтримує розширене оформлення вікна.
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

        try {
            windowChromeLifecycle.configure(scene);
            stage.setTitle(windowConfig.title());
            stage.setScene(scene);
            stage.setWidth(windowConfig.defaultWidth());
            stage.setHeight(windowConfig.defaultHeight());

            WindowState savedState = windowStateStore.load().orElse(null);
            savedStateRestored = Objects.nonNull(savedState);
            if (Objects.nonNull(savedState)) {
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
        } catch (RuntimeException | Error configurationFailure) {
            windowChromeLifecycle.dispose();
            throw configurationFailure;
        }
    }

    /**
     * Показує налаштоване вікно; центрує його лише за відсутності збереженої геометрії.
     * Повторний показ заборонено, а додаткове встановлення віконного механізму після показу не виконується.
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
            if (!savedStateRestored) {
                stage.centerOnScreen();
            }
        } catch (RuntimeException | Error exception) {
            windowChromeLifecycle.dispose();
            if (stage.isShowing()) {
                stage.hide();
            }
            throw exception;
        }
    }

    /**
     * Виконує штатне закриття вікна після збереження геометрії та завершення редактора.
     * Метод також використовується для команди виходу з меню.
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
     * Згортає головне вікно через API Stage.
     *
     * @throws IllegalStateException якщо вікно ще не налаштовано.
     */
    public void minimize() {
        requireConfigured();
        stage.setIconified(true);
    }

    /**
     * Перемикає головне вікно між звичайним і максимізованим станами через API Stage.
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
     * Один раз завершує редактор, але кожним викликом повторно перевіряє очищення оформлення вікна.
     * Метод також викликається з {@code Application.stop()} як резервний запит завершення застосунку.
     */
    public void shutdown() {
        try {
            if (Objects.nonNull(lifecycleCoordinator)) {
                lifecycleCoordinator.shutdown();
            }
        } finally {
            windowChromeLifecycle.dispose();
        }
    }

    /**
     * Зчитує поточну геометрію вікна для збереження перед завершенням редактора.
     *
     * @return поточні розміри та координати головного вікна.
     */
    private WindowState currentWindowState() {
        return new WindowState(stage.getWidth(), stage.getHeight(), stage.getX(), stage.getY());
    }

    /**
     * Перевіряє, що команди керування викликаються лише після налаштування головного вікна.
     *
     * @throws IllegalStateException якщо метод керування викликано до налаштування вікна.
     */
    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException("main window is not configured");
        }
    }
}
