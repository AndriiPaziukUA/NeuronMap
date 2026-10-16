package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.window.WindowState;
import com.example.neuronmap.persistence.window.WindowStateStore;
import com.example.neuronmap.windowing.WindowPlatformAdapter;
import com.example.neuronmap.windowing.WindowPlatformAdapterFactory;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Керує головним JavaFX-вікном, відновленням його геометрії та узгодженим життєвим циклом редактора.
 * Контролер володіє системним {@link Stage}, а оформлення й платформні ресурси делегує {@link WindowChromeLifecycle}.
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
     * Створює контролер головного вікна зі спільним сховищем геометрії та службою локалізації застосунку.
     * JavaFX повідомляє, чи підтримує поточний toolkit розширену системну смугу заголовка.
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
                WindowPlatformAdapterFactory::createForCurrentOperatingSystem,
                Objects.requireNonNull(stage, "stage")::show,
                () -> Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW));
    }

    /**
     * Створює контролер із постачальником адаптера для тестування резервного шляху незалежно від ОС.
     * Цей конструктор явно залишає вибір режиму розширеного вікна вимкненим, щоб тести могли перевіряти fallback.
     *
     * @param stage головне JavaFX-вікно.
     * @param windowStateStore сховище геометрії вікна.
     * @param localization служба поточної локалі та перекладів.
     * @param windowPlatformAdapterFactory постачальник адаптера для резервної платформної інтеграції.
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
     * Створює контролер із можливістю підмінити показ вікна в тестах без створення нативного вікна ОС.
     *
     * @param stage головне JavaFX-вікно.
     * @param windowStateStore сховище геометрії вікна.
     * @param localization спільна служба локалізації застосунку.
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
        this(stage, windowStateStore, localization, windowPlatformAdapterFactory,
                showWindowAction, () -> false);
    }

    /**
     * Створює контролер із контрольованою перевіркою підтримки нативного заголовка JavaFX.
     * Такий контракт дозволяє окремо перевірити новий шлях і резервну інтеграцію без залежності від ОС.
     *
     * @param stage головне JavaFX-вікно.
     * @param windowStateStore сховище геометрії вікна.
     * @param localization спільна служба локалізації застосунку.
     * @param windowPlatformAdapterFactory постачальник адаптера для резервного шляху.
     * @param showWindowAction дія показу вікна.
     * @param extendedWindowSupport перевірка підтримки {@link ConditionalFeature#EXTENDED_WINDOW}.
     * @throws NullPointerException якщо будь-яку залежність або дію не задано.
     */
    MainWindowController(
            Stage stage,
            WindowStateStore windowStateStore,
            LocalizationService localization,
            Supplier<Optional<WindowPlatformAdapter>> windowPlatformAdapterFactory,
            Runnable showWindowAction,
            BooleanSupplier extendedWindowSupport
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.windowStateStore = Objects.requireNonNull(windowStateStore, "windowStateStore");
        LocalizationService requiredLocalization = Objects.requireNonNull(localization, "localization");
        Supplier<Optional<WindowPlatformAdapter>> requiredAdapterFactory = Objects.requireNonNull(
                windowPlatformAdapterFactory, "windowPlatformAdapterFactory");
        this.showWindowAction = Objects.requireNonNull(showWindowAction, "showWindowAction");
        BooleanSupplier requiredExtendedWindowSupport = Objects.requireNonNull(
                extendedWindowSupport, "extendedWindowSupport");
        this.windowChromeLifecycle = new WindowChromeLifecycle(
                this.stage,
                requiredLocalization,
                requiredAdapterFactory,
                requiredExtendedWindowSupport,
                this::close,
                this::minimize,
                this::toggleMaximized
        );
    }

    /**
     * Налаштовує вікно, відновлює його геометрію та під'єднує спільний шлях завершення редактора.
     * Викликати метод слід один раз до показу Stage.
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

        windowChromeLifecycle.configure(scene);
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
     * Показує налаштоване вікно й передає вже показаний Stage власнику системного оформлення.
     * Центрування виконується лише за відсутності збереженої геометрії. Повторний показ заборонений.
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
            windowChromeLifecycle.installAfterShowing();
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
     * Запитує звичайне закриття вікна; збереження та завершення редактора виконує спільний життєвий цикл.
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
     * Згортає головне вікно через API Stage, щоб його стан залишався узгодженим із JavaFX.
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
     * Один раз завершує збереження та редактор, а кожним викликом дає повторну спробу очищення вікна.
     * Метод також викликається з {@code Application.stop()} як резервний шлях завершення.
     */
    public void shutdown() {
        try {
            if (lifecycleCoordinator != null) {
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
