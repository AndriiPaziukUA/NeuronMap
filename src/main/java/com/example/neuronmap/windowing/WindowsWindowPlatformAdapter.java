package com.example.neuronmap.windowing;

import com.example.neuronmap.window.WindowChrome;
import com.example.neuronmap.window.WindowHitTestCalculator;
import com.example.neuronmap.window.WindowHitTestCalculator.HitTarget;
import com.example.neuronmap.window.WindowHitTestCalculator.Layout;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Координує інтеграцію власного заголовка з Win32 та керує життєвим циклом системних ресурсів.
 * Пошук HWND і системні стилі залишаються тут, а нативна процедура та системні метрики мають окремі відповідальності.
 */
public final class WindowsWindowPlatformAdapter implements WindowPlatformAdapter {

    private static final Logger LOGGER = Logger.getLogger(WindowsWindowPlatformAdapter.class.getName());

    private static final int GWL_STYLE = -16;
    private static final long WS_CAPTION = 0x00C00000L;
    private static final long WS_POPUP = 0x80000000L;
    private static final long WS_CHILD = 0x40000000L;
    private static final long WS_THICKFRAME = 0x00040000L;
    private static final long WS_SYSMENU = 0x00080000L;
    private static final long WS_MINIMIZEBOX = 0x00020000L;
    private static final long WS_MAXIMIZEBOX = 0x00010000L;
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_FRAMECHANGED = 0x0020;

    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicBoolean frameBoundsFailureLogged = new AtomicBoolean();
    private final WindowsSystemMetrics systemMetrics = new WindowsSystemMetrics();
    private final WindowsWindowLongPtr.Setter windowLongPtrSetter;
    private final NonClientFrameUpdater nonClientFrameUpdater;
    private Stage stage;
    private WindowChrome chrome;
    private HWND hwnd;
    private volatile WindowsUser32Extensions user32Extensions;
    private WindowsWindowProcedure windowProcedure;
    private long previousWindowStyle;
    private boolean previousWindowStyleCaptured;
    private boolean installationAttempted;
    private volatile boolean nativeWindowDestroyed;
    private volatile double outputScaleX = 1.0;
    private volatile double outputScaleY = 1.0;
    private final ChangeListener<Number> outputScaleXListener = (observable, oldValue, newValue) ->
            outputScaleX = WindowsSystemMetrics.validScale(newValue.doubleValue());
    private final ChangeListener<Number> outputScaleYListener = (observable, oldValue, newValue) ->
            outputScaleY = WindowsSystemMetrics.validScale(newValue.doubleValue());
    private final EventHandler<WindowEvent> hiddenHandler = event -> dispose();

    /** Створює адаптер без підключення до Win32 до виклику {@link #install(Stage, WindowChrome)}. */
    public WindowsWindowPlatformAdapter() {
        this(WindowsWindowLongPtr::set, WindowsWindowPlatformAdapter::setNonClientFrame);
    }

    /**
     * Створює адаптер із заданим механізмом зміни атрибутів Win32.
     * Конструктор з ін'єкцією залежності використовується для ізольованої перевірки сценаріїв помилки.
     *
     * @param windowLongPtrSetter перевірений механізм зміни атрибута Win32.
     * @throws NullPointerException якщо механізм не задано.
     */
    WindowsWindowPlatformAdapter(WindowsWindowLongPtr.Setter windowLongPtrSetter) {
        this(windowLongPtrSetter, WindowsWindowPlatformAdapter::setNonClientFrame);
    }

    /**
     * Створює адаптер із контрольованими механізмами зміни атрибутів і оновлення рамки Win32.
     * Заміна операції оновлення рамки дає змогу перевіряти помилки очищення без реального вікна Windows.
     *
     * @param windowLongPtrSetter механізм зміни атрибута Win32.
     * @param nonClientFrameUpdater механізм оновлення рамки вікна.
     * @throws NullPointerException якщо один із механізмів не задано.
     */
    WindowsWindowPlatformAdapter(
            WindowsWindowLongPtr.Setter windowLongPtrSetter,
            NonClientFrameUpdater nonClientFrameUpdater
    ) {
        this.windowLongPtrSetter = Objects.requireNonNull(windowLongPtrSetter, "windowLongPtrSetter");
        this.nonClientFrameUpdater = Objects.requireNonNull(nonClientFrameUpdater, "nonClientFrameUpdater");
    }

