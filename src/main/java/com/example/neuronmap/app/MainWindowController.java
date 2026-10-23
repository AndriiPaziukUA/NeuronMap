package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.controller.FullScreenShortcutEvent;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.settings.GlobalSettingsFullScreenPreferenceStore;
import com.example.neuronmap.persistence.window.WindowState;
import com.example.neuronmap.persistence.window.WindowStateStore;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Керує головним JavaFX-вікном, його геометрією, повноекранним режимом і життєвим циклом редактора.
 * Віконне оформлення делегується єдиному механізму {@link WindowChromeLifecycle} на базі JavaFX.
 */
public final class MainWindowController {

    private final Stage stage;
    private final WindowStateStore windowStateStore;
    private final Runnable showWindowAction;
    private final WindowChromeLifecycle windowChromeLifecycle;
    private final GlobalSettingsFullScreenPreferenceStore fullScreenPreferenceStore;
    private final EventHandler<KeyEvent> fullScreenShortcutHandler = this::handleFullScreenShortcut;
    private WindowLifecycleCoordinator lifecycleCoordinator;
    private WindowState lastWindowedState;
    private Scene fullScreenShortcutScene;
    private boolean fullScreenEnabled;
    private boolean restoringWindowedGeometry;
    private boolean configured;
    private boolean savedStateRestored;
    private boolean showAttempted;

