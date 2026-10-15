package com.example.neuronmap.windowing;

import com.example.neuronmap.window.WindowHitTestCalculator.HitTarget;
import com.sun.jna.CallbackReference;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Володіє підміною нативної процедури вікна, обробкою повідомлень власного заголовка та викликом початкової процедури.
 * Геометрію та команди JavaFX отримує через зворотні виклики, не залежачи від контролера чи конкретного подання.
 */
final class WindowsWindowProcedure {

    private static final Logger LOGGER = Logger.getLogger(WindowsWindowProcedure.class.getName());
    private static final int GWL_WNDPROC = -4;
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

    private final Callbacks callbacks;
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicReference<HitTarget> pressedTarget = new AtomicReference<>();
    private HWND window;
    private Pointer previousWindowProcedure;
    private WinUser.WindowProc windowProcedure;
    private boolean installationAttempted;

    /**
     * Створює обробник повідомлень із функціями взаємодії з геометрією та поданням заголовка.
     *
     * @param callbacks зворотні виклики для визначення областей і передавання UI-команд.
     * @throws NullPointerException якщо набір зворотних викликів не задано.
     */
    WindowsWindowProcedure(Callbacks callbacks) {
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
    }

    /**
     * Підміняє процедуру заданого вікна й зберігає адресу попередньої процедури для подальшого відновлення.
     * Екземпляр одноразовий: після спроби встановлення він не може повторно підключатися до іншого HWND.
     *
     * @param window дескриптор вікна Windows.
     * @throws IllegalStateException якщо встановлення вже починалося або попередню процедуру неможливо зберегти.
     * @throws NullPointerException якщо дескриптор вікна не задано.
     */
    void install(HWND window) {
        if (installationAttempted || disposed.get()) {
            throw new IllegalStateException("Native window procedure can only be installed once");
        }
        installationAttempted = true;
        this.window = Objects.requireNonNull(window, "window");
        windowProcedure = this::handleMessage;
        try {
            previousWindowProcedure = User32.INSTANCE.SetWindowLongPtr(
                    window,
                    GWL_WNDPROC,
                    CallbackReference.getFunctionPointer(windowProcedure)
            );
            if (previousWindowProcedure == null) {
                throw new IllegalStateException("Could not replace the native window procedure");
            }
        } catch (RuntimeException | LinkageError exception) {
            disposed.set(true);
            this.window = null;
            windowProcedure = null;
            throw exception;
        }
    }

