package com.example.neuronmap.windowing;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє остаточне очищення WndProc після знищення HWND навіть за повторної помилки відновлення. */
final class WindowsWindowProcedureDestructionTest {

    private static final int WM_NCDESTROY = 0x0082;

    private WindowsWindowProcedure procedure;
    private HWND window;

    /**
     * Звільняє callback, якщо перевірка завершилася раніше за сценарій штатного знищення HWND.
     * Це запобігає витоку сильного посилання в статичному реєстрі між тестами.
     */
    @AfterEach
    void releaseRetainedProcedureAfterTest() {
        if (procedure != null && isProcedureRetained(procedure)) {
            procedure.handleMessage(window, WM_NCDESTROY, new WPARAM(0), new LPARAM(0));
        }
    }

    /**
     * Перевіряє, що WM_NCDESTROY передається попередній процедурі, а потім звільняє callback та стан адаптера.
     * Сценарій імітує відмову відновлення WndProc через SetWindowLongPtr без виклику реального user32.dll.
     */
    @Test
    void releasesCallbackAfterWindowDestructionWhenProcedureRestorationFails() {
        AtomicInteger setterCalls = new AtomicInteger();
        window = new HWND(new Pointer(0x24L));
        WindowsWindowLongPtr.Setter setter = (targetWindow, index, value) -> {
            int invocation = setterCalls.incrementAndGet();
            if (invocation == 2) {
                throw new IllegalStateException("Тестова відмова відновлення WndProc");
            }
            return new Pointer(0x42L);
        };
        RecordingNativeOperations nativeOperations = new RecordingNativeOperations();
        WindowsWindowPlatformAdapter adapter = new WindowsWindowPlatformAdapter(setter);
        procedure = new WindowsWindowProcedure(
                noOpCallbacks(),
                setter,
                nativeOperations,
                adapter::onNativeWindowDestroyed
        );
        procedure.install(window);
        writeField(adapter, "hwnd", window);
        writeField(adapter, "windowProcedure", procedure);
        writeField(adapter, "previousWindowStyleCaptured", false);

        assertFalse(adapter.disposeAndReportCompletion(),
                "Невдале відновлення WndProc має повідомити власника, що очищення ще не завершено");
        assertEquals(2, setterCalls.get(),
                "Перша спроба встановлює WndProc, друга відмовляється його відновити");
        assertTrue(isProcedureRetained(procedure),
                "Нативний callback потрібно утримувати, доки HWND ще може його викликати");

        LRESULT destructionResult = procedure.handleMessage(
                window,
                WM_NCDESTROY,
                new WPARAM(0),
                new LPARAM(0)
        );

        assertSame(nativeOperations.previousResult, destructionResult,
                "WM_NCDESTROY має бути передано початковій процедурі до звільнення callback");
        assertEquals(1, nativeOperations.forwardedCalls.get());
        assertTrue((Boolean) readField(adapter, "nativeWindowDestroyed"));
        assertFalse(isProcedureRetained(procedure),
                "Після WM_NCDESTROY сильне посилання на callback більше не потрібне");
        assertNull(readField(procedure, "windowProcedure"));
        assertNull(readField(procedure, "previousWindowProcedure"));
        assertNull(readField(procedure, "window"));

        assertTrue(adapter.disposeAndReportCompletion(),
                "Після підтвердженого знищення HWND адаптер має завершити очищення без нативних викликів");
        assertEquals(2, setterCalls.get(),
                "Після знищення HWND повторно звертатися до SetWindowLongPtr не можна");
        assertNull(readField(adapter, "hwnd"));
        assertNull(readField(adapter, "windowProcedure"));
        assertFalse((Boolean) readField(adapter, "previousWindowStyleCaptured"));
    }

    /**
     * Створює набір порожніх callback-ів для перевірки завершення життєвого циклу поза Windows.
     *
     * @return валідний набір callback-ів без побічних ефектів.
     */
    private static WindowsWindowProcedure.Callbacks noOpCallbacks() {
        return new WindowsWindowProcedure.Callbacks(
                lParam -> null,
                lParam -> null,
                target -> { },
                target -> { },
                target -> { },
                () -> { }
        );
    }

    /**
     * Перевіряє, чи зберігає активний реєстр сильне посилання на callback.
     *
     * @param procedure процедура, для якої перевіряється реєстрація.
     * @return {@code true}, якщо екземпляр є в реєстрі активних процедур.
     */
    private static boolean isProcedureRetained(WindowsWindowProcedure procedure) {
        try {
            Field field = WindowsWindowProcedure.class.getDeclaredField("ACTIVE_PROCEDURES");
            field.setAccessible(true);
            Object registry = field.get(null);
            return registry instanceof Set<?> procedures && procedures.contains(procedure);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Не вдалося перевірити реєстр активних процедур", exception);
        }
    }

    /**
     * Зчитує приватне поле компонента, щоб перевірити фактичний стан нативного ресурсу.
     *
     * @param target об'єкт, поле якого перевіряється.
     * @param fieldName назва поля.
     * @return значення поля.
     */
    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Не вдалося прочитати поле " + fieldName, exception);
        }
    }

    /**
     * Установлює початковий стан адаптера для тесту без створення справжнього JavaFX-вікна.
     *
     * @param target об'єкт, поле якого змінюється.
     * @param fieldName назва поля.
     * @param value значення для встановлення.
     */
    private static void writeField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Не вдалося встановити поле " + fieldName, exception);
        }
    }

    /** Фіксує передачу нативного повідомлення попередній процедурі без виклику функцій Win32. */
    private static final class RecordingNativeOperations implements WindowsWindowProcedure.NativeOperations {

        private final AtomicInteger forwardedCalls = new AtomicInteger();
        private final LRESULT previousResult = new LRESULT(73L);

        /** Імітує захоплення миші, яке для тесту очищення не має додаткових ефектів. */
        @Override
        public void setCapture(HWND window) {
            // Немає потреби імітувати захоплення миші в цьому тесті.
        }

        /** Імітує звільнення захоплення курсора без виклику Windows API. */
        @Override
        public void releaseCapture() {
            // Немає потреби імітувати захоплення миші в цьому тесті.
        }

        /** Імітує відмову від реєстрації відстеження, яку тест очищення не використовує. */
        @Override
        public boolean trackNonClientMouseLeave(HWND window) {
            return false;
        }

        /**
         * Записує факт передавання повідомлення та повертає контрольований результат.
         *
         * @param previousProcedure адреса початкової процедури.
         * @param sourceWindow дескриптор вікна.
         * @param message код повідомлення.
         * @param wParam перший параметр повідомлення.
         * @param lParam другий параметр повідомлення.
         * @return контрольований результат попередньої процедури.
         */
        @Override
        public LRESULT callPreviousWindowProcedure(
                Pointer previousProcedure,
                HWND sourceWindow,
                int message,
                WPARAM wParam,
                LPARAM lParam
        ) {
            forwardedCalls.incrementAndGet();
            return previousResult;
        }
    }
}
