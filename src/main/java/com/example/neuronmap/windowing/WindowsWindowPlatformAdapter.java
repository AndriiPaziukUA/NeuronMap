package com.example.neuronmap.windowing;

import com.example.neuronmap.view.window.WindowChromeView;
import com.sun.jna.CallbackReference;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
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
 * Інтегрує власний заголовок із Win32, залишаючи переміщення, зміну розміру та системні команди в розпорядженні Windows.
 * Вікно отримує нативні hit-test-коди для країв, області заголовка й кнопок; HTMAXBUTTON потрібен для Snap Layouts Windows 11.
 */
public final class WindowsWindowPlatformAdapter implements WindowPlatformAdapter {

    private static final Logger LOGGER = Logger.getLogger(WindowsWindowPlatformAdapter.class.getName());
    private static final User32CoordinateFunctions USER32_COORDINATES = Native.load(
            "user32", User32CoordinateFunctions.class, W32APIOptions.DEFAULT_OPTIONS
    );

    private static final int GWL_STYLE = -16;
    private static final int GWL_WNDPROC = -4;
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

    private static final int WM_NCCALCSIZE = 0x0083;
    private static final int WM_NCHITTEST = 0x0084;
    private static final int WM_NCLBUTTONDOWN = 0x00A1;
    private static final int WM_NCLBUTTONUP = 0x00A2;
    private static final int WM_NCLBUTTONDBLCLK = 0x00A3;
    private static final int WM_NCMOUSEMOVE = 0x00A0;
    private static final int WM_NCMOUSELEAVE = 0x02A2;
    private static final int WM_MOUSEMOVE = 0x0200;
    private static final int WM_LBUTTONUP = 0x0202;
    private static final int WM_CAPTURECHANGED = 0x0215;
    private static final int SM_CXSIZEFRAME = 32;
    private static final int SM_CYSIZEFRAME = 33;
    private static final int SM_CXPADDEDBORDER = 92;

    /** Нативні функції user32, відсутні в інтерфейсі User32 конкретної версії JNA. */
    private interface User32CoordinateFunctions extends StdCallLibrary {
        /** Перетворює координати клієнтської області у координати екрана.
         * @param window дескриптор вікна.
         * @param point точка клієнтської області на вході та екранна точка на виході.
         * @return {@code true}, якщо перетворення виконано.
         */
        boolean ClientToScreen(HWND window, POINT point);
    }

    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicReference<WindowHitTestCalculator.HitTarget> pressedTarget = new AtomicReference<>();
    private Stage stage;
    private WindowChromeView chromeView;
    private HWND hwnd;
    private Pointer previousWindowProcedure;
    private long previousWindowStyle;
    private boolean previousWindowStyleCaptured;
    private WinUser.WindowProc windowProcedure;
    private volatile double outputScaleX = 1.0;
    private volatile double outputScaleY = 1.0;
    private final ChangeListener<Number> outputScaleXListener = (observable, oldValue, newValue) -> {
        outputScaleX = validScale(newValue.doubleValue());
        updateNativeResizeBorders();
    };
    private final ChangeListener<Number> outputScaleYListener = (observable, oldValue, newValue) -> {
        outputScaleY = validScale(newValue.doubleValue());
        updateNativeResizeBorders();
    };
    private final EventHandler<WindowEvent> hiddenHandler = event -> dispose();

    /** Створює адаптер без підключення до Win32 до виклику {@link #install(Stage, WindowChromeView)}. */
    public WindowsWindowPlatformAdapter() {
    }

    /**
     * Знаходить HWND показаного JavaFX-вікна, додає потрібні стилі Win32 і встановлює власний ланцюжок обробки повідомлень.
     * Метод викликається після {@code Stage.show()}, коли віконний дескриптор уже створений.
     *
     * @param stage показане головне вікно JavaFX.
     * @param chromeView подання з геометрією кнопок і командами керування вікном.
     * @throws IllegalStateException якщо HWND не знайдено або нативний обробник неможливо встановити.
     * @throws NullPointerException якщо передано порожню залежність.
     */
    @Override
    public void install(Stage stage, WindowChromeView chromeView) {
        if (this.hwnd != null) {
            throw new IllegalStateException("Windows window adapter is already installed");
        }
        this.stage = Objects.requireNonNull(stage, "stage");
        this.chromeView = Objects.requireNonNull(chromeView, "chromeView");
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Windows window adapter must be installed on the JavaFX Application Thread");
        }
        if (!stage.isShowing()) {
            throw new IllegalStateException("Stage must be showing before native window integration");
        }

