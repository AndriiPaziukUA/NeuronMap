package com.example.neuronmap.window;

import org.junit.jupiter.api.Test;

import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.CLIENT;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_BOTTOM;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_BOTTOM_LEFT;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_BOTTOM_RIGHT;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_LEFT;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_RIGHT;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_TOP;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_TOP_LEFT;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_TOP_RIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Перевіряє геометрію зовнішньої resize-області без залежності від Win32, JavaFX чи поточного дисплея. */
final class WindowOuterResizeHitTestTest {

    private static final WindowHitTestCalculator.Rectangle WINDOW =
            new WindowHitTestCalculator.Rectangle(100.0, 100.0, 1100.0, 700.0);
    private static final WindowHitTestCalculator.Rectangle VISIBLE =
            new WindowHitTestCalculator.Rectangle(106.0, 105.0, 1094.0, 694.0);

    /** Перевіряє визначення кожного зовнішнього краю та кута за екранними координатами. */
    @Test
    void recognizesAllOuterEdgesAndCorners() {
        assertEquals(RESIZE_TOP_LEFT, hit(101.0, 101.0, false));
        assertEquals(RESIZE_TOP, hit(500.0, 102.0, false));
        assertEquals(RESIZE_TOP_RIGHT, hit(1098.0, 102.0, false));
        assertEquals(RESIZE_LEFT, hit(102.0, 400.0, false));
        assertEquals(RESIZE_RIGHT, hit(1098.0, 400.0, false));
        assertEquals(RESIZE_BOTTOM_LEFT, hit(101.0, 698.0, false));
        assertEquals(RESIZE_BOTTOM, hit(500.0, 698.0, false));
        assertEquals(RESIZE_BOTTOM_RIGHT, hit(1098.0, 698.0, false));
    }

    /** Перевіряє, що пікселі в межах видимої рамки не класифікуються як зовнішня resize-зона. */
    @Test
    void leavesVisibleClientBoundsAvailableToTheInterface() {
        assertNull(hit(106.0, 400.0, false));
        assertNull(hit(500.0, 105.0, false));
        assertNull(hit(1093.0, 693.0, false));
    }

    /** Перевіряє, що зовнішня resize-зона не виходить за межі HWND. */
    @Test
    void ignoresPointsOutsideNativeWindowBounds() {
        assertNull(hit(99.0, 400.0, false));
        assertNull(hit(1100.0, 400.0, false));
        assertNull(hit(500.0, 701.0, false));
        assertNull(hit(Double.NaN, 400.0, false));
    }

    /** Перевіряє вимкнення зовнішнього resize при максимізації. */
    @Test
    void disablesOuterResizeWhenMaximized() {
        assertNull(hit(101.0, 101.0, true));
        assertNull(hit(1098.0, 400.0, true));
    }

    /** Переконується, що вимкнення внутрішніх resize-смуг не відбирає крайні пікселі у JavaFX-контенту. */
    @Test
    void clientHitTestDoesNotReserveAnInnerResizeStrip() {
        WindowHitTestCalculator.Layout layout = new WindowHitTestCalculator.Layout(
                1000.0, 600.0, 36.0, 12.0, 12.0, false,
                new WindowHitTestCalculator.Rectangle(856.0, 3.0, 902.0, 33.0),
                new WindowHitTestCalculator.Rectangle(902.0, 3.0, 948.0, 33.0),
                new WindowHitTestCalculator.Rectangle(948.0, 3.0, 994.0, 33.0)
        );

        assertEquals(CLIENT, WindowHitTestCalculator.hitTestClientArea(layout, 1.0, 300.0));
        assertEquals(CLIENT, WindowHitTestCalculator.hitTestClientArea(layout, 999.0, 300.0));
        assertEquals(CLIENT, WindowHitTestCalculator.hitTestClientArea(layout, 500.0, 599.0));
    }

    /**
     * Викликає обчислювач для тестових меж вікна.
     *
     * @param x екранна координата X.
     * @param y екранна координата Y.
     * @param maximized чи максимізоване тестове вікно.
     * @return семантичний код зовнішньої resize-області або {@code null}.
     */
    private static WindowHitTestCalculator.HitTarget hit(double x, double y, boolean maximized) {
        return WindowHitTestCalculator.hitTestOuterResizeArea(WINDOW, VISIBLE, x, y, maximized);
    }
}