    /**
     * Підключає Win32 до показаного JavaFX-вікна: знаходить HWND, змінює стилі та реєструє нативну процедуру.
     * Нативні бібліотеки завантажуються лише після перевірки потоку JavaFX і видимості {@code Stage}.
     * Екземпляр одноразовий, зокрема після невдалої спроби встановлення.
     *
     * @param stage показане головне вікно JavaFX.
     * @param chrome платформонезалежний контракт геометрії та команд заголовка.
     * @throws IllegalStateException якщо адаптер уже використовували, HWND не знайдено або інтеграцію не встановлено.
     * @throws NullPointerException якщо передано порожню залежність.
     */
    @Override
    public void install(Stage stage, WindowChrome chrome) {
        if (installationAttempted || disposed.get()) {
            throw new IllegalStateException("Windows window adapter can only be installed once");
        }
        Stage requestedStage = Objects.requireNonNull(stage, "stage");
        WindowChrome requestedChrome = Objects.requireNonNull(chrome, "chrome");
        installationAttempted = true;
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Windows window adapter must be installed on the JavaFX Application Thread");
        }
        if (!requestedStage.isShowing()) {
            throw new IllegalStateException("Stage must be showing before native window integration");
        }

        this.stage = requestedStage;
        this.chrome = requestedChrome;
        outputScaleX = WindowsSystemMetrics.validScale(requestedStage.getOutputScaleX());
        outputScaleY = WindowsSystemMetrics.validScale(requestedStage.getOutputScaleY());