        outputScaleX = validScale(stage.getOutputScaleX());
        outputScaleY = validScale(stage.getOutputScaleY());
        hwnd = findWindowHandle(stage.getTitle());
        if (hwnd == null) {
            throw new IllegalStateException("Could not locate the native window for the displayed JavaFX stage");
        }

        try {
            installNativeWindowProcedureAndStyle();
            updateNativeResizeBorders();
            stage.outputScaleXProperty().addListener(outputScaleXListener);
            stage.outputScaleYProperty().addListener(outputScaleYListener);
            stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
        } catch (RuntimeException | LinkageError exception) {
            restoreNativeWindow();
            removeStageListeners();
            hwnd = null;
            throw new IllegalStateException("Could not install native Windows window behavior", exception);
        }
    }

    /**
     * Відновлює початкові стилі Win32 та початкову процедуру вікна, якщо адаптер був установлений.
     */
    @Override
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        restoreNativeWindow();
        removeStageListeners();
        hwnd = null;
        stage = null;
        chromeView = null;
        windowProcedure = null;
        previousWindowProcedure = null;
        previousWindowStyle = 0L;
        previousWindowStyleCaptured = false;
    }

    /**
     * Установлює callback, прибирає системний заголовок і залишає нативні стилі зміни розміру та системних команд.
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

        windowProcedure = this::windowProcedureCallback;
        previousWindowProcedure = User32.INSTANCE.SetWindowLongPtr(
                hwnd,
                GWL_WNDPROC,
                CallbackReference.getFunctionPointer(windowProcedure)
        );
        if (previousWindowProcedure == null) {
            windowProcedure = null;
            throw new IllegalStateException("Could not replace the native window procedure");
        }

        previousWindowStyle = style;
        previousWindowStyleCaptured = true;
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
     * Обробляє повідомлення рамки, а решту передає попередній процедурі JavaFX Glass.
     * Помилка в додатковій обробці не повинна переривати нативний ланцюжок повідомлень.
     *
     * @param window нативний HWND джерела повідомлення.
     * @param message ідентифікатор повідомлення Win32.
     * @param wParam перший параметр повідомлення.
     * @param lParam другий параметр повідомлення.
     * @return результат Win32 або результат попередньої процедури вікна.
     */
    private LRESULT windowProcedureCallback(HWND window, int message, WPARAM wParam, LPARAM lParam) {
        try {
            if (message == WM_NCCALCSIZE) {
                return new LRESULT(0L);
            }
            if (message == WM_NCHITTEST) {
                WindowHitTestCalculator.HitTarget hitTarget = hitTestNativePoint(lParam);
                if (hitTarget != null) {
                    updateNativeHover(hitTarget.isCaptionButton() ? hitTarget : null);
                    return new LRESULT(hitTarget.nativeCode());
                }
            } else if (message == WM_NCMOUSEMOVE) {
                updateNativeHover(hitTargetFromNativeCode(wParam.intValue()));
            } else if (message == WM_NCMOUSELEAVE) {
                updateNativeHover(null);
            } else if (message == WM_MOUSEMOVE && pressedTarget.get() != null) {
                updateNativeHover(hitTestClientPoint(lParam));
            } else if (message == WM_LBUTTONUP && pressedTarget.get() != null) {
                WindowHitTestCalculator.HitTarget wasPressed = pressedTarget.getAndSet(null);
                User32WithTracking.INSTANCE.ReleaseCapture();
                updateNativePressed(null);
                WindowHitTestCalculator.HitTarget released = hitTestClientPoint(lParam);
                if (wasPressed == released) {
                    performNativeButtonAction(wasPressed);
                }
                return new LRESULT(0L);
            } else if (message == WM_NCLBUTTONDOWN) {
                WindowHitTestCalculator.HitTarget target = hitTargetFromNativeCode(wParam.intValue());
                if (target != null && target.isCaptionButton()) {
                    pressedTarget.set(target);
                    updateNativePressed(target);
                    User32WithTracking.INSTANCE.SetCapture(window);
                    return new LRESULT(0L);
                }
            } else if (message == WM_NCLBUTTONUP) {
                WindowHitTestCalculator.HitTarget wasPressed = pressedTarget.getAndSet(null);
                if (wasPressed != null) {
                    User32WithTracking.INSTANCE.ReleaseCapture();
                    updateNativePressed(null);
                    WindowHitTestCalculator.HitTarget released = hitTargetFromNativeCode(wParam.intValue());
                    if (wasPressed == released) {
                        performNativeButtonAction(wasPressed);
                    }
                    return new LRESULT(0L);
                }
            } else if (message == WM_NCLBUTTONDBLCLK
                    && wParam.intValue() == WindowHitTestCalculator.HitTarget.CAPTION.nativeCode()) {
                performCaptionDoubleClick();
                return new LRESULT(0L);
            } else if (message == WM_CAPTURECHANGED && pressedTarget.getAndSet(null) != null) {
                updateNativePressed(null);
            }
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.log(Level.WARNING, "Помилка під час обробки нативної взаємодії з вікном", exception);
        }
        return callPreviousWindowProcedure(window, message, wParam, lParam);
    }

    /**
     * Визначає HWND за назвою та PID поточного процесу, не використовуючи приватні API JavaFX.
     *
     * @param title заголовок JavaFX-вікна.
     * @return знайдений HWND або {@code null}, якщо відповідного вікна немає.
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
     * Розраховує код області нативного вікна за екранними координатами WM_NCHITTEST.
     *
     * @param lParam упаковані знакові координати курсора у фізичних пікселях екрана.
     * @return результат hit testing або {@code null}, якщо геометрію HWND прочитати не вдалося.
     */
    private WindowHitTestCalculator.HitTarget hitTestNativePoint(LPARAM lParam) {
        if (chromeView == null || hwnd == null) {
            return null;
        }
        WindowHitTestCalculator.Layout layout = chromeView.hitTestLayout();
        RECT clientRectangle = new RECT();
        POINT clientOrigin = new POINT();
        if (layout == null || !User32.INSTANCE.GetClientRect(hwnd, clientRectangle)
                || !USER32_COORDINATES.ClientToScreen(hwnd, clientOrigin)) {
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
     * Перевіряє точку в координатах клієнтської області; цей шлях потрібен, коли натиснута кнопка захопила мишу.
     *
     * @param lParam упаковані координати миші відносно клієнтської області.
     * @return кнопка або інша область під курсором; {@code null}, якщо геометрія ще не готова.
     */
    private WindowHitTestCalculator.HitTarget hitTestClientPoint(LPARAM lParam) {
        if (chromeView == null) {
            return null;
        }
        WindowHitTestCalculator.Layout layout = chromeView.hitTestLayout();
        if (layout == null) {
            return null;
        }
        long packedPoint = lParam.longValue();
        int clientX = (short) (packedPoint & 0xFFFFL);
        int clientY = (short) ((packedPoint >>> 16) & 0xFFFFL);
        return WindowHitTestCalculator.hitTest(layout, clientX / outputScaleX, clientY / outputScaleY);
    }

    /**
     * Передає зміну стану наведення у JavaFX Application Thread.
     *
     * @param target активна кнопка або {@code null}, якщо курсор залишив кнопки заголовка.
     */
    private void updateNativeHover(WindowHitTestCalculator.HitTarget target) {
        WindowChromeView view = chromeView;
        if (view == null || disposed.get()) {
            return;
        }
        Platform.runLater(() -> {
            if (!disposed.get() && chromeView == view) {
                view.setNativeHoveredControl(target);
            }
        });
    }

    /**
     * Передає зміну стану натискання у JavaFX Application Thread.
     *
     * @param target натиснута кнопка або {@code null}, якщо кнопка відпущена.
     */
    private void updateNativePressed(WindowHitTestCalculator.HitTarget target) {
        WindowChromeView view = chromeView;
        if (view == null || disposed.get()) {
            return;
        }
        Platform.runLater(() -> {
            if (!disposed.get() && chromeView == view) {
                view.setNativePressedControl(target);
            }
        });
    }

    /**
     * Виконує команду кнопки в потоці JavaFX після нативного клацання.
     *
     * @param target кнопка, на якій завершився клік.
     */
    private void performNativeButtonAction(WindowHitTestCalculator.HitTarget target) {
        WindowChromeView view = chromeView;
        if (view == null || disposed.get()) {
            return;
        }
        Platform.runLater(() -> {
            if (!disposed.get() && chromeView == view) {
                view.performNativeButtonAction(target);
            }
        });
    }

    /**
     * Виконує системоподібне перемикання максимізації після подвійного клацання по заголовку.
     */
    private void performCaptionDoubleClick() {
        WindowChromeView view = chromeView;
        if (view == null || disposed.get()) {
            return;
        }
        Platform.runLater(() -> {
            if (!disposed.get() && chromeView == view) {
                view.performNativeCaptionDoubleClick();
            }
        });
    }

    /**
     * Перетворює код Win32, отриманий для non-client-повідомлення, на типізовану область заголовка.
     *
     * @param nativeCode нативний код області.
     * @return відповідна область заголовка або {@code null}, якщо код не стосується кнопки.
     */
    private static WindowHitTestCalculator.HitTarget hitTargetFromNativeCode(int nativeCode) {
        for (WindowHitTestCalculator.HitTarget target : WindowHitTestCalculator.HitTarget.values()) {
            if (target.nativeCode() == nativeCode && target.isCaptionButton()) {
                return target;
            }
        }
        return null;
    }

    /**
     * Передає повідомлення початковій процедурі JavaFX Glass, збереженій перед під'єднанням адаптера.
     *
     * @param window HWND вікна.
     * @param message ідентифікатор Win32-повідомлення.
     * @param wParam перший параметр повідомлення.
     * @param lParam другий параметр повідомлення.
     * @return результат початкової процедури або нуль, якщо вона вже відновлюється.
     */
    private LRESULT callPreviousWindowProcedure(HWND window, int message, WPARAM wParam, LPARAM lParam) {
        Pointer previous = previousWindowProcedure;
        if (previous == null) {
            return new LRESULT(0L);
        }
        return User32.INSTANCE.CallWindowProc(previous, window, message, wParam, lParam);
    }

    /**
     * Отримує фактичну товщину нативної зони resize для поточного DPI й передає її знімку геометрії JavaFX.
     */
    private void updateNativeResizeBorders() {
        Stage currentStage = stage;
        WindowChromeView view = chromeView;
        HWND currentWindow = hwnd;
        if (currentStage == null || view == null || currentWindow == null || !Platform.isFxApplicationThread()) {
            return;
        }
        int dpi = (int) Math.round(96.0 * Math.max(outputScaleX, outputScaleY));
        try {
            int windowDpi = User32WithTracking.INSTANCE.GetDpiForWindow(currentWindow);
            if (windowDpi > 0) {
                dpi = windowDpi;
            }
        } catch (UnsatisfiedLinkError exception) {
            LOGGER.log(Level.FINE, "GetDpiForWindow недоступний; для метрик використовується масштаб JavaFX", exception);
        }
        double horizontal = systemResizeMetric(SM_CXSIZEFRAME, dpi, outputScaleX)
                + systemResizeMetric(SM_CXPADDEDBORDER, dpi, outputScaleX);
        double vertical = systemResizeMetric(SM_CYSIZEFRAME, dpi, outputScaleY)
                + systemResizeMetric(SM_CXPADDEDBORDER, dpi, outputScaleY);
        view.setNativeResizeBorders(positiveMetric(horizontal), positiveMetric(vertical));
    }

    /**
     * Зчитує одну системну метрику у фізичних пікселях і повертає її в логічних одиницях JavaFX.
     *
     * @param metric індекс системної метрики Windows.
     * @param dpi DPI поточного вікна.
     * @param scale коефіцієнт масштабування відповідної осі.
     * @return значення метрики в логічних пікселях або нуль для відсутньої метрики.
     */
    private double systemResizeMetric(int metric, int dpi, double scale) {
        try {
            return User32WithTracking.INSTANCE.GetSystemMetricsForDpi(metric, dpi) / validScale(scale);
        } catch (UnsatisfiedLinkError exception) {
            return User32.INSTANCE.GetSystemMetrics(metric) / validScale(scale);
        }
    }

    /**
     * Замінює відсутню або недодатну системну товщину безпечним типовим значенням.
     *
     * @param metric сумарна товщина resize у логічних пікселях.
     * @return додатна товщина зони resize.
     */
    private static double positiveMetric(double metric) {
        return Double.isFinite(metric) && metric > 0.0
                ? metric
                : WindowHitTestCalculator.DEFAULT_RESIZE_BORDER;
    }

    /**
     * Оновлює системний стиль та просить Windows повторно обчислити рамку вікна.
     */
    private void updateNonClientFrame() {
        User32.INSTANCE.SetWindowPos(
                hwnd,
                null,
                0,
                0,
                0,
                0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED
        );
    }

    /**
     * Відновлює попередні нативні стиль і процедуру, якщо їх уже було змінено.
     */
    private void restoreNativeWindow() {
        HWND window = hwnd;
        if (window == null) {
            return;
        }
        try {
            if (previousWindowProcedure != null) {
                User32.INSTANCE.SetWindowLongPtr(window, GWL_WNDPROC, previousWindowProcedure);
            }
            if (previousWindowStyleCaptured) {
                User32.INSTANCE.SetWindowLongPtr(window, GWL_STYLE, new Pointer(previousWindowStyle));
                updateNonClientFrame();
            }
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.log(Level.WARNING, "Не вдалося повністю відновити початковий стан вікна Windows", exception);
        }
    }

    /**
     * Від'єднує слухачі Stage, щоб закрите вікно не утримувало адаптер через властивості масштабування.
     */
    private void removeStageListeners() {
        Stage currentStage = stage;
        if (currentStage != null) {
            currentStage.outputScaleXProperty().removeListener(outputScaleXListener);
            currentStage.outputScaleYProperty().removeListener(outputScaleYListener);
            currentStage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
        }
    }

    /** Інтерфейс доступу до додаткових функцій user32, яких немає у базовому наборі JNA User32. */
    public interface User32WithTracking extends User32 {
        /** Спільний Win32-проксі з коректними параметрами виклику stdcall. */
        User32WithTracking INSTANCE = Native.load("user32", User32WithTracking.class, W32APIOptions.DEFAULT_OPTIONS);

        /**
         * Передає тимчасове захоплення миші заданому вікну, щоб отримати подію відпускання кнопки за межами кнопки.
         *
         * @param window HWND, що захоплює мишу.
         * @return попередній HWND, який мав захоплення.
         */
        HWND SetCapture(HWND window);

        /**
         * Звільняє захоплення миші після завершення нативного кліку.
         *
         * @return {@code true}, якщо захоплення було звільнено.
         */
        boolean ReleaseCapture();

        /**
         * Повертає системний коефіцієнт зміни розміру, масштабований під конкретний DPI.
         *
         * @param metric індекс SM_* метрики.
         * @param dpi DPI екрана.
         * @return значення метрики у фізичних пікселях.
         */
        int GetSystemMetricsForDpi(int metric, int dpi);

        /**
         * Повертає DPI, встановлений для HWND.
         *
         * @param window HWND вікна.
         * @return DPI вікна.
         */
        int GetDpiForWindow(HWND window);
    }

    /**
     * Перевіряє коефіцієнт масштабування екрана та замінює недопустиме значення одиницею.
     *
     * @param scale коефіцієнт JavaFX для відповідної осі.
     * @return скінченний додатний коефіцієнт.
     */
    private static double validScale(double scale) {
        return Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
    }
}
