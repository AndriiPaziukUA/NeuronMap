package com.example.neuronmap.windowing;

import com.example.neuronmap.window.WindowHitTestCalculator.HitTarget;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє маршрутизацію повідомлень нативної процедури без виклику Windows API. */
final class WindowsWindowProcedureRoutingTest {

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
    private static final int WM_APP_TEST = 0x8001;

    private TestHarness harness;

    /** Звільняє тестовий callback після кожного сценарію, прибираючи його з реєстру активних процедур. */
    @AfterEach
    void disposeProcedure() {
        if (harness != null) {
            assertTrue(harness.procedure.dispose());
        }
    }

    /** Перевіряє, що hit-test повертає код Win32 і позначає кнопку заголовка як активну для наведення. */
    @Test
    void hitTestReturnsNativeCodeAndUpdatesHoveredButton() {
        harness = new TestHarness();
        harness.nativeTarget = HitTarget.MAXIMIZE_BUTTON;

        LRESULT result = harness.send(WM_NCHITTEST, 0, 0);

        assertEquals(9, result.intValue());
        assertSame(HitTarget.MAXIMIZE_BUTTON, harness.hoveredTargets.getLast());
        assertEquals(0, harness.nativeOperations.forwardedCalls.get());
    }

    /** Перевіряє, що розрахунок non-client області перехоплюється без передавання повідомлення JavaFX. */
    @Test
    void nonClientAreaCalculationIsConsumed() {
        harness = new TestHarness();

        assertEquals(0, harness.send(WM_NCCALCSIZE, 0, 0).intValue());
        assertEquals(0, harness.nativeOperations.forwardedCalls.get());
    }

    /** Перевіряє передавання WM_NCHITTEST далі, якщо обчислювач не визначив семантичну область. */
    @Test
    void hitTestWithoutTargetFallsBackToPreviousProcedure() {
        harness = new TestHarness();
        harness.nativeTarget = null;

        LRESULT result = harness.send(WM_NCHITTEST, 0, 0);

        assertSame(harness.nativeOperations.previousResult, result);
        assertEquals(1, harness.nativeOperations.forwardedCalls.get());
    }

    /** Перевіряє оновлення наведення для non-client повідомлень і скидання стану під час виходу курсора. */
    @Test
    void nonClientMouseMessagesUpdateAndClearHover() {
        harness = new TestHarness();

        harness.send(WM_NCMOUSEMOVE, 20, 0);
        assertSame(HitTarget.CLOSE_BUTTON, harness.hoveredTargets.getLast());

        harness.send(WM_NCMOUSELEAVE, 0, 0);
        assertNull(harness.hoveredTargets.getLast());
    }

    /** Перевіряє захоплення миші на натисканні кнопки та команду лише після відпускання тієї самої кнопки. */
    @Test
    void matchingNonClientButtonClickCapturesAndReleasesMouse() {
        harness = new TestHarness();

        assertEquals(0, harness.send(WM_NCLBUTTONDOWN, 8, 0).intValue());
        assertEquals(1, harness.nativeOperations.captureCalls.get());
        assertSame(harness.window, harness.nativeOperations.capturedWindow);
        assertSame(HitTarget.MINIMIZE_BUTTON, harness.pressedTargets.getLast());

        assertEquals(0, harness.send(WM_NCLBUTTONUP, 8, 0).intValue());
        assertEquals(1, harness.nativeOperations.releaseCalls.get());
        assertEquals(2, harness.pressedTargets.size());
        assertNull(harness.pressedTargets.getLast());
        assertSame(HitTarget.MINIMIZE_BUTTON, harness.actions.getFirst());
        assertEquals(1, harness.actions.size());
        assertEquals(0, harness.nativeOperations.forwardedCalls.get());
    }

