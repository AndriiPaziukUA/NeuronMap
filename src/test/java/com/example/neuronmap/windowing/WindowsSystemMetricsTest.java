package com.example.neuronmap.windowing;

import com.example.neuronmap.window.WindowHitTestCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє перетворення системних метрик Windows без завантаження Win32 на платформі тестування. */
final class WindowsSystemMetricsTest {

    /** Перевіряє обчислення типового DPI для стандартного й збільшеного масштабу JavaFX. */
    @Test
    void derivesFallbackDpiFromLargestOutputScale() {
        assertEquals(96, WindowsSystemMetrics.fallbackDpi(1.0, 1.0));
        assertEquals(144, WindowsSystemMetrics.fallbackDpi(1.5, 1.25));
        assertEquals(168, WindowsSystemMetrics.fallbackDpi(1.25, 1.75));
        assertEquals(96, WindowsSystemMetrics.fallbackDpi(0.0, Double.NaN));
    }

    /** Перевіряє переведення фізичних пікселів у логічні одиниці для коректного та некоректного масштабу. */
    @Test
    void convertsPhysicalPixelsToLogicalUnits() {
        assertEquals(12.0, WindowsSystemMetrics.toLogicalPixels(12, 1.0));
        assertEquals(8.0, WindowsSystemMetrics.toLogicalPixels(12, 1.5));
        assertEquals(12.0, WindowsSystemMetrics.toLogicalPixels(12, 0.0));
        assertEquals(12.0, WindowsSystemMetrics.toLogicalPixels(12, Double.NaN));
    }

    /** Переконується, що відсутня або некоректна системна метрика замінюється типовою межею resize. */
    @Test
    void usesDefaultResizeBorderForNonPositiveOrNonFiniteMetrics() {
        double expected = WindowHitTestCalculator.DEFAULT_RESIZE_BORDER;

        assertEquals(expected, WindowsSystemMetrics.positiveMetric(0.0));
        assertEquals(expected, WindowsSystemMetrics.positiveMetric(-2.0));
        assertEquals(expected, WindowsSystemMetrics.positiveMetric(Double.NaN));
        assertEquals(expected, WindowsSystemMetrics.positiveMetric(Double.POSITIVE_INFINITY));
        assertEquals(expected, WindowsSystemMetrics.positiveMetric(Double.NEGATIVE_INFINITY));
        assertEquals(7.5, WindowsSystemMetrics.positiveMetric(7.5));
    }
}
