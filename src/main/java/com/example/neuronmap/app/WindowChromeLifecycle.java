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
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Володіє повним життєвим циклом оформлення вікна, не керуючи його геометрією чи завершенням редактора.
 * За наявності підтримки JavaFX використовує системний {@link StageStyle#EXTENDED}; платформні адаптери
 * залишаються резервним шляхом для середовищ, які не підтримують вбудований механізм заголовка.
 */
final class WindowChromeLifecycle {

    private static final Logger LOGGER = Logger.getLogger(WindowChromeLifecycle.class.getName());

    private final Stage stage;
    private final LocalizationService localization;
    private final Supplier<Optional<WindowPlatformAdapter>> adapterFactory;
    private final BooleanSupplier extendedWindowSupport;
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
     * Створює власника оформлення вікна зі спільною локалізацією, перевіркою можливостей JavaFX і callback-ами.
     *
     * @param stage системне JavaFX-вікно, до якого під'єднується оформлення.
     * @param localization спільна служба локалізації застосунку.
     * @param adapterFactory фабрика адаптера для резервного платформного шляху.
     * @param extendedWindowSupport перевірка підтримки вбудованого розширеного заголовка JavaFX.
     * @param closeAction дія закриття через контролер головного вікна.
     * @param minimizeAction дія згортання вікна для резервного шляху.
     * @param toggleMaximizedAction дія перемикання максимізації для резервного шляху.
     * @throws NullPointerException якщо будь-яку залежність не задано.
     */
    WindowChromeLifecycle(
            Stage stage,
            LocalizationService localization,
            Supplier<Optional<WindowPlatformAdapter>> adapterFactory,
            BooleanSupplier extendedWindowSupport,
            Runnable closeAction,
            Runnable minimizeAction,
            Runnable toggleMaximizedAction
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.adapterFactory = Objects.requireNonNull(adapterFactory, "adapterFactory");
        this.extendedWindowSupport = Objects.requireNonNull(extendedWindowSupport, "extendedWindowSupport");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
        this.minimizeAction = Objects.requireNonNull(minimizeAction, "minimizeAction");
        this.toggleMaximizedAction = Objects.requireNonNull(toggleMaximizedAction, "toggleMaximizedAction");
    }

    /**
     * Обирає нативне оформлення JavaFX або резервний адаптер до встановлення сцени та показу Stage.
     * Вбудований режим має пріоритет, бо toolkit сам відповідає за системне переміщення, resize та кнопки заголовка.
     *
     * @param scene сцена редактора, корінь якої отримає власне оформлення.
     * @throws NullPointerException якщо сцена або результат фабрики не задані.
     * @throws IllegalStateException якщо налаштування вже починалося.
     */
    void configure(Scene scene) {
        if (configured) {
            throw new IllegalStateException("window chrome lifecycle is already configured");
        }
        Objects.requireNonNull(scene, "scene");

        if (extendedWindowSupport.getAsBoolean()) {
            configured = true;
            configureExtendedWindow(scene);
            return;
        }

        Optional<WindowPlatformAdapter> adapter = Objects.requireNonNull(
                adapterFactory.get(), "window platform adapter factory result");
        configured = true;
        if (adapter.isEmpty()) {
            return;
        }

        activeAdapter = adapter.orElseThrow();
        try {
            stage.initStyle(StageStyle.UNDECORATED);
            windowChromeView = createWindowChromeView(scene);
            registerHiddenHandler();
        } catch (RuntimeException | Error configurationFailure) {
            disposeAfterConfigurationFailure(configurationFailure);
            throw configurationFailure;
        }
    }

    /**
     * Налаштовує заголовок через JavaFX HeaderBar, не встановлюючи власної процедури Win32.
     *
     * @param scene сцена, до якої вже підготовлено корінь редактора.
     */
    private void configureExtendedWindow(Scene scene) {
        try {
            stage.initStyle(StageStyle.EXTENDED);
            windowChromeView = createWindowChromeView(scene);
            windowChromeView.installExtendedHeaderBar();
            registerHiddenHandler();
        } catch (RuntimeException | Error configurationFailure) {
            disposeAfterConfigurationFailure(configurationFailure);
            throw configurationFailure;
        }
    }

    /**
     * Створює єдине JavaFX-подання заголовка та встановлює його навколо кореня редактора.
     *
     * @param scene сцена з коренем редактора.
     * @return створене подання, яким керує цей життєвий цикл.
     */
    private WindowChromeView createWindowChromeView(Scene scene) {
        WindowChromeView view = new WindowChromeView(
                stage, localization, closeAction, minimizeAction, toggleMaximizedAction);
        view.installInto(scene);
        return view;
    }

    /** Реєструє одноразовий шлях очищення ресурсів після приховування головного вікна. */
    private void registerHiddenHandler() {
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
        hiddenHandlerRegistered = true;
    }

    /**
     * Під'єднує резервний адаптер до вже показаного вікна. Якщо первинний варіант не працює, використовує JavaFX fallback.
     * У вбудованому режимі JavaFX нативний callback не потрібний і метод завершується без додаткового підключення.
     *
     * @throws IllegalStateException якщо життєвий цикл ще не налаштовано.
     * @throws RuntimeException якщо не вдалося встановити ні первинний адаптер, ні fallback.
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
                    "Резервне нативне оформлення не вдалося під'єднати; використовується JavaFX fallback",
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