    /** Перевіряє, що відпускання захопленої кнопки над іншою областю завершує натискання без виконання команди. */
    @Test
    void clientButtonReleaseOutsidePressedTargetDoesNotExecuteAction() {
        harness = new TestHarness();
        harness.send(WM_NCLBUTTONDOWN, 8, 0);
        harness.clientTarget = HitTarget.CLOSE_BUTTON;

        assertEquals(0, harness.send(WM_LBUTTONUP, 0, 0).intValue());

        assertEquals(1, harness.nativeOperations.releaseCalls.get());
        assertEquals(0, harness.actions.size());
        assertEquals(2, harness.pressedTargets.size());
        assertNull(harness.pressedTargets.getLast());
    }

    /** Перевіряє, що рух миші під час захоплення маршрутизує наведення за координатами клієнтської області. */
    @Test
    void mouseMoveDuringCaptureUpdatesClientHover() {
        harness = new TestHarness();
        harness.send(WM_NCLBUTTONDOWN, 8, 0);
        harness.clientTarget = HitTarget.CLOSE_BUTTON;

        harness.send(WM_MOUSEMOVE, 0, 0);

        assertSame(HitTarget.CLOSE_BUTTON, harness.hoveredTargets.getLast());
    }

    /** Перевіряє скасування натискання при втраті захоплення без запуску команди кнопки. */
    @Test
    void captureChangedClearsPressedStateWithoutExecutingAction() {
        harness = new TestHarness();
        harness.send(WM_NCLBUTTONDOWN, 8, 0);

        harness.send(WM_CAPTURECHANGED, 0, 0);

        assertNull(harness.pressedTargets.getLast());
        assertEquals(0, harness.actions.size());
        assertEquals(0, harness.nativeOperations.releaseCalls.get());
    }

    /** Перевіряє, що подвійне клацання по області заголовка викликає відповідну команду рівно один раз. */
    @Test
    void captionDoubleClickInvokesCaptionAction() {
        harness = new TestHarness();

        assertEquals(0, harness.send(WM_NCLBUTTONDBLCLK, 2, 0).intValue());

        assertEquals(1, harness.doubleClickCalls.get());
        assertEquals(0, harness.nativeOperations.forwardedCalls.get());
    }

    /** Перевіряє збереження параметрів невідомого повідомлення та результату попередньої процедури. */
    @Test
    void unknownMessageIsForwardedToPreviousProcedure() {
        harness = new TestHarness();
        WPARAM wParam = new WPARAM(17L);
        LPARAM lParam = new LPARAM(29L);

        LRESULT result = harness.procedure.handleMessage(harness.window, WM_APP_TEST, wParam, lParam);

        assertSame(harness.nativeOperations.previousResult, result);
        assertEquals(1, harness.nativeOperations.forwardedCalls.get());
        assertSame(harness.window, harness.nativeOperations.forwardedWindow);
        assertEquals(WM_APP_TEST, harness.nativeOperations.forwardedMessage);
        assertSame(wParam, harness.nativeOperations.forwardedWParam);
        assertSame(lParam, harness.nativeOperations.forwardedLParam);
        assertEquals(0x42L, Pointer.nativeValue(harness.nativeOperations.forwardedProcedure));
    }

    /** Перевіряє, що помилка callback-а hit-test не перериває ланцюжок і повідомлення отримує попередня процедура. */
    @Test
    void hitTestCallbackFailureFallsBackToPreviousProcedure() {
        harness = new TestHarness();
        harness.failNativeHitTest = true;

        LRESULT result = harness.send(WM_NCHITTEST, 0, 0);

        assertSame(harness.nativeOperations.previousResult, result);
        assertEquals(1, harness.nativeOperations.forwardedCalls.get());
        assertEquals(WM_NCHITTEST, harness.nativeOperations.forwardedMessage);
    }

    /** Перевіряє, що після невдалого відновлення WndProc утриманий callback передає повідомлення старій процедурі. */
    @Test
    void failedRestorationKeepsCallbackForwardingMessagesToPreviousProcedure() {
        harness = new TestHarness(true);

        assertFalse(harness.procedure.dispose());
        harness.nativeTarget = HitTarget.MAXIMIZE_BUTTON;

        LRESULT result = harness.send(WM_NCHITTEST, 0, 0);

        assertSame(harness.nativeOperations.previousResult, result);
        assertEquals(1, harness.nativeOperations.forwardedCalls.get());
        assertEquals(0, harness.hoveredTargets.size(), "Після невдалого відновлення нова логіка не повинна виконуватися");
    }

