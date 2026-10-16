package com.example.neuronmap.windowing;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Містить оголошення DWM API, потрібні для розрізнення зовнішніх та видимих меж вікна Windows.
 * Окремий інтерфейс не змішує функції dwmapi.dll з розширеннями user32.dll.
 */
interface WindowsDwmExtensions extends StdCallLibrary {

    /** Атрибут DWM, який повертає видимі межі рамки у координатах екрана. */
    int DWMWA_EXTENDED_FRAME_BOUNDS = 9;

    /** Проксі DWM API; завантаження бібліотеки відбувається лише за першого нативного запиту. */
    WindowsDwmExtensions INSTANCE = Native.load(
            "dwmapi",
            WindowsDwmExtensions.class,
            W32APIOptions.DEFAULT_OPTIONS
    );

    /**
     * Читає значення атрибута DWM для заданого HWND.
     *
     * @param window дескриптор вікна Windows.
     * @param attribute ідентифікатор атрибута DWM.
     * @param value структура, до якої DWM записує результат.
     * @param size розмір структури в байтах.
     * @return HRESULT: нуль або додатне значення означає успіх, від'ємне — помилку.
     */
    int DwmGetWindowAttribute(HWND window, int attribute, RECT value, int size);
}
