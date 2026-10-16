package com.example.neuronmap.windowing;

import com.example.neuronmap.window.WindowHitTestCalculator.HitTarget;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє придушення системного малювання рамки без виклику справжньої процедури Windows. */
final class WindowsWindowProcedureNonClientFrameTest {

    private static final int WM_NCCALCSIZE = 0x0083;
    private static final int WM_NCPAINT = 0x0085;
    private static final int WM_NCACTIVATE = 0x0086;

    private TestHarness harness;

    /** Звільняє тестову процедуру після кожної перевірки. */
    @AfterEach
    void disposeProcedure() {
        if (harness != null) {
            assertTrue(harness.procedure.dispose());
        }
    }

    /** Перевіряє, що обчислення клієнтської області повертає нуль і не передає повідомлення далі. */
    @Test
    void nonClientCalculationUsesTheWholeWindowAsClientArea() {
        harness = new TestHarness();

        assertEquals(0, harness.send(WM_NCCALCSIZE, 1, 0L).intValue());
        assertEquals(0, harness.nativeOperations.forwardedCalls);
    }

    /** Перевіряє, що стандартне малювання non-client рамки пригнічується. */
    @Test
    void nonClientPaintingIsSuppressed() {
        harness = new TestHarness();

        assertEquals(0, harness.send(WM_NCPAINT, 1, 0L).intValue());
        assertEquals(0, harness.nativeOperations.forwardedCalls);
    }

    /** Перевіряє, що активація делегується попередній процедурі з lParam=-1 для придушення перерисовування рамки. */
    @Test
    void nonClientActivationDoesNotRequestFrameRedraw() {
        harness = new TestHarness();

        LRESULT result = harness.send(WM_NCACTIVATE, 1, 37L);

        assertSame(harness.nativeOperations.previousResult, result);
        assertEquals(1, harness.nativeOperations.forwardedCalls);
        assertEquals(-1L, harness.nativeOperations.forwardedLParam.longValue());
    }

    /** Готує нативну процедуру з контрольованим приймачем повідомлень. */
    private static final class TestHarness {
        private final HWND window = new HWND(new Pointer(0x24L));
        private final RecordingNativeOperations nativeOperations = new RecordingNativeOperations();
        private final WindowsWindowProcedure procedure = new WindowsWindowProcedure(
                new WindowsWindowProcedure.Callbacks(
                        ignored -> HitTarget.CLIENT,
                        ignored -> HitTarget.CLIENT,
                        ignored -> { },
                        ignored -> { },
                        ignored -> { },
                        () -> { }
                ),
                (targetWindow, index, value) -> new Pointer(0x42L),
                nativeOperations
        );

        /** Створює процедуру та встановлює її на тестовий HWND без завантаження системної бібліотеки. */
        private TestHarness() {
            procedure.install(window);
        }

        /**
         * Передає повідомлення процедурі з типізованими Win32-параметрами.
         *
         * @param message ідентифікатор повідомлення.
         * @param wParam перший параметр повідомлення.
         * @param lParam другий параметр повідомлення.
         * @return результат обробки повідомлення.
         */
        private LRESULT send(int message, int wParam, long lParam) {
            return procedure.handleMessage(window, message, new WPARAM(wParam), new LPARAM(lParam));
        }
    }

    /** Зберігає параметри передачі попередній процедурі, щоб перевірити зміну lParam. */
    private static final class RecordingNativeOperations implements WindowsWindowProcedure.NativeOperations {
        private final LRESULT previousResult = new LRESULT(73L);
        private int forwardedCalls;
        private LPARAM forwardedLParam;

        /** Не використовується цими тестами, але реалізує контракт захоплення миші. */
        @Override
        public void setCapture(HWND window) {
        }

        /** Не використовується цими тестами, але реалізує контракт звільнення захоплення миші. */
        @Override
        public void releaseCapture() {
        }

        /** Не використовується цими тестами, але реалізує контракт відстеження non-client курсора. */
        @Override
        public boolean trackNonClientMouseLeave(HWND window) {
            return true;
        }

        /** Фіксує параметри повідомлення та повертає детермінований результат. */
        @Override
        public LRESULT callPreviousWindowProcedure(
                Pointer previousProcedure,
                HWND sourceWindow,
                int message,
                WPARAM wParam,
                LPARAM lParam
        ) {
            forwardedCalls++;
            forwardedLParam = lParam;
            return previousResult;
        }
    }
}
