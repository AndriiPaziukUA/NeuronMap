package com.example.neuronmap.windowing;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinDef.HWND;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє обробку результатів SetWindowLongPtr та безпечне очищення нативних ресурсів. */
final class WindowsWindowRestorationTest {

    /** Перевіряє, що нульовий результат нативного виклику оцінюється разом із кодом Win32. */
    @Test
    void validatesSetWindowLongPtrResultUsingLastError() {
        Pointer previousValue = new Pointer(0x42L);

        assertNull(WindowsWindowLongPtr.validateResult(null, 0));
        assertSame(previousValue, WindowsWindowLongPtr.validateResult(previousValue, 5));
        assertThrows(Win32Exception.class, () -> WindowsWindowLongPtr.validateResult(null, 5));
    }

    /** Перевіряє, що невдале відновлення процедури зберігає callback і дозволяє повторити очищення. */
    @Test
    void retainsNativeCallbackAfterFailedProcedureRestoration() {
        AtomicInteger calls = new AtomicInteger();
        WindowsWindowLongPtr.Setter setter = (window, index, value) -> {
            if (calls.incrementAndGet() == 2) {
                throw new Win32Exception(5);
            }
            return new Pointer(0x42L);
        };
        WindowsWindowProcedure procedure = new WindowsWindowProcedure(noOpCallbacks(), setter);
        HWND window = new HWND(new Pointer(0x24L));
        procedure.install(window);

        assertFalse(procedure.dispose());
        assertNotNull(readField(procedure, "windowProcedure"));
        assertNotNull(readField(procedure, "previousWindowProcedure"));
        assertSame(window, readField(procedure, "window"));
        assertTrue(isProcedureRetained(procedure), "Активний нативний callback має залишатися в сильному реєстрі");

        assertTrue(procedure.dispose());
        assertFalse(isProcedureRetained(procedure));
        assertNull(readField(procedure, "windowProcedure"));
        assertNull(readField(procedure, "previousWindowProcedure"));
        assertNull(readField(procedure, "window"));
        assertEquals(3, calls.get(), "Повторне очищення має повторно звернутися до Win32");
    }

    /** Перевіряє, що адаптер не втрачає стан попереднього стилю, якщо Win32 відмовляється його відновити. */
    @Test
    void retainsPreviousWindowStyleAfterFailedRestoration() {
        AtomicInteger calls = new AtomicInteger();
        WindowsWindowPlatformAdapter adapter = new WindowsWindowPlatformAdapter((window, index, value) -> {
            calls.incrementAndGet();
            throw new Win32Exception(5);
        });
        HWND window = new HWND(new Pointer(0x24L));
        writeField(adapter, "hwnd", window);
        writeField(adapter, "previousWindowStyle", 0x00CF0000L);
        writeField(adapter, "previousWindowStyleCaptured", true);

        adapter.dispose();

        assertEquals(1, calls.get());
        assertSame(window, readField(adapter, "hwnd"));
        assertTrue((Boolean) readField(adapter, "previousWindowStyleCaptured"));

        adapter.dispose();

        assertEquals(2, calls.get(), "Адаптер має дозволяти повторну спробу відновлення стилю");
        assertSame(window, readField(adapter, "hwnd"));
    }

    /** Створює набір порожніх callback-ів для тесту життєвого циклу без JavaFX та Win32. */
    private WindowsWindowProcedure.Callbacks noOpCallbacks() {
        return new WindowsWindowProcedure.Callbacks(
                lParam -> null,
                lParam -> null,
                target -> { },
                target -> { },
                target -> { },
                () -> { }
        );
    }


    /** Перевіряє, чи зберігає реєстр активних нативних процедур сильне посилання на заданий екземпляр. */
    private boolean isProcedureRetained(WindowsWindowProcedure procedure) {
        try {
            Field field = WindowsWindowProcedure.class.getDeclaredField("ACTIVE_PROCEDURES");
            field.setAccessible(true);
            Object registry = field.get(null);
            return registry instanceof Set<?> procedures && procedures.contains(procedure);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Не вдалося перевірити реєстр нативних процедур", exception);
        }
    }

    /** Зчитує приватний стан компонента, щоб перевірити збереження ресурсів після помилки очищення. */
    private Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Не вдалося прочитати поле " + fieldName, exception);
        }
    }

    /** Встановлює початковий стан нативного ресурсу для тесту без створення справжнього Windows-вікна. */
    private void writeField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Не вдалося встановити поле " + fieldName, exception);
        }
    }
}
