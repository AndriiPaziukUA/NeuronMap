package com.example.neuronmap.windowing;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє архітектурний контракт єдиного оголошення додаткових функцій user32.dll. */
class WindowsUser32ExtensionsTest {

    /** Переконується, що системні функції оголошено у спільному інтерфейсі, а не дубльовано в адаптері. */
    @Test
    void declaresNativeFunctionsOnlyInSharedUser32Extension() {
        var methodNames = Arrays.stream(WindowsUser32Extensions.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertTrue(methodNames.contains("ClientToScreen"));
        assertTrue(methodNames.contains("TrackMouseEvent"));
        assertFalse(Arrays.stream(WindowsWindowPlatformAdapter.class.getDeclaredClasses())
                .flatMap(nestedType -> Arrays.stream(nestedType.getDeclaredMethods()))
                .map(Method::getName)
                .anyMatch(name -> name.equals("ClientToScreen") || name.equals("TrackMouseEvent")));
    }

    /** Перевіряє поля та розмір нативної структури без завантаження user32.dll. */
    @Test
    void initializesNonClientTrackingRequestWithNativeStructureSize() {
        WindowsUser32Extensions.TrackMouseEventInfo eventTrack =
                new WindowsUser32Extensions.TrackMouseEventInfo(new HWND(new Pointer(0x24L)));

        assertEquals(eventTrack.size(), eventTrack.cbSize);
        assertEquals(WindowsUser32Extensions.TME_LEAVE | WindowsUser32Extensions.TME_NONCLIENT, eventTrack.dwFlags);
        assertWindowPointerValue(0x24L, eventTrack.hwndTrack);
    }

    /** Перевіряє значення HWND, для якого буде зареєстровано відстеження. */
    private static void assertWindowPointerValue(long expected, HWND actual) {
        assertEquals(expected, Pointer.nativeValue(actual.getPointer()));
    }
}
