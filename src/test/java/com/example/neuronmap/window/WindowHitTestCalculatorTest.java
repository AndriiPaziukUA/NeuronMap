package com.example.neuronmap.window;

import org.junit.jupiter.api.Test;

import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.CAPTION;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.CLIENT;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.CLOSE_BUTTON;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.MAXIMIZE_BUTTON;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.MINIMIZE_BUTTON;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_BOTTOM;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_BOTTOM_LEFT;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_BOTTOM_RIGHT;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_LEFT;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_RIGHT;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_TOP;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_TOP_LEFT;
import static com.example.neuronmap.window.WindowHitTestCalculator.HitTarget.RESIZE_TOP_RIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє платформонезалежні правила геометрії власного оформлення без запуску JavaFX або Win32. */
final class WindowHitTestCalculatorTest {

    /** Перевіряє визначення трьох кнопок і те, що їхні області розпізнаються як кнопки заголовка. */
    @Test
    void recognizesCaptionButtons() {
        WindowHitTestCalculator.Layout layout = layout(false);

        assertEquals(MINIMIZE_BUTTON, WindowHitTestCalculator.hitTest(layout, 870.0, 15.0));
        assertEquals(MAXIMIZE_BUTTON, WindowHitTestCalculator.hitTest(layout, 920.0, 15.0));
        assertEquals(CLOSE_BUTTON, WindowHitTestCalculator.hitTest(layout, 970.0, 15.0));
        assertTrue(MAXIMIZE_BUTTON.isCaptionButton());
    }

    /** Перевіряє, що краї кнопок заголовка мають пріоритет над зоною resize, яка геометрично їх перекриває. */
    @Test
    void captionButtonsTakePriorityAtTheirEdges() {
        WindowHitTestCalculator.Layout layout = layout(false);

        assertEquals(MINIMIZE_BUTTON, WindowHitTestCalculator.hitTest(layout, 856.0, 3.0));
        assertEquals(MAXIMIZE_BUTTON, WindowHitTestCalculator.hitTest(layout, 920.0, 3.0));
        assertEquals(CLOSE_BUTTON, WindowHitTestCalculator.hitTest(layout, 993.9, 3.0));
        assertEquals(RESIZE_RIGHT, WindowHitTestCalculator.hitTest(layout, 994.0, 15.0));
        assertEquals(CAPTION, WindowHitTestCalculator.hitTest(layout, 993.9, 33.0));
    }

    /** Перевіряє розрізнення вільної області заголовка, клієнтського вмісту й некоректних координат. */
    @Test
    void distinguishesCaptionFromEditorContent() {
        WindowHitTestCalculator.Layout layout = layout(false);

        assertEquals(CAPTION, WindowHitTestCalculator.hitTest(layout, 400.0, 18.0));
        assertEquals(CLIENT, WindowHitTestCalculator.hitTest(layout, 400.0, 200.0));
        assertEquals(CLIENT, WindowHitTestCalculator.hitTest(layout, -1.0, 200.0));
        assertEquals(CLIENT, WindowHitTestCalculator.hitTest(layout, Double.NaN, 200.0));
    }

    /** Перевіряє семантичні області всіх кутів і країв вікна в звичайному стані. */
    @Test
    void recognizesResizeEdgesAndCornersWhenWindowIsRestored() {
        WindowHitTestCalculator.Layout layout = layout(false);

        assertEquals(RESIZE_TOP_LEFT, WindowHitTestCalculator.hitTest(layout, 1.0, 1.0));
        assertEquals(RESIZE_TOP, WindowHitTestCalculator.hitTest(layout, 500.0, 1.0));
        assertEquals(RESIZE_TOP_RIGHT, WindowHitTestCalculator.hitTest(layout, 999.0, 1.0));
        assertEquals(RESIZE_LEFT, WindowHitTestCalculator.hitTest(layout, 1.0, 300.0));
        assertEquals(RESIZE_RIGHT, WindowHitTestCalculator.hitTest(layout, 999.0, 300.0));
        assertEquals(RESIZE_BOTTOM_LEFT, WindowHitTestCalculator.hitTest(layout, 1.0, 599.0));
        assertEquals(RESIZE_BOTTOM, WindowHitTestCalculator.hitTest(layout, 500.0, 599.0));
        assertEquals(RESIZE_BOTTOM_RIGHT, WindowHitTestCalculator.hitTest(layout, 999.0, 599.0));
    }

    /** Перевіряє, що в максимізованому стані геометрія не пропонує ручну зміну розміру. */
    @Test
    void doesNotOfferResizeEdgesWhileMaximized() {
        WindowHitTestCalculator.Layout layout = layout(true);

        assertEquals(CLIENT, WindowHitTestCalculator.hitTest(layout, 1.0, 300.0));
        assertEquals(CAPTION, WindowHitTestCalculator.hitTest(layout, 400.0, 18.0));
        assertEquals(MAXIMIZE_BUTTON, WindowHitTestCalculator.hitTest(layout, 920.0, 15.0));
    }

    /** Перевіряє відхилення некоректних розмірів та збереження семантики кнопок у спільному контракті. */
    @Test
    void rejectsInvalidGeometryAndKeepsButtonSemanticsPlatformIndependent() {
        assertThrows(IllegalArgumentException.class, () -> new WindowHitTestCalculator.Layout(
                0.0, 600.0, 36.0, 6.0, 6.0, false,
                button(856.0, 902.0), button(902.0, 948.0), button(948.0, 994.0)
        ));
        assertThrows(IllegalArgumentException.class, () -> new WindowHitTestCalculator.Rectangle(
                10.0, 10.0, 9.0, 20.0
        ));
        assertThrows(NullPointerException.class, () -> WindowHitTestCalculator.hitTest(null, 0.0, 0.0));
        assertTrue(CLOSE_BUTTON.isCaptionButton());
        assertFalse(CLIENT.isCaptionButton());
    }

    /**
     * Створює узгоджений тестовий знімок вікна з кнопками у правій частині заголовка.
     *
     * @param maximized чи має тестове вікно бути максимізованим.
     * @return перевірений знімок геометрії.
     */
    private static WindowHitTestCalculator.Layout layout(boolean maximized) {
        return new WindowHitTestCalculator.Layout(
                1000.0,
                600.0,
                36.0,
                WindowHitTestCalculator.DEFAULT_RESIZE_BORDER,
                WindowHitTestCalculator.DEFAULT_RESIZE_BORDER,
                maximized,
                button(856.0, 902.0),
                button(902.0, 948.0),
                button(948.0, 994.0)
        );
    }

    /**
     * Створює тестовий прямокутник кнопки типової висоти.
     *
     * @param left ліва координата кнопки.
     * @param right права координата кнопки.
     * @return прямокутник кнопки.
     */
    private static WindowHitTestCalculator.Rectangle button(double left, double right) {
        return new WindowHitTestCalculator.Rectangle(left, 3.0, right, 33.0);
    }
}
