package com.example.neuronmap.windowing;

import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє декларацію DWM API без завантаження dwmapi.dll на системах, відмінних від Windows. */
final class WindowsDwmExtensionsTest {

    /** Перевіряє ідентифікатор атрибута та типізовану сигнатуру читання видимих меж DWM. */
    @Test
    void declaresExtendedFrameBoundsFunction() throws NoSuchMethodException {
        assertEquals(9, WindowsDwmExtensions.DWMWA_EXTENDED_FRAME_BOUNDS);

        Method method = WindowsDwmExtensions.class.getDeclaredMethod(
                "DwmGetWindowAttribute", HWND.class, int.class, RECT.class, int.class);
        assertEquals(int.class, method.getReturnType());
        assertEquals(4, method.getParameterCount());
    }
}
