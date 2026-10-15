package com.example.neuronmap.windowing;

import com.example.neuronmap.window.WindowChrome;
import com.example.neuronmap.window.WindowHitTestCalculator;
import com.example.neuronmap.window.WindowHitTestCalculator.HitTarget;
import com.example.neuronmap.window.WindowHitTestCalculator.Layout;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
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
    private final WindowsSystemMetrics systemMetrics = new WindowsSystemMetrics();
    private Stage stage;
    private WindowChrome chrome;
    private HWND hwnd;
    private volatile WindowsUser32Extensions user32Extensions;
    private WindowsWindowProcedure windowProcedure;
    private long previousWindowStyle;
    private boolean previousWindowStyleCaptured;
    private boolean installationAttempted;
    private volatile double outputScaleX = 1.0;
    private volatile double outputScaleY = 1.0;
    private final ChangeListener<Number> outputScaleXListener = (observable, oldValue, newValue) -> {
        outputScaleX = WindowsSystemMetrics.validScale(newValue.doubleValue());
        updateNativeResizeBorders();
    };
    private final ChangeListener<Number> outputScaleYListener = (observable, oldValue, newValue) -> {
        outputScaleY = WindowsSystemMetrics.validScale(newValue.doubleValue());
        updateNativeResizeBorders();
    };
    private final EventHandler<WindowEvent> hiddenHandler = event -> dispose();

    /** Створює адаптер без підключення до Win32 до виклику {@link #install(Stage, WindowChrome)}. */
    public WindowsWindowPlatformAdapter() {
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
            updateNativeResizeBorders();
            requestedStage.outputScaleXProperty().addListener(outputScaleXListener);
            requestedStage.outputScaleYProperty().addListener(outputScaleYListener);
            requestedStage.addEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
        } catch (RuntimeException | LinkageError exception) {
            disposeAfterFailedInstall(exception);
            throw new IllegalStateException("Could not install native Windows window behavior", exception);
        }
    }

    /**
     * Відновлює початкові стилі та процедуру вікна й від'єднує слухачі адаптера.
     * Повторний виклик безпечний; після очищення цей екземпляр не можна встановлювати повторно.
     */
    @Override
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        restoreNativeWindow();
        removeStageListeners();
        hwnd = null;
        user32Extensions = null;
        stage = null;
        chrome = null;
        windowProcedure = null;
        previousWindowStyle = 0L;
        previousWindowStyleCaptured = false;
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
        ));
        windowProcedure.install(hwnd);

        long customStyle = (style & ~(WS_CAPTION | WS_POPUP | WS_CHILD))
                | WS_THICKFRAME
                | WS_SYSMENU
                | WS_MINIMIZEBOX
                | WS_MAXIMIZEBOX;
        Pointer previousStyle = User32.INSTANCE.SetWindowLongPtr(hwnd, GWL_STYLE, new Pointer(customStyle));
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
     * Розраховує семантичну область вікна за екранними координатами повідомлення {@code WM_NCHITTEST}.
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
        RECT clientRectangle = new RECT();
        POINT clientOrigin = new POINT();
        if (!User32.INSTANCE.GetClientRect(window, clientRectangle)
                || !coordinates.ClientToScreen(window, clientOrigin)) {
            return null;
        }
        long packedPoint = lParam.longValue();
        int screenX = (short) (packedPoint & 0xFFFFL);
        int screenY = (short) ((packedPoint >>> 16) & 0xFFFFL);
        double localX = (screenX - clientOrigin.x) / outputScaleX;
        double localY = (screenY - clientOrigin.y) / outputScaleY;
        return WindowHitTestCalculator.hitTest(layout, localX, localY);
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
        return WindowHitTestCalculator.hitTest(layout, clientX / outputScaleX, clientY / outputScaleY);
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

    /** Оновлює системну зону зміни розміру через окремий обчислювач метрик Windows. */
    private void updateNativeResizeBorders() {
        Stage currentStage = stage;
        WindowChrome currentChrome = chrome;
        HWND currentWindow = hwnd;
        if (currentStage == null || currentChrome == null || currentWindow == null || disposed.get()
                || !Platform.isFxApplicationThread()) {
            return;
        }
        WindowsSystemMetrics.ResizeBorders borders =
                systemMetrics.readResizeBorders(currentWindow, outputScaleX, outputScaleY);
        currentChrome.setNativeResizeBorders(borders.horizontal(), borders.vertical());
    }

    /** Оновлює системний стиль і просить Windows повторно обчислити рамку вікна. */
    private void updateNonClientFrame() {
        HWND currentWindow = hwnd;
        if (currentWindow == null) {
            return;
        }
        User32.INSTANCE.SetWindowPos(
                currentWindow,
                null,
                0,
                0,
                0,
                0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED
        );
    }

    /**
     * Відновлює попередню процедуру та початковий стиль незалежними спробами, щоб одна помилка не блокувала іншу.
     */
    private void restoreNativeWindow() {
        WindowsWindowProcedure currentProcedure = windowProcedure;
        if (currentProcedure != null) {
            currentProcedure.dispose();
            windowProcedure = null;
        }

        HWND currentWindow = hwnd;
        if (currentWindow == null || !previousWindowStyleCaptured) {
            return;
        }
        try {
            User32.INSTANCE.SetWindowLongPtr(currentWindow, GWL_STYLE, new Pointer(previousWindowStyle));
            updateNonClientFrame();
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.log(Level.WARNING, "Не вдалося відновити початковий стиль вікна Windows", exception);
        } finally {
            previousWindowStyleCaptured = false;
        }
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
