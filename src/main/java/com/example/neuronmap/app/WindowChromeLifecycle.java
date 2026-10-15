package com.example.neuronmap.app;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.window.WindowChromeView;
import com.example.neuronmap.windowing.JavaFxWindowPlatformAdapter;
import com.example.neuronmap.windowing.WindowPlatformAdapter;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Володіє повним життєвим циклом власного оформлення вікна, не керуючи геометрією чи завершенням редактора.
 * Вибір основного адаптера делегується фабриці, а для помилки нативного підключення використовується спільний
 * JavaFX-адаптер. Адаптери з незавершеним очищенням зберігаються окремо від активного адаптера.
 */
final class WindowChromeLifecycle {

    private static final Logger LOGGER = Logger.getLogger(WindowChromeLifecycle.class.getName());

    private final Stage stage;
    private final LocalizationService localization;
    private final Supplier<Optional<WindowPlatformAdapter>> adapterFactory;
    private final Runnable closeAction;
    private final Runnable minimizeAction;
    private final Runnable toggleMaximizedAction;
    private final javafx.event.EventHandler<WindowEvent> hiddenHandler = event -> dispose();
    private final List<WindowPlatformAdapter> adaptersPendingCleanup = new ArrayList<>();

    private WindowChromeView windowChromeView;
    private WindowPlatformAdapter activeAdapter;
    private boolean configured;
    private boolean adapterInstalled;
    private boolean hiddenHandlerRegistered;

    /**
     * Створює власника оформлення вікна зі спільною локалізацією та діями керування, наданими контролером.
     *
     * @param stage системне JavaFX-вікно, до якого під'єднується оформлення.
     * @param localization спільна служба локалізації застосунку.
     * @param adapterFactory фабрика адаптера для поточної платформи.
     * @param closeAction дія закриття, що делегується контролеру вікна.
     * @param minimizeAction дія згортання вікна.
     * @param toggleMaximizedAction дія перемикання максимізації.
     * @throws NullPointerException якщо будь-яку залежність не задано.
     */
    WindowChromeLifecycle(
            Stage stage,
            LocalizationService localization,
            Supplier<Optional<WindowPlatformAdapter>> adapterFactory,
            Runnable closeAction,
            Runnable minimizeAction,
            Runnable toggleMaximizedAction
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.adapterFactory = Objects.requireNonNull(adapterFactory, "adapterFactory");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
        this.minimizeAction = Objects.requireNonNull(minimizeAction, "minimizeAction");
        this.toggleMaximizedAction = Objects.requireNonNull(toggleMaximizedAction, "toggleMaximizedAction");
    }

    /**
     * Створює та встановлює подання заголовка, якщо фабрика зареєструвала підтримку поточної ОС.
     * Метод викликається до показу Stage, адже JavaFX вимагає налаштувати стиль вікна заздалегідь.
     *
     * @param scene сцена редактора, корінь якої буде обгорнуто поданням заголовка.
     * @throws NullPointerException якщо сцена або результат фабрики не задані.
     * @throws IllegalStateException якщо налаштування вже починалося.
     */
    void configure(Scene scene) {
        if (configured) {
            throw new IllegalStateException("window chrome lifecycle is already configured");
        }
        Objects.requireNonNull(scene, "scene");

        Optional<WindowPlatformAdapter> adapter = Objects.requireNonNull(
                adapterFactory.get(), "window platform adapter factory result");
        configured = true;
        if (adapter.isEmpty()) {
            return;
        }

        activeAdapter = adapter.orElseThrow();
        try {
            stage.initStyle(StageStyle.UNDECORATED);
            windowChromeView = new WindowChromeView(
                    stage, localization, closeAction, minimizeAction, toggleMaximizedAction);
            windowChromeView.installInto(scene);
            stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
            hiddenHandlerRegistered = true;
        } catch (RuntimeException | Error configurationFailure) {
            disposeAfterConfigurationFailure(configurationFailure);
            throw configurationFailure;
        }
    }

    /**
     * Під'єднує адаптер до вже показаного вікна. Якщо основна реалізація не працює, один раз використовує JavaFX fallback.
     *
     * @throws IllegalStateException якщо життєвий цикл ще не налаштовано.
     * @throws RuntimeException якщо не вдалося встановити ні основний адаптер, ні fallback.
     * @throws LinkageError якщо під'єднання адаптера не може виконатися через помилку завантаження платформного коду.
     */
    void installAfterShowing() {
        if (!configured) {
            throw new IllegalStateException("window chrome lifecycle is not configured");
        }
        if (activeAdapter == null || windowChromeView == null || adapterInstalled) {
            return;
        }

        WindowPlatformAdapter primaryAdapter = activeAdapter;
        Throwable primaryFailure;
        try {
            primaryAdapter.install(stage, windowChromeView);
            adapterInstalled = true;
            return;
        } catch (RuntimeException | LinkageError installationFailure) {
            primaryFailure = installationFailure;
            disposeAfterInstallationFailure(primaryAdapter, installationFailure);
            if (primaryAdapter instanceof JavaFxWindowPlatformAdapter) {
                activeAdapter = null;
                throw installationFailure;
            }
            LOGGER.log(Level.WARNING,
                    "Нативне оформлення вікна не вдалося під'єднати; використовується резервне керування JavaFX",
                    installationFailure);
        }

        WindowPlatformAdapter fallbackAdapter = new JavaFxWindowPlatformAdapter();
        activeAdapter = fallbackAdapter;
        try {
            fallbackAdapter.install(stage, windowChromeView);
            adapterInstalled = true;
        } catch (RuntimeException | LinkageError fallbackFailure) {
            disposeAfterInstallationFailure(fallbackAdapter, fallbackFailure);
            if (fallbackFailure != primaryFailure) {
                fallbackFailure.addSuppressed(primaryFailure);
            }
            activeAdapter = null;
            throw fallbackFailure;
        }
    }