    /** Відновлює початкову процедуру вікна та звільняє посилання на нативний callback. */
    void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        HWND currentWindow = window;
        Pointer previousProcedure = previousWindowProcedure;
        try {
            if (currentWindow != null && previousProcedure != null) {
                User32.INSTANCE.SetWindowLongPtr(currentWindow, GWL_WNDPROC, previousProcedure);
            }
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.log(Level.WARNING, "Не вдалося відновити початкову процедуру вікна Windows", exception);
        } finally {
            pressedTarget.set(null);
            previousWindowProcedure = null;
            windowProcedure = null;
            window = null;
        }
    }

    /**
     * Обробляє повідомлення власного заголовка або передає його початковій процедурі JavaFX Glass.
     * Помилка додаткової обробки не повинна переривати нативний ланцюжок повідомлень.
     *
     * @param sourceWindow HWND, від якого надійшло повідомлення.
     * @param message ідентифікатор повідомлення Win32.
     * @param wParam перший параметр повідомлення.
     * @param lParam другий параметр повідомлення.
     * @return результат власної обробки або результат попередньої процедури.
     */
    private LRESULT handleMessage(HWND sourceWindow, int message, WPARAM wParam, LPARAM lParam) {
        if (disposed.get()) {
            return callPreviousWindowProcedure(sourceWindow, message, wParam, lParam);
        }
        try {
            LRESULT result = handleChromeMessage(sourceWindow, message, wParam, lParam);
            if (result != null) {
                return result;
            }
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.log(Level.WARNING, "Помилка під час обробки нативної взаємодії з вікном", exception);
        }
        return callPreviousWindowProcedure(sourceWindow, message, wParam, lParam);
    }

    /**
     * Обробляє лише повідомлення, поведінка яких належить власному заголовку.
     *
     * @param sourceWindow HWND, від якого надійшло повідомлення.
     * @param message ідентифікатор повідомлення Win32.
     * @param wParam перший параметр повідомлення.
     * @param lParam другий параметр повідомлення.
     * @return результат обробки або {@code null}, щоб передати повідомлення попередній процедурі.
     */
    private LRESULT handleChromeMessage(HWND sourceWindow, int message, WPARAM wParam, LPARAM lParam) {
        if (message == WM_NCCALCSIZE) {
            return new LRESULT(0L);
        }
        if (message == WM_NCHITTEST) {
            HitTarget target = callbacks.nativeHitTester().apply(lParam);
            if (target != null) {
                callbacks.hoverUpdater().accept(target.isCaptionButton() ? target : null);
                return new LRESULT(Win32HitTestCode.toNativeCode(target));
            }
        } else if (message == WM_NCMOUSEMOVE) {
            callbacks.hoverUpdater().accept(Win32HitTestCode.captionButtonFromNativeCode(wParam.intValue()));
        } else if (message == WM_NCMOUSELEAVE) {
            callbacks.hoverUpdater().accept(null);
        } else if (message == WM_MOUSEMOVE && pressedTarget.get() != null) {
            callbacks.hoverUpdater().accept(callbacks.clientHitTester().apply(lParam));
        } else if (message == WM_LBUTTONUP && pressedTarget.get() != null) {
            HitTarget wasPressed = pressedTarget.getAndSet(null);
            WindowsUser32Extensions.INSTANCE.ReleaseCapture();
            callbacks.pressedUpdater().accept(null);
            if (wasPressed == callbacks.clientHitTester().apply(lParam)) {
                callbacks.buttonAction().accept(wasPressed);
            }
            return new LRESULT(0L);
        } else if (message == WM_NCLBUTTONDOWN) {
            HitTarget target = Win32HitTestCode.captionButtonFromNativeCode(wParam.intValue());
            if (target != null) {
                pressedTarget.set(target);
                callbacks.pressedUpdater().accept(target);
                WindowsUser32Extensions.INSTANCE.SetCapture(sourceWindow);
                return new LRESULT(0L);
            }
        } else if (message == WM_NCLBUTTONUP) {
            HitTarget wasPressed = pressedTarget.getAndSet(null);
            if (wasPressed != null) {
                WindowsUser32Extensions.INSTANCE.ReleaseCapture();
                callbacks.pressedUpdater().accept(null);
                if (wasPressed == Win32HitTestCode.captionButtonFromNativeCode(wParam.intValue())) {
                    callbacks.buttonAction().accept(wasPressed);
                }
                return new LRESULT(0L);
            }
        } else if (message == WM_NCLBUTTONDBLCLK
                && wParam.intValue() == Win32HitTestCode.toNativeCode(HitTarget.CAPTION)) {
            callbacks.captionDoubleClick().run();
            return new LRESULT(0L);
        } else if (message == WM_CAPTURECHANGED && pressedTarget.getAndSet(null) != null) {
            callbacks.pressedUpdater().accept(null);
        }
        return null;
    }

    /**
     * Передає невідоме повідомлення попередній процедурі вікна.
     *
     * @param sourceWindow HWND, від якого надійшло повідомлення.
     * @param message ідентифікатор повідомлення Win32.
     * @param wParam перший параметр повідомлення.
     * @param lParam другий параметр повідомлення.
     * @return результат початкової процедури або нуль, якщо вона недоступна.
     */
    private LRESULT callPreviousWindowProcedure(HWND sourceWindow, int message, WPARAM wParam, LPARAM lParam) {
        Pointer previous = previousWindowProcedure;
        if (previous == null) {
            return new LRESULT(0L);
        }
        return User32.INSTANCE.CallWindowProc(previous, sourceWindow, message, wParam, lParam);
    }

    /** Набір функцій, якими процедура спілкується з координатором платформного адаптера. */
    record Callbacks(
            Function<LPARAM, HitTarget> nativeHitTester,
            Function<LPARAM, HitTarget> clientHitTester,
            Consumer<HitTarget> hoverUpdater,
            Consumer<HitTarget> pressedUpdater,
            Consumer<HitTarget> buttonAction,
            Runnable captionDoubleClick
    ) {

        /**
         * Перевіряє наявність усіх необхідних функцій взаємодії.
         *
         * @param nativeHitTester визначає інтерактивну область за екранними координатами.
         * @param clientHitTester визначає інтерактивну область за координатами клієнтської області.
         * @param hoverUpdater передає зміну наведення на кнопку.
         * @param pressedUpdater передає зміну стану натискання кнопки.
         * @param buttonAction виконує команду кнопки заголовка.
         * @param captionDoubleClick перемикає максимізацію за подвійним клацанням заголовка.
         * @throws NullPointerException якщо будь-який зворотний виклик не задано.
         */
        Callbacks {
            Objects.requireNonNull(nativeHitTester, "nativeHitTester");
            Objects.requireNonNull(clientHitTester, "clientHitTester");
            Objects.requireNonNull(hoverUpdater, "hoverUpdater");
            Objects.requireNonNull(pressedUpdater, "pressedUpdater");
            Objects.requireNonNull(buttonAction, "buttonAction");
            Objects.requireNonNull(captionDoubleClick, "captionDoubleClick");
        }
    }
}
