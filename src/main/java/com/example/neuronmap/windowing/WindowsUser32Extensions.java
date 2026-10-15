package com.example.neuronmap.windowing;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Містить сигнатури функцій user32.dll, які потрібні адаптеру вікна, але відсутні в стандартному інтерфейсі JNA.
 *
 * <p>Інтерфейс зберігає типобезпечне відображення Win32 API й не змішує його з логікою обробки повідомлень вікна.</p>
 */
public interface WindowsUser32Extensions extends StdCallLibrary {

    /** Екземпляр типобезпечного відображення функцій user32.dll. */
    WindowsUser32Extensions INSTANCE = Native.load(
            "user32",
            WindowsUser32Extensions.class,
            W32APIOptions.DEFAULT_OPTIONS
    );

    /**
     * Перетворює точку з клієнтських координат вікна на координати екрана на місці переданої структури.
     *
     * @param window дескриптор вікна, чия клієнтська система координат використовується.
     * @param point точка у клієнтських координатах; після успішного виклику містить екранні координати.
     * @return {@code true}, якщо перетворення виконано; інакше {@code false}.
     */
    boolean ClientToScreen(HWND window, POINT point);
}