        try {
            user32Extensions = WindowsUser32Extensions.INSTANCE;
            hwnd = findWindowHandle(requestedStage.getTitle());
            if (hwnd == null) {
                throw new IllegalStateException("Could not locate the native window for the displayed JavaFX stage");
            }

            installNativeWindowProcedureAndStyle();
            requestedStage.outputScaleXProperty().addListener(outputScaleXListener);
            requestedStage.outputScaleYProperty().addListener(outputScaleYListener);
            requestedStage.addEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
        } catch (RuntimeException | LinkageError exception) {
            disposeAfterFailedInstall(exception);
            throw new IllegalStateException("Could not install native Windows window behavior", exception);
        }
    }

    /**
     * Звільняє ресурси адаптера й повідомляє, чи завершилося очищення повністю.
     * Якщо HWND ще існує, але Win32 відмовив у відновленні, ресурси зберігаються для наступної спроби.
     * Після WM_NCDESTROY відновлювати стиль уже не потрібно, а процедура сама звільняє callback.
     */
    @Override
    public synchronized void dispose() {
        disposeAndReportCompletion();
    }

    /**
     * Повторює очищення та повертає його фактичний результат для власника адаптера.
     *
     * @return {@code true}, якщо всі ресурси звільнено або HWND уже знищено; інакше {@code false}.
     */
    @Override
    public synchronized boolean disposeAndReportCompletion() {
        if (disposed.get() && windowProcedure == null && !previousWindowStyleCaptured) {
            return true;
        }
        disposed.set(true);
        boolean restored = restoreNativeWindow();
        removeStageListeners();
        if (!restored) {
            LOGGER.warning("Очищення Windows-вікна не завершено; адаптер потрібно зберегти для повторної спроби");
            return false;
        }
        hwnd = null;
        user32Extensions = null;
        stage = null;
        chrome = null;
        windowProcedure = null;
        previousWindowStyle = 0L;
        previousWindowStyleCaptured = false;
        return true;
    }

    /**
     * Позначає підтверджене знищення HWND та планує завершальне очищення поза нативним callback.
     * Відкладений виклик звільняє посилання JavaFX на правильному потоці й не створює блокування між WndProc
     * та операціями відновлення Win32.
     */
    void onNativeWindowDestroyed() {
        nativeWindowDestroyed = true;
        try {
            Platform.runLater(this::disposeAfterNativeWindowDestroyed);
        } catch (IllegalStateException toolkitUnavailable) {
            LOGGER.log(Level.FINE,
                    "JavaFX уже зупинено; очищення адаптера буде завершено під час наступного виклику dispose()",
                    toolkitUnavailable);
        }
    }

    /**
     * Завершує звільнення адаптера після асинхронного підтвердження знищення вікна.
     * Повторне очищення не виконує нативних операцій, оскільки HWND уже недійсний.
     */
    private void disposeAfterNativeWindowDestroyed() {
        if (nativeWindowDestroyed) {
            disposeAndReportCompletion();
        }
    }

    /**
     * Звільняє ресурси після помилки встановлення, додаючи помилку очищення до первинної причини.
     *
     * @param installationFailure помилка, що перервала встановлення адаптера.
     */
    private void disposeAfterFailedInstall(Throwable installationFailure) {
        try {
            dispose();
        } catch (RuntimeException | LinkageError cleanupFailure) {
            if (cleanupFailure != installationFailure) {
                installationFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    /**
     * Зчитує початковий стиль, установлює нативну процедуру та залишає системні команди й resize активними.
     * Збережені значення потрібні для відновлення в разі помилки або закриття вікна.
     */
    private void installNativeWindowProcedureAndStyle() {
        BaseTSD.LONG_PTR styleValue = User32.INSTANCE.GetWindowLongPtr(hwnd, GWL_STYLE);
        if (styleValue == null) {
            throw new IllegalStateException("Could not read the native window style");
        }
        long style = styleValue.longValue() & 0xFFFF_FFFFL;
        if (style == 0L) {
            throw new IllegalStateException("Native window style is unexpectedly empty");
        }
        previousWindowStyle = style;
        previousWindowStyleCaptured = true;

        windowProcedure = new WindowsWindowProcedure(new WindowsWindowProcedure.Callbacks(
                this::hitTestNativePoint,
                this::hitTestClientPoint,
                this::updateNativeHover,
                this::updateNativePressed,
                this::performNativeButtonAction,
                this::performCaptionDoubleClick
        ), windowLongPtrSetter, this::onNativeWindowDestroyed);
        windowProcedure.install(hwnd);

        long customStyle = (style & ~(WS_CAPTION | WS_POPUP | WS_CHILD))
                | WS_THICKFRAME
                | WS_SYSMENU
                | WS_MINIMIZEBOX
                | WS_MAXIMIZEBOX;
        Pointer previousStyle = windowLongPtrSetter.set(hwnd, GWL_STYLE, new Pointer(customStyle));
        if (previousStyle == null) {
            throw new IllegalStateException("Could not update the native window style");
        }
        updateNonClientFrame();
    }

    /**
     * Визначає HWND за заголовком і PID поточного процесу без використання приватних API JavaFX.
     *
     * @param title заголовок JavaFX-вікна.
     * @return знайдений HWND або {@code null}, якщо відповідного видимого вікна немає.
     */
    private HWND findWindowHandle(String title) {
        long currentProcessId = ProcessHandle.current().pid();
        AtomicReference<HWND> matchedWindow = new AtomicReference<>();
        User32.INSTANCE.EnumWindows((candidate, data) -> {
            if (!User32.INSTANCE.IsWindowVisible(candidate)) {
                return true;
            }
            int titleLength = User32.INSTANCE.GetWindowTextLength(candidate);
            if (titleLength <= 0) {
                return true;
            }
            char[] buffer = new char[titleLength + 1];
            int copied = User32.INSTANCE.GetWindowText(candidate, buffer, buffer.length);
            if (copied <= 0 || !title.equals(new String(buffer, 0, copied))) {
                return true;
            }
            com.sun.jna.ptr.IntByReference processId = new com.sun.jna.ptr.IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(candidate, processId);
            if (Integer.toUnsignedLong(processId.getValue()) == currentProcessId) {
                matchedWindow.set(candidate);
                return false;
            }
            return true;
        }, null);
        return matchedWindow.get();
    }

    /**
     * Розраховує семантичну область за екранними координатами WM_NCHITTEST.
     * Спочатку перевіряється зовнішній простір між GetWindowRect і видимою рамкою DWM; лише після цього
     * координати передаються геометрії JavaFX, яка не резервує внутрішню смугу resize.
     *
     * @param lParam упаковані знакові координати курсора у фізичних пікселях екрана.
     * @return область hit testing або {@code null}, якщо геометрію HWND прочитати не вдалося.
     */
    private HitTarget hitTestNativePoint(LPARAM lParam) {
        WindowChrome currentChrome = chrome;
        HWND window = hwnd;
        WindowsUser32Extensions coordinates = user32Extensions;
        if (currentChrome == null || window == null || coordinates == null) {
            return null;
        }
        Layout layout = currentChrome.hitTestLayout();
        if (layout == null) {
            return null;
        }

        long packedPoint = lParam.longValue();
        int screenX = (short) (packedPoint & 0xFFFFL);
        int screenY = (short) ((packedPoint >>> 16) & 0xFFFFL);

        RECT windowBounds = new RECT();
        RECT visibleBounds = new RECT();
        boolean frameBoundsAvailable = readNativeFrameBounds(window, windowBounds, visibleBounds);
        if (frameBoundsAvailable) {
            WindowHitTestCalculator.Rectangle nativeWindowBounds = toRectangle(windowBounds);
            if (!nativeWindowBounds.contains(screenX, screenY)) {
                return null;
            }
            HitTarget outerTarget = WindowHitTestCalculator.hitTestOuterResizeArea(
                    nativeWindowBounds,
                    toRectangle(visibleBounds),
                    screenX,
                    screenY,
                    layout.maximized()
            );
            if (outerTarget != null) {
                return outerTarget;
            }
        }

        POINT clientOrigin = new POINT();
        if (!coordinates.ClientToScreen(window, clientOrigin)) {
            return null;
        }
        double localX = (screenX - clientOrigin.x) / outputScaleX;
        double localY = (screenY - clientOrigin.y) / outputScaleY;
        return frameBoundsAvailable
                ? WindowHitTestCalculator.hitTestClientArea(layout, localX, localY)
                : WindowHitTestCalculator.hitTest(layout, localX, localY);
    }

    /**
     * Отримує зовнішні та видимі межі HWND. Якщо DWM недоступний, видимі межі оцінюються за системними метриками,
     * щоб resize-зона залишалася зовні JavaFX-контенту.
     *
     * @param window HWND, для якого читаються межі.
     * @param windowBounds вихідний зовнішній прямокутник HWND.
     * @param visibleBounds вихідний прямокутник видимої рамки.
     * @return {@code true}, якщо отримано зовнішній прямокутник і точні або оцінені видимі межі.
     */
    private boolean readNativeFrameBounds(HWND window, RECT windowBounds, RECT visibleBounds) {
        if (!User32.INSTANCE.GetWindowRect(window, windowBounds)) {
            return false;
        }
        windowBounds.read();
        try {
            int result = WindowsDwmExtensions.INSTANCE.DwmGetWindowAttribute(
                    window,
                    WindowsDwmExtensions.DWMWA_EXTENDED_FRAME_BOUNDS,
                    visibleBounds,
                    visibleBounds.size()
            );
            if (result >= 0) {
                visibleBounds.read();
                if (hasFrameInsets(windowBounds, visibleBounds)) {
                    frameBoundsFailureLogged.set(false);
                    return true;
                }
                logFrameBoundsFailure(
                        "DWM не повернув окрему видиму рамку; використовуються системні метрики", null);
            } else {
                logFrameBoundsFailure("DwmGetWindowAttribute повернув HRESULT " + result, null);
            }
        } catch (RuntimeException | LinkageError exception) {
            logFrameBoundsFailure("Не вдалося отримати видимі межі рамки через DWM", exception);
        }

        WindowsSystemMetrics.ResizeBorders borders =
                systemMetrics.readResizeBorders(window, outputScaleX, outputScaleY);
        int horizontal = (int) Math.ceil(borders.horizontal() * outputScaleX);
        int vertical = (int) Math.ceil(borders.vertical() * outputScaleY);
        visibleBounds.left = windowBounds.left + horizontal;
        visibleBounds.right = windowBounds.right - horizontal;
        visibleBounds.top = windowBounds.top + vertical;
        visibleBounds.bottom = windowBounds.bottom - vertical;
        if (visibleBounds.right < visibleBounds.left) {
            visibleBounds.left = windowBounds.left;
            visibleBounds.right = windowBounds.right;
        }
        if (visibleBounds.bottom < visibleBounds.top) {
            visibleBounds.top = windowBounds.top;
            visibleBounds.bottom = windowBounds.bottom;
        }
        return true;
    }

    /**
     * Перевіряє, що межі DWM лежать усередині зовнішнього прямокутника та залишають зовнішню смугу.
     *
     * @param windowBounds зовнішні межі HWND.
     * @param visibleBounds видимі межі DWM.
     * @return {@code true}, якщо зовнішній прямокутник містить видиму рамку й має хоча б один відступ.
     */
    private static boolean hasFrameInsets(RECT windowBounds, RECT visibleBounds) {
        boolean contained = visibleBounds.left >= windowBounds.left
                && visibleBounds.top >= windowBounds.top
                && visibleBounds.right <= windowBounds.right
                && visibleBounds.bottom <= windowBounds.bottom;
        boolean inset = windowBounds.left < visibleBounds.left
                || windowBounds.top < visibleBounds.top
                || windowBounds.right > visibleBounds.right
                || windowBounds.bottom > visibleBounds.bottom;
        return contained && inset;
    }

    /**
     * Записує помилку визначення видимих меж лише один раз, щоб рух курсора не засмічував журнал.
     *
     * @param message опис проблеми читання меж.
     * @param exception причина помилки або {@code null}, якщо DWM повернув помилковий HRESULT.
     */
    private void logFrameBoundsFailure(String message, Throwable exception) {
        if (!frameBoundsFailureLogged.compareAndSet(false, true)) {
            return;
        }
        if (exception == null) {
            LOGGER.fine(message);
        } else {
            LOGGER.log(Level.FINE, message, exception);
        }
    }

    /**
     * Перетворює нативний прямокутник Win32 на платформонезалежну геометрію.
     *
     * @param rectangle прямокутник у координатах екрана.
     * @return еквівалентний прямокутник обчислювача.
     */
    private static WindowHitTestCalculator.Rectangle toRectangle(RECT rectangle) {
        return new WindowHitTestCalculator.Rectangle(
                rectangle.left, rectangle.top, rectangle.right, rectangle.bottom);
    }

    /**
     * Перевіряє точку в координатах клієнтської області під час захоплення миші кнопкою.
     *
     * @param lParam упаковані координати миші відносно клієнтської області.
     * @return кнопка або інша область під курсором; {@code null}, якщо геометрія ще не готова.
     */
    private HitTarget hitTestClientPoint(LPARAM lParam) {
        WindowChrome currentChrome = chrome;
        if (currentChrome == null) {
            return null;
        }
        Layout layout = currentChrome.hitTestLayout();
        if (layout == null) {
            return null;
        }
        long packedPoint = lParam.longValue();
        int clientX = (short) (packedPoint & 0xFFFFL);
        int clientY = (short) ((packedPoint >>> 16) & 0xFFFFL);
        return WindowHitTestCalculator.hitTestClientArea(layout, clientX / outputScaleX, clientY / outputScaleY);
    }

    /**
     * Передає зміну наведення у JavaFX Application Thread.
     *
     * @param target активна кнопка або {@code null}, якщо курсор залишив кнопки.
     */
    private void updateNativeHover(HitTarget target) {
        WindowChrome currentChrome = chrome;
        if (currentChrome == null || disposed.get()) {
            return;
        }
        Platform.runLater(() -> {
            if (!disposed.get() && chrome == currentChrome) {
                currentChrome.setNativeHoveredControl(target);
            }
        });
    }

    /**
     * Передає зміну натискання у JavaFX Application Thread.
     *
     * @param target натиснута кнопка або {@code null}, якщо натискання завершено.
     */
    private void updateNativePressed(HitTarget target) {
        WindowChrome currentChrome = chrome;
        if (currentChrome == null || disposed.get()) {
            return;
        }
        Platform.runLater(() -> {
            if (!disposed.get() && chrome == currentChrome) {
                currentChrome.setNativePressedControl(target);
            }
        });
    }

    /**
     * Виконує команду кнопки у JavaFX Application Thread після нативного клацання.
     *
     * @param target кнопка, на якій завершився клік.
     */
    private void performNativeButtonAction(HitTarget target) {
        WindowChrome currentChrome = chrome;
        if (currentChrome == null || disposed.get()) {
            return;
        }
        Platform.runLater(() -> {
            if (!disposed.get() && chrome == currentChrome) {
                currentChrome.performNativeButtonAction(target);
            }
        });
    }

    /** Виконує перемикання максимізації після подвійного клацання по заголовку у JavaFX Application Thread. */
    private void performCaptionDoubleClick() {
        WindowChrome currentChrome = chrome;
        if (currentChrome == null || disposed.get()) {
            return;
        }
        Platform.runLater(() -> {
            if (!disposed.get() && chrome == currentChrome) {
                currentChrome.performNativeCaptionDoubleClick();
            }
        });
    }

    /**
     * Оновлює рамку вікна через контрольований механізм і передає помилки до процедури очищення.
     * Відсутній HWND означає, що немає чинної нативної операції для виконання.
     */
    private void updateNonClientFrame() {
        HWND currentWindow = hwnd;
        if (currentWindow != null) {
            nonClientFrameUpdater.update(currentWindow);
        }
    }

    /**
     * Викликає {@code SetWindowPos} для повторного обчислення неклієнтської рамки вікна.
     *
     * @param window дескриптор вікна, рамку якого потрібно оновити.
     * @throws Win32Exception якщо операція завершилася невдало.
     */
    private static void setNonClientFrame(HWND window) {
        Kernel32.INSTANCE.SetLastError(0);
        boolean updated = User32.INSTANCE.SetWindowPos(
                window,
                null,
                0,
                0,
                0,
                0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED
        );
        validateSetWindowPosResult(updated, Kernel32.INSTANCE.GetLastError());
    }

    /**
     * Перевіряє результат {@code SetWindowPos} і перетворює невдалий виклик на виняток Win32.
     *
     * @param updated логічний результат операції Win32.
     * @param errorCode код помилки, прочитаний відразу після виклику.
     * @throws Win32Exception якщо оновлення не вдалося; для нульового коду використовується
     *                        стандартна помилка загального збою Win32.
     */
    static void validateSetWindowPosResult(boolean updated, int errorCode) {
        if (!updated) {
            throw new Win32Exception(errorCode == 0 ? WinError.ERROR_GEN_FAILURE : errorCode);
        }
    }

    /**
     * Відновлює стиль перед від'єднанням WndProc, щоб callback міг безпечно дочекатися повторної спроби.
     * Після підтвердженого знищення HWND пропускає нативні виклики та завершує очищення локального стану.
     *
     * @return {@code true}, якщо відновлення завершено або HWND знищено; інакше {@code false}.
     */
    private boolean restoreNativeWindow() {
        if (nativeWindowDestroyed) {
            clearDestroyedWindowState();
            return true;
        }

        WindowsWindowProcedure currentProcedure = windowProcedure;
        if (currentProcedure != null) {
            currentProcedure.deactivate();
        }

        if (previousWindowStyleCaptured) {
            HWND currentWindow = hwnd;
            if (currentWindow == null) {
                LOGGER.warning("Неможливо відновити початковий стиль вікна Windows: дескриптор відсутній");
                return false;
            }
            try {
                windowLongPtrSetter.set(currentWindow, GWL_STYLE, new Pointer(previousWindowStyle));
                if (nativeWindowDestroyed) {
                    clearDestroyedWindowState();
                    return true;
                }
                updateNonClientFrame();
                previousWindowStyleCaptured = false;
            } catch (RuntimeException | LinkageError exception) {
                if (nativeWindowDestroyed) {
                    clearDestroyedWindowState();
                    return true;
                }
                LOGGER.log(Level.WARNING,
                        "Не вдалося відновити початковий стиль або оновити рамку вікна Windows",
                        exception);
                return false;
            }
        }

        if (nativeWindowDestroyed) {
            clearDestroyedWindowState();
            return true;
        }
        if (currentProcedure != null) {
            if (currentProcedure.dispose()) {
                windowProcedure = null;
            } else {
                LOGGER.warning("Нативна процедура вікна ще не відновлена; callback збережено до WM_NCDESTROY");
                return false;
            }
        }
        return windowProcedure == null && !previousWindowStyleCaptured;
    }

    /** Контракт операції оновлення рамки, потрібний для перевірки помилок очищення без виклику user32. */
    @FunctionalInterface
    interface NonClientFrameUpdater {

        /**
         * Оновлює рамку заданого вікна або кидає виняток, якщо операція не вдалася.
         *
         * @param window дескриптор вікна.
         */
        void update(HWND window);
    }

    /** Скидає нативні посилання після WM_NCDESTROY, коли звертатися до HWND більше не можна. */
    private void clearDestroyedWindowState() {
        hwnd = null;
        user32Extensions = null;
        windowProcedure = null;
        previousWindowStyle = 0L;
        previousWindowStyleCaptured = false;
    }

    /** Від'єднує слухачі Stage, щоб закрите вікно не утримувало адаптер через властивості масштабування. */
    private void removeStageListeners() {
        Stage currentStage = stage;
        if (currentStage != null) {
            currentStage.outputScaleXProperty().removeListener(outputScaleXListener);
            currentStage.outputScaleYProperty().removeListener(outputScaleYListener);
            currentStage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
        }
    }
}
