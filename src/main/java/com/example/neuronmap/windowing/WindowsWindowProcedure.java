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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Володіє підміною нативної процедури вікна, обробкою повідомлень власного заголовка та викликом початкової процедури.
 * Поки Win32 не підтвердив від'єднання процедури, активний екземпляр утримується в реєстрі сильних посилань.
 */
final class WindowsWindowProcedure {

    private static final Logger LOGGER = Logger.getLogger(WindowsWindowProcedure.class.getName());
    private static final Set<WindowsWindowProcedure> ACTIVE_PROCEDURES = ConcurrentHashMap.newKeySet();
    private static final NativeOperations SYSTEM_NATIVE_OPERATIONS = new NativeOperations() {
        /** {@inheritDoc} */
        @Override
        public void setCapture(HWND window) {
            WindowsUser32Extensions.INSTANCE.SetCapture(window);
        }

        /** {@inheritDoc} */
        @Override
        public void releaseCapture() {
            WindowsUser32Extensions.INSTANCE.ReleaseCapture();
        }

        /** {@inheritDoc} */
        @Override
        public LRESULT callPreviousWindowProcedure(
                Pointer previousProcedure,
                HWND sourceWindow,
                int message,
                WPARAM wParam,
                LPARAM lParam
        ) {
            return User32.INSTANCE.CallWindowProc(previousProcedure, sourceWindow, message, wParam, lParam);
        }
    };

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
    private final WindowsWindowLongPtr.Setter windowLongPtrSetter;
    private final NativeOperations nativeOperations;
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicReference<HitTarget> pressedTarget = new AtomicReference<>();
    private HWND window;
    private Pointer previousWindowProcedure;
    private WinUser.WindowProc windowProcedure;
    private boolean windowProcedureInstalled;
    private boolean installationAttempted;

    /**
     * Створює обробник повідомлень із системними операціями Win32 та функціями роботи із заголовком.
     *
     * @param callbacks зворотні виклики для визначення областей і передавання UI-команд.
     * @throws NullPointerException якщо набір зворотних викликів не задано.
     */
    WindowsWindowProcedure(Callbacks callbacks) {
        this(callbacks, WindowsWindowLongPtr::set, SYSTEM_NATIVE_OPERATIONS);
    }

    /**
     * Створює обробник із заданим механізмом зміни атрибутів вікна та системними операціями Win32.
     * Конструктор з ін'єкцією setter дає змогу перевіряти помилки встановлення й відновлення без Windows.
     *
     * @param callbacks зворотні виклики для визначення областей і передавання UI-команд.
     * @param windowLongPtrSetter перевірений механізм зміни атрибута Win32.
     * @throws NullPointerException якщо будь-яку залежність не задано.
     */
    WindowsWindowProcedure(Callbacks callbacks, WindowsWindowLongPtr.Setter windowLongPtrSetter) {
        this(callbacks, windowLongPtrSetter, SYSTEM_NATIVE_OPERATIONS);
    }

    /**
     * Створює обробник із замінними нативними операціями для перевірки маршрутизації повідомлень без user32.dll.
     *
     * @param callbacks зворотні виклики для визначення областей і передавання UI-команд.
     * @param windowLongPtrSetter механізм установлення та відновлення адреси WndProc.
     * @param nativeOperations операції захоплення миші й передавання повідомлення попередній процедурі.
     * @throws NullPointerException якщо будь-яку залежність не задано.
     */
    WindowsWindowProcedure(
            Callbacks callbacks,
            WindowsWindowLongPtr.Setter windowLongPtrSetter,
            NativeOperations nativeOperations
    ) {
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
        this.windowLongPtrSetter = Objects.requireNonNull(windowLongPtrSetter, "windowLongPtrSetter");
        this.nativeOperations = Objects.requireNonNull(nativeOperations, "nativeOperations");
    }

