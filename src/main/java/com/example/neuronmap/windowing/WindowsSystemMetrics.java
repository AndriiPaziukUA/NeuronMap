package com.example.neuronmap.windowing;

import com.example.neuronmap.window.WindowHitTestCalculator;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Читає DPI й системні метрики Windows та переводить товщину рамки зміни розміру в логічні одиниці JavaFX.
 * Цей клас зосереджує залежні від ОС обчислення, щоб адаптер керував лише життєвим циклом їх застосування.
 */
final class WindowsSystemMetrics {

    private static final Logger LOGGER = Logger.getLogger(WindowsSystemMetrics.class.getName());

    private static final int SM_CXSIZEFRAME = 32;
    private static final int SM_CYSIZEFRAME = 33;
    private static final int SM_CXPADDEDBORDER = 92;
    private static final int DEFAULT_DPI = 96;

    /** Створює обчислювач метрик без завантаження нативної бібліотеки до першого запиту. */
    WindowsSystemMetrics() {
    }

    /**
     * Читає системні метрики для вікна й переводить їх у координати JavaFX.
     *
     * @param window дескриптор нативного вікна.
     * @param outputScaleX масштаб JavaFX по горизонталі.
     * @param outputScaleY масштаб JavaFX по вертикалі.
     * @return обчислена товщина горизонтальної та вертикальної зон зміни розміру.
     */
    ResizeBorders readResizeBorders(HWND window, double outputScaleX, double outputScaleY) {
        double scaleX = validScale(outputScaleX);
        double scaleY = validScale(outputScaleY);
        int dpi = fallbackDpi(scaleX, scaleY);
        try {
            int windowDpi = WindowsUser32Extensions.INSTANCE.GetDpiForWindow(window);
            if (windowDpi > 0) {
                dpi = windowDpi;
            }
        } catch (UnsatisfiedLinkError exception) {
            LOGGER.log(Level.FINE,
                    "GetDpiForWindow недоступний; для системних метрик використовується масштаб JavaFX",
                    exception);
        }

        double horizontal = systemResizeMetric(SM_CXSIZEFRAME, dpi, scaleX)
                + systemResizeMetric(SM_CXPADDEDBORDER, dpi, scaleX);
        double vertical = systemResizeMetric(SM_CYSIZEFRAME, dpi, scaleY)
                + systemResizeMetric(SM_CXPADDEDBORDER, dpi, scaleY);
        return new ResizeBorders(positiveMetric(horizontal), positiveMetric(vertical));
    }

    /**
     * Обчислює DPI за масштабом JavaFX, якщо нативне вікно не надає точного значення.
     *
     * @param scaleX горизонтальний масштаб JavaFX.
     * @param scaleY вертикальний масштаб JavaFX.
     * @return типове DPI, скориговане на найбільший із допустимих масштабів.
     */
    static int fallbackDpi(double scaleX, double scaleY) {
        return (int) Math.round(DEFAULT_DPI * Math.max(validScale(scaleX), validScale(scaleY)));
    }

    /**
     * Переводить фізичну системну метрику в логічні одиниці JavaFX.
     *
     * @param physicalPixels значення системної метрики у фізичних пікселях.
     * @param scale масштаб відповідної осі.
     * @return значення в логічних одиницях.
     */
    static double toLogicalPixels(int physicalPixels, double scale) {
        return physicalPixels / validScale(scale);
    }

    /**
     * Замінює відсутню або недодатну системну товщину безпечним типовим значенням.
     *
     * @param metric сумарна товщина зони зміни розміру.
     * @return скінченна додатна товщина зони.
     */
    static double positiveMetric(double metric) {
        return Double.isFinite(metric) && metric > 0.0
                ? metric
                : WindowHitTestCalculator.DEFAULT_RESIZE_BORDER;
    }

    /**
     * Перевіряє коефіцієнт масштабування та замінює недопустиме значення одиницею.
     *
     * @param scale коефіцієнт JavaFX для відповідної осі.
     * @return скінченний додатний коефіцієнт масштабування.
     */
    static double validScale(double scale) {
        return Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
    }

    /**
     * Зчитує системну метрику у фізичних пікселях із сумісним резервним викликом для старих версій Windows.
     *
     * @param metric індекс системної метрики.
     * @param dpi DPI, для якого запитується метрика.
     * @param scale масштаб JavaFX відповідної осі.
     * @return значення метрики в логічних одиницях.
     */
    private double systemResizeMetric(int metric, int dpi, double scale) {
        int physicalPixels;
        try {
            physicalPixels = WindowsUser32Extensions.INSTANCE.GetSystemMetricsForDpi(metric, dpi);
        } catch (UnsatisfiedLinkError exception) {
            physicalPixels = User32.INSTANCE.GetSystemMetrics(metric);
        }
        return toLogicalPixels(physicalPixels, scale);
    }

    /** Повертає результат читання товщини рамки у логічних одиницях JavaFX. */
    record ResizeBorders(double horizontal, double vertical) {

        /**
         * Перевіряє, що обчислені межі можна безпечно передати контракту оформлення вікна.
         *
         * @param horizontal горизонтальна товщина зони зміни розміру.
         * @param vertical вертикальна товщина зони зміни розміру.
         * @throws IllegalArgumentException якщо будь-яка межа не є скінченною додатною величиною.
         */
        ResizeBorders {
            if (!Double.isFinite(horizontal) || horizontal <= 0.0
                    || !Double.isFinite(vertical) || vertical <= 0.0) {
                throw new IllegalArgumentException("Resize borders must be finite and positive");
            }
        }
    }
}
