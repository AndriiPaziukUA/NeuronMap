package com.example.neuronmap.windowing;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Містить сигнатури функцій {@code user32.dll}, яких немає у стандартному інтерфейсі JNA {@code User32}.
 * Відображення API відокремлене від логіки адаптера та використовує стандартні параметри виклику Windows.
 */
public interface WindowsUser32Extensions extends StdCallLibrary {

    /** Спільний проксі Win32 API з параметрами виклику, сумісними з Windows. */
    WindowsUser32Extensions INSTANCE = Native.load(
            "user32",
            WindowsUser32Extensions.class,
            W32APIOptions.DEFAULT_OPTIONS
    );

    /**
     * Перетворює точку з клієнтських координат вікна на координати екрана.
     *
     * @param window дескриптор вікна, чия клієнтська система координат використовується.
     * @param point точка у клієнтських координатах; після успішного виклику містить екранні координати.
     * @return {@code true}, якщо перетворення виконано; інакше {@code false}.
     */
    boolean ClientToScreen(HWND window, POINT point);

    /**
     * Передає тимчасове захоплення миші вікну, щоб отримувати подію відпускання поза кнопкою.
     *
     * @param window HWND, що захоплює мишу.
     * @return попередній HWND, який мав захоплення.
     */
    HWND SetCapture(HWND window);

    /**
     * Звільняє захоплення миші після завершення нативного кліку.
     *
     * @return {@code true}, якщо захоплення було звільнено.
     */
    boolean ReleaseCapture();

    /**
     * Повертає системну метрику для конкретного DPI.
     *
     * @param metric індекс системної метрики Windows.
     * @param dpi DPI, для якого потрібно обчислити метрику.
     * @return значення метрики у фізичних пікселях.
     */
    int GetSystemMetricsForDpi(int metric, int dpi);

    /**
     * Повертає DPI, встановлений для заданого нативного вікна.
     *
     * @param window HWND вікна.
     * @return DPI вікна.
     */
    int GetDpiForWindow(HWND window);
}