    /**
     * Підміняє процедуру заданого вікна й зберігає адресу попередньої процедури для подальшого відновлення.
     * Екземпляр одноразовий: після спроби встановлення він не може повторно підключатися до іншого HWND.
     *
     * @param window дескриптор вікна Windows.
     * @throws IllegalStateException якщо встановлення вже починалося або попередня процедура має нульову адресу.
     * @throws NullPointerException якщо дескриптор вікна не задано.
     */
    synchronized void install(HWND window) {
        if (installationAttempted || disposed.get()) {
            throw new IllegalStateException("Native window procedure can only be installed once");
        }
        installationAttempted = true;
        this.window = Objects.requireNonNull(window, "window");
        windowProcedure = this::handleMessage;
        try {
            previousWindowProcedure = windowLongPtrSetter.set(
                    window,
                    GWL_WNDPROC,
                    CallbackReference.getFunctionPointer(windowProcedure)
            );
            windowProcedureInstalled = true;
            ACTIVE_PROCEDURES.add(this);
            if (previousWindowProcedure == null) {
                throw new IllegalStateException("The previous native window procedure has an empty address");
            }
        } catch (RuntimeException | LinkageError exception) {
            if (!windowProcedureInstalled) {
                disposed.set(true);
                this.window = null;
                windowProcedure = null;
                previousWindowProcedure = null;
            }
            throw exception;
        }
    }

    /**
     * Відновлює початкову процедуру та звільняє callback лише після успішного завершення виклику Win32.
     * У разі помилки зберігає всі потрібні посилання й дозволяє повторити спробу очищення.
     *
     * @return {@code true}, якщо callback гарантовано від'єднано; {@code false}, якщо його потрібно утримувати.
     */
    synchronized boolean dispose() {
        if (windowProcedure == null) {
            return true;
        }
        disposed.set(true);
        pressedTarget.set(null);
        if (windowProcedureInstalled) {
            HWND currentWindow = window;
            if (currentWindow == null) {
                LOGGER.warning("Неможливо відновити процедуру вікна Windows: дескриптор уже відсутній; callback утримується");
                return false;
            }
            try {
                windowLongPtrSetter.set(currentWindow, GWL_WNDPROC, previousWindowProcedure);
            } catch (RuntimeException | LinkageError exception) {
                LOGGER.log(Level.WARNING,
                        "Не вдалося відновити початкову процедуру вікна Windows; callback залишається активним",
                        exception);
                return false;
            }
        }
        ACTIVE_PROCEDURES.remove(this);
        windowProcedureInstalled = false;
        previousWindowProcedure = null;
        windowProcedure = null;
        window = null;
        return true;
    }

    /**
     * Обробляє повідомлення власного заголовка або передає його початковій процедурі JavaFX Glass.
     * Помилка додаткової обробки не повинна переривати нативний ланцюжок повідомлень.
     * Метод доступний у межах пакета, щоб перевіряти маршрутизацію без створення реального Windows-вікна.
     *
     * @param sourceWindow HWND, від якого надійшло повідомлення.
     * @param message ідентифікатор повідомлення Win32.
     * @param wParam перший параметр повідомлення.
     * @param lParam другий параметр повідомлення.
     * @return результат власної обробки або результат попередньої процедури.
     */
    LRESULT handleMessage(HWND sourceWindow, int message, WPARAM wParam, LPARAM lParam) {
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
            nativeOperations.releaseCapture();
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
                nativeOperations.setCapture(sourceWindow);
                return new LRESULT(0L);
            }
        } else if (message == WM_NCLBUTTONUP) {
            HitTarget wasPressed = pressedTarget.getAndSet(null);
            if (wasPressed != null) {
                nativeOperations.releaseCapture();
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
        return nativeOperations.callPreviousWindowProcedure(previous, sourceWindow, message, wParam, lParam);
    }

    /** Описує доступ до системних операцій, що залишають маршрутизацію повідомлень незалежною від Win32. */
    interface NativeOperations {

        /**
         * Передає захоплення миші вікну до завершення натискання кнопки заголовка.
         *
         * @param window HWND, якому передається захоплення.
         */
        void setCapture(HWND window);

        /** Звільняє захоплення миші після завершення або скасування натискання кнопки заголовка. */
        void releaseCapture();

        /**
         * Передає повідомлення збереженій нативній процедурі вікна.
         *
         * @param previousProcedure адреса попередньої процедури, збережена під час встановлення.
         * @param sourceWindow HWND, від якого надійшло повідомлення.
         * @param message ідентифікатор повідомлення Win32.
         * @param wParam перший параметр повідомлення.
         * @param lParam другий параметр повідомлення.
         * @return результат попередньої процедури.
         */
        LRESULT callPreviousWindowProcedure(
                Pointer previousProcedure,
                HWND sourceWindow,
                int message,
                WPARAM wParam,
                LPARAM lParam
        );
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
