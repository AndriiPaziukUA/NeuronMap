package com.example.neuronmap.windowing;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.Objects;

/**
 * Містить сигнатури функцій {@code user32.dll}, яких немає у стандартному інтерфейсі JNA {@code User32}.
 * Відображення API відокремлене від логіки адаптера та використовує стандартні параметри виклику Windows.
 */
public interface WindowsUser32Extensions extends StdCallLibrary {

    /** Прапорець запиту сповіщення про вихід курсора з області відстеження. */
    int TME_LEAVE = 0x00000002;
    /** Прапорець відстеження non-client області вікна. */
    int TME_NONCLIENT = 0x00000010;

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
     * Реєструє нативне сповіщення про вихід курсора з клієнтської або non-client області вікна.
     *
     * @param eventTrack структура з дескриптором вікна та прапорцями відстеження.
     * @return {@code true}, якщо Windows зареєструвала відстеження; інакше {@code false}.
     */
    boolean TrackMouseEvent(TrackMouseEventInfo eventTrack);

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

    /** Відображає нативну структуру TRACKMOUSEEVENT, якої немає в JNA WinUser. */
    @Structure.FieldOrder({"cbSize", "dwFlags", "hwndTrack", "dwHoverTime"})
    class TrackMouseEventInfo extends Structure {

        /** Розмір структури у байтах, потрібний Win32 для перевірки версії контракту. */
        public int cbSize;
        /** Комбінація прапорців TME_*, що визначає вид повідомлень відстеження. */
        public int dwFlags;
        /** Вікно, для якого реєструється відстеження курсора. */
        public HWND hwndTrack;
        /** Інтервал наведення в мілісекундах; не використовується для запиту лише виходу. */
        public int dwHoverTime;

        /**
         * Створює структуру відстеження для вказаного вікна та встановлює коректний розмір ABI.
         *
         * @param window HWND, для якого Windows має відстежувати рух курсора.
         * @throws NullPointerException якщо дескриптор вікна не задано.
         */
        @SuppressWarnings("this-escape")
        public TrackMouseEventInfo(HWND window) {
            this.hwndTrack = Objects.requireNonNull(window, "window");
            this.dwFlags = TME_LEAVE | TME_NONCLIENT;
            this.dwHoverTime = 0;
            this.cbSize = size();
        }
    }
}