    /** Створює контролер вікна без окремого сховища повноекранного налаштування, як у тестових сценаріях. */
    public MainWindowController(Stage stage, WindowStateStore windowStateStore, LocalizationService localization) {
        this(stage, windowStateStore, localization,
                Objects.requireNonNull(stage, "stage")::show,
                () -> Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW), null);
    }

    /**
     * Створює production-контролер зі сховищем глобального налаштування повноекранного режиму.
     *
     * @param stage головне JavaFX-вікно.
     * @param windowStateStore сховище геометрії вікна.
     * @param localization спільна служба локалізації.
     * @param fullScreenPreferenceStore сховище стану повноекранного режиму.
     */
    public MainWindowController(Stage stage, WindowStateStore windowStateStore, LocalizationService localization,
                                GlobalSettingsFullScreenPreferenceStore fullScreenPreferenceStore) {
        this(stage, windowStateStore, localization,
                Objects.requireNonNull(stage, "stage")::show,
                () -> Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW),
                Objects.requireNonNull(fullScreenPreferenceStore, "fullScreenPreferenceStore"));
    }

    /** Створює контролер із керованими діями показу й перевірки підтримки для автоматизованих тестів. */
    MainWindowController(Stage stage, WindowStateStore windowStateStore, LocalizationService localization,
                         Runnable showWindowAction, BooleanSupplier extendedWindowSupport) {
        this(stage, windowStateStore, localization, showWindowAction, extendedWindowSupport, null);
    }

    /**
     * Ініціалізує власників вікна та читає збережений стан повноекранного режиму, якщо сховище задано.
     *
     * @param stage головне вікно JavaFX.
     * @param windowStateStore сховище геометрії вікна.
     * @param localization служба локалізації.
     * @param showWindowAction дія показу вікна.
     * @param extendedWindowSupport перевірка підтримки оформлення вікна.
     * @param fullScreenPreferenceStore необов'язкове сховище повноекранного режиму.
     */
    MainWindowController(Stage stage, WindowStateStore windowStateStore, LocalizationService localization,
                         Runnable showWindowAction, BooleanSupplier extendedWindowSupport,
                         GlobalSettingsFullScreenPreferenceStore fullScreenPreferenceStore) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.windowStateStore = Objects.requireNonNull(windowStateStore, "windowStateStore");
        this.showWindowAction = Objects.requireNonNull(showWindowAction, "showWindowAction");
        this.fullScreenPreferenceStore = fullScreenPreferenceStore;
        fullScreenEnabled = fullScreenPreferenceStore != null && fullScreenPreferenceStore.loadFullScreenEnabled();
        windowChromeLifecycle = new WindowChromeLifecycle(stage,
                Objects.requireNonNull(localization, "localization"),
                Objects.requireNonNull(extendedWindowSupport, "extendedWindowSupport"));
    }

    /**
     * Налаштовує Stage, відновлює геометрію, застосовує режим відображення та під'єднує клавіатурні команди.
     *
     * @param windowConfig перевірені налаштування головного вікна.
     * @param scene сцена редактора.
     * @param shutdownEditor дія завершення редактора та звільнення його ресурсів.
     * @throws NullPointerException якщо конфігурацію, сцену або дію завершення не задано.
     * @throws IllegalStateException якщо контролер уже налаштовано.
     * @throws UnsupportedOperationException якщо JavaFX toolkit не підтримує розширене оформлення вікна.
     */
    public void configure(AppConfig.Window windowConfig, Scene scene, Runnable shutdownEditor) {
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
            // ESC має залишатися доступним меню, щоб перемикач і стан Stage не розходилися.
            stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);

            WindowState savedState = windowStateStore.load().orElse(null);
            savedStateRestored = savedState != null;
            if (savedState != null) {
                stage.setWidth(savedState.width());
                stage.setHeight(savedState.height());
                stage.setX(savedState.x());
                stage.setY(savedState.y());
                lastWindowedState = savedState;
            } else {
                lastWindowedState = centeredWindowState(stage.getWidth(), stage.getHeight());
            }
            stage.setFullScreen(fullScreenEnabled);
            lifecycleCoordinator = new WindowLifecycleCoordinator(
                    () -> windowStateStore.save(currentWindowState()), shutdownEditor);
            stage.setOnCloseRequest(event -> shutdown());
            fullScreenShortcutScene = scene;
            scene.addEventFilter(KeyEvent.KEY_PRESSED, fullScreenShortcutHandler);
            configured = true;
        } catch (RuntimeException | Error configurationFailure) {
            detachFullScreenShortcut();
            windowChromeLifecycle.dispose();
            throw configurationFailure;
        }
    }

    /** Показує вікно лише один раз і центрує його, якщо раніше не було збережено геометрію. */
    public void show() {
        requireConfigured();
        if (showAttempted) {
            throw new IllegalStateException("main window can only be shown once");
        }
        showAttempted = true;
        try {
            showWindowAction.run();
            if (!savedStateRestored && !fullScreenEnabled) {
                stage.centerOnScreen();
                lastWindowedState = currentWindowState();
            }
        } catch (RuntimeException | Error exception) {
            detachFullScreenShortcut();
            windowChromeLifecycle.dispose();
            if (stage.isShowing()) {
                stage.hide();
            }
            throw exception;
        }
    }

    /** Закриває вікно після спільного сценарію збереження геометрії та завершення редактора. */
    public void close() {
        requireConfigured();
        try {
            shutdown();
        } finally {
            stage.close();
        }
    }

    /** Згортає головне вікно через API Stage. */
    public void minimize() {
        requireConfigured();
        stage.setIconified(true);
    }

    /** Перемикає головне вікно між звичайним і максимізованим станами через API Stage. */
    public void toggleMaximized() {
        requireConfigured();
        stage.setMaximized(!stage.isMaximized());
    }

    /** Повертає ознаку максимізованого стану вікна. */
    public boolean isMaximized() {
        requireConfigured();
        return stage.isMaximized();
    }

    /** Повертає поточне або заплановане під час запуску значення повноекранного режиму. */
    public boolean isFullScreen() {
        return fullScreenEnabled;
    }

    /**
     * Тимчасово перемикає Stage без запису до глобального файла налаштувань.
     * Зберігає звичайну геометрію окремо, щоб повноекранний розмір не перезаписав розміри вікна.
     *
     * @param enabled бажаний стан повноекранного режиму.
     * @throws IllegalStateException якщо головне вікно ще не налаштовано.
     */
    public void previewFullScreen(boolean enabled) {
        requireConfigured();
        if (fullScreenEnabled == enabled) {
            return;
        }
        if (enabled) {
            lastWindowedState = currentWindowState();
        }
        stage.setFullScreen(enabled);
        fullScreenEnabled = enabled;
        if (!enabled && lastWindowedState != null) {
            restoringWindowedGeometry = true;
            WindowState restoreState = lastWindowedState;
            Platform.runLater(() -> restoreWindowedGeometry(restoreState));
        }
    }

    /**
     * Зберігає підтверджений стан режиму через централізоване сховище глобальних властивостей.
     *
     * @param enabled підтверджений стан повноекранного режиму.
     * @throws IllegalStateException якщо контролер створено без сховища налаштувань.
     */
    public void persistFullScreen(boolean enabled) {
        if (fullScreenPreferenceStore == null) {
            throw new IllegalStateException("Full-screen preference storage is not configured");
        }
        fullScreenPreferenceStore.saveFullScreenEnabled(enabled);
    }

    /** Завершує редактор один раз і звільняє ресурси оформлення вікна та клавіатурних обробників. */
    public void shutdown() {
        try {
            if (lifecycleCoordinator != null) {
                lifecycleCoordinator.shutdown();
            }
        } finally {
            detachFullScreenShortcut();
            windowChromeLifecycle.dispose();
        }
    }

    /**
     * Перемикає повноекранний режим через F11 і зберігає його перед застосуванням нового стану.
     *
     * @param event подія натискання клавіші зі сцени головного вікна.
     */
    private void handleFullScreenShortcut(KeyEvent event) {
        if (event.isConsumed() || event.getCode() != KeyCode.F11 || !configured) {
            return;
        }
        boolean enabled = !fullScreenEnabled;
        if (fullScreenPreferenceStore != null) {
            persistFullScreen(enabled);
        }
        previewFullScreen(enabled);
        Event.fireEvent(fullScreenShortcutScene, new FullScreenShortcutEvent(enabled));
        event.consume();
    }

    /** Від'єднує клавіатурний обробник від його сцени, щоб уникнути витоку після закриття вікна. */
    private void detachFullScreenShortcut() {
        if (fullScreenShortcutScene == null) {
            return;
        }
        fullScreenShortcutScene.removeEventFilter(KeyEvent.KEY_PRESSED, fullScreenShortcutHandler);
        fullScreenShortcutScene = null;
    }

    /** Повертає геометрію для збереження, не записуючи поточний розмір екрана як розмір вікна. */
    private WindowState currentWindowState() {
        if ((fullScreenEnabled || restoringWindowedGeometry) && lastWindowedState != null) {
            return lastWindowedState;
        }
        WindowState current = new WindowState(stage.getWidth(), stage.getHeight(), stage.getX(), stage.getY());
        if (!fullScreenEnabled) {
            lastWindowedState = current;
        }
        return current;
    }

    /** Обчислює початкову геометрію по центру робочої області основного екрана. */
    private static WindowState centeredWindowState(double width, double height) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double x = bounds.getMinX() + Math.max(0.0, (bounds.getWidth() - width) / 2.0);
        double y = bounds.getMinY() + Math.max(0.0, (bounds.getHeight() - height) / 2.0);
        return new WindowState(width, height, x, y);
    }

    /** Відновлює геометрію звичайного вікна після виходу з повноекранного режиму. */
    private void restoreWindowedGeometry(WindowState state) {
        if (!configured || fullScreenEnabled) {
            return;
        }
        stage.setWidth(state.width());
        stage.setHeight(state.height());
        stage.setX(state.x());
        stage.setY(state.y());
        restoringWindowedGeometry = false;
    }

    /** Забороняє керувати вікном до завершення його конфігурації. */
    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException("main window is not configured");
        }
    }
}