    /** Готує маршрутизатор із тестовими callback-ами й підмінними нативними операціями. */
    private static final class TestHarness {

        private final HWND window = new HWND(new Pointer(0x24L));
        private final RecordingNativeOperations nativeOperations = new RecordingNativeOperations();
        private final List<HitTarget> hoveredTargets = new ArrayList<>();
        private final List<HitTarget> pressedTargets = new ArrayList<>();
        private final List<HitTarget> actions = new ArrayList<>();
        private final AtomicInteger doubleClickCalls = new AtomicInteger();
        private HitTarget nativeTarget = HitTarget.CAPTION;
        private HitTarget clientTarget = HitTarget.MINIMIZE_BUTTON;
        private boolean failNativeHitTest;
        private final WindowsWindowProcedure procedure;

        /** Створює та встановлює тестову процедуру, не завантажуючи системні бібліотеки Windows. */
        private TestHarness() {
            this(false);
        }

        /**
         * Створює тестову процедуру та за потреби імітує одноразову помилку відновлення WndProc.
         *
         * @param failRestorationOnce чи потрібно відхилити першу спробу відновлення.
         */
        private TestHarness(boolean failRestorationOnce) {
            AtomicInteger setterCalls = new AtomicInteger();
            WindowsWindowProcedure.Callbacks callbacks = new WindowsWindowProcedure.Callbacks(
                    lParam -> {
                        if (failNativeHitTest) {
                            throw new IllegalStateException("Тестова помилка hit-test");
                        }
                        return nativeTarget;
                    },
                    lParam -> clientTarget,
                    hoveredTargets::add,
                    pressedTargets::add,
                    actions::add,
                    doubleClickCalls::incrementAndGet
            );
            procedure = new WindowsWindowProcedure(
                    callbacks,
                    (targetWindow, index, value) -> {
                        int invocation = setterCalls.incrementAndGet();
                        if (failRestorationOnce && invocation == 2) {
                            throw new IllegalStateException("Тестова помилка відновлення WndProc");
                        }
                        return new Pointer(0x42L);
                    },
                    nativeOperations
            );
            procedure.install(window);
        }

        /** Передає повідомлення процедурі з типізованими параметрами Win32. */
        private LRESULT send(int message, int wParam, long lParam) {
            return procedure.handleMessage(window, message, new WPARAM(wParam), new LPARAM(lParam));
        }
    }

    /** Фіксує межові нативні виклики, щоб тестувати маршрутизацію без виклику user32.dll. */
    private static final class RecordingNativeOperations implements WindowsWindowProcedure.NativeOperations {

        private final AtomicInteger captureCalls = new AtomicInteger();
        private final AtomicInteger releaseCalls = new AtomicInteger();
        private final AtomicInteger forwardedCalls = new AtomicInteger();
        private final LRESULT previousResult = new LRESULT(73L);
        private HWND capturedWindow;
        private Pointer forwardedProcedure;
        private HWND forwardedWindow;
        private int forwardedMessage;
        private WPARAM forwardedWParam;
        private LPARAM forwardedLParam;

        /** Реєструє вікно, якому процедура передала захоплення миші. */
        @Override
        public void setCapture(HWND window) {
            captureCalls.incrementAndGet();
            capturedWindow = window;
        }

        /** Рахує спроби звільнити захоплення миші. */
        @Override
        public void releaseCapture() {
            releaseCalls.incrementAndGet();
        }

        /** Запам'ятовує повідомлення й повертає контрольований результат попередньої процедури. */
        @Override
        public LRESULT callPreviousWindowProcedure(
                Pointer previousProcedure,
                HWND sourceWindow,
                int message,
                WPARAM wParam,
                LPARAM lParam
        ) {
            forwardedCalls.incrementAndGet();
            forwardedProcedure = previousProcedure;
            forwardedWindow = sourceWindow;
            forwardedMessage = message;
            forwardedWParam = wParam;
            forwardedLParam = lParam;
            return previousResult;
        }
    }
}