    /**
     * Звільняє ресурси активного адаптера та повторює очищення адаптерів, що очікують на завершення.
     * Посилання зберігаються, доки кожен адаптер не підтвердить успішне звільнення ресурсів.
     */
    void dispose() {
        WindowPlatformAdapter adapterToDispose = activeAdapter;
        WindowChromeView viewToDispose = windowChromeView;
        boolean adapterDisposed = adapterToDispose == null;
        Throwable cleanupFailure = null;

        if (adapterToDispose != null) {
            try {
                adapterDisposed = adapterToDispose.disposeAndReportCompletion();
            } catch (RuntimeException | LinkageError exception) {
                cleanupFailure = exception;
            }
        }

        if (viewToDispose != null) {
            try {
                viewToDispose.dispose();
            } catch (RuntimeException | LinkageError exception) {
                cleanupFailure = combineFailures(cleanupFailure, exception);
            }
        }

        cleanupFailure = combineFailures(cleanupFailure, retryPendingAdapterCleanup());
        windowChromeView = null;
        adapterInstalled = false;

        if (adapterDisposed && activeAdapter == adapterToDispose) {
            activeAdapter = null;
        }
        if (adapterDisposed && adaptersPendingCleanup.isEmpty()) {
            removeHiddenHandler();
        }
        if (!adapterDisposed) {
            LOGGER.warning("Очищення активного адаптера вікна не завершено; посилання збережено для повторної спроби");
        }
        if (!adaptersPendingCleanup.isEmpty()) {
            LOGGER.warning("Очищення одного або кількох попередніх адаптерів не завершено; повторну спробу буде збережено");
        }

        if (cleanupFailure != null) {
            LOGGER.log(Level.WARNING, "Не вдалося повністю очистити ресурси власного оформлення вікна", cleanupFailure);
        }
    }

    /**
     * Очищає частково створені ресурси після помилки налаштування, не замінюючи першопричину помилкою очищення.
     *
     * @param configurationFailure помилка, що перервала початкове налаштування оформлення.
     */
    private void disposeAfterConfigurationFailure(Throwable configurationFailure) {
        try {
            dispose();
        } catch (RuntimeException | LinkageError cleanupFailure) {
            if (cleanupFailure != configurationFailure) {
                configurationFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    /**
     * Звільняє адаптер після невдалого встановлення та зберігає його, якщо очищення не завершилося.
     * Помилка очищення додається до первинного збою, не приховуючи його причину.
     *
     * @param adapter адаптер, який не вдалося встановити.
     * @param installationFailure першопричина, до якої додаються помилки очищення.
     */
    private void disposeAfterInstallationFailure(WindowPlatformAdapter adapter, Throwable installationFailure) {
        try {
            if (adapter.disposeAndReportCompletion()) {
                adaptersPendingCleanup.removeIf(pendingAdapter -> pendingAdapter == adapter);
                return;
            }
            retainAdapterForCleanup(adapter);
            LOGGER.warning("Очищення адаптера після невдалого встановлення не завершено; збережено для повторної спроби");
        } catch (RuntimeException | LinkageError cleanupFailure) {
            retainAdapterForCleanup(adapter);
            if (installationFailure != cleanupFailure) {
                installationFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    /**
     * Повторює очищення адаптерів, які вже не є активними, і вилучає лише підтверджено очищені екземпляри.
     *
     * @return об'єднані винятки, що виникли під час повторного очищення, або {@code null}, якщо винятків не було.
     */
    private Throwable retryPendingAdapterCleanup() {
        Throwable cleanupFailure = null;
        Iterator<WindowPlatformAdapter> pendingAdapters = adaptersPendingCleanup.iterator();
        while (pendingAdapters.hasNext()) {
            WindowPlatformAdapter pendingAdapter = pendingAdapters.next();
            try {
                if (pendingAdapter.disposeAndReportCompletion()) {
                    pendingAdapters.remove();
                } else {
                    LOGGER.warning("Повторне очищення попереднього адаптера ще не завершено");
                }
            } catch (RuntimeException | LinkageError exception) {
                cleanupFailure = combineFailures(cleanupFailure, exception);
            }
        }
        return cleanupFailure;
    }

    /**
     * Зберігає адаптер для повторного очищення, не додаючи повторні посилання на той самий екземпляр.
     *
     * @param adapter адаптер, ресурси якого ще можуть залишатися зайнятими.
     */
    private void retainAdapterForCleanup(WindowPlatformAdapter adapter) {
        boolean alreadyRetained = adaptersPendingCleanup.stream().anyMatch(pendingAdapter -> pendingAdapter == adapter);
        if (!alreadyRetained) {
            adaptersPendingCleanup.add(adapter);
        }
    }

    /**
     * Поєднує послідовні помилки очищення, зберігаючи першу як головну.
     *
     * @param primaryFailure перша помилка або {@code null}, якщо її ще немає.
     * @param nextFailure наступна помилка очищення.
     * @return перша помилка зі збереженою додатковою причиною.
     */
    private Throwable combineFailures(Throwable primaryFailure, Throwable nextFailure) {
        if (primaryFailure == null) {
            return nextFailure;
        }
        if (nextFailure != null && primaryFailure != nextFailure) {
            primaryFailure.addSuppressed(nextFailure);
        }
        return primaryFailure;
    }

    /** Від'єднує обробник приховування лише після підтвердженого очищення всіх адаптерів. */
    private void removeHiddenHandler() {
        if (hiddenHandlerRegistered) {
            stage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
            hiddenHandlerRegistered = false;
        }
    }
}
