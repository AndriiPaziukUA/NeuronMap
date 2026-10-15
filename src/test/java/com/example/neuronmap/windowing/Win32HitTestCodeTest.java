package com.example.neuronmap.windowing;

import com.example.neuronmap.window.WindowHitTestCalculator.HitTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Перевіряє перетворення нейтральних інтерактивних областей у коди платформного протоколу Win32. */
final class Win32HitTestCodeTest {

    /** Перевіряє ключові коди заголовка, зміну розміру та збереження HTMAXBUTTON для Snap Layouts. */
    @Test
    void mapsSemanticAreasToWin32Codes() {
        assertEquals(1, Win32HitTestCode.toNativeCode(HitTarget.CLIENT));
        assertEquals(2, Win32HitTestCode.toNativeCode(HitTarget.CAPTION));
        assertEquals(8, Win32HitTestCode.toNativeCode(HitTarget.MINIMIZE_BUTTON));
        assertEquals(9, Win32HitTestCode.toNativeCode(HitTarget.MAXIMIZE_BUTTON));
        assertEquals(20, Win32HitTestCode.toNativeCode(HitTarget.CLOSE_BUTTON));
        assertEquals(17, Win32HitTestCode.toNativeCode(HitTarget.RESIZE_BOTTOM_RIGHT));
    }

    /** Перевіряє зворотне розпізнавання лише кнопок заголовка з нативних кодів миші. */
    @Test
    void mapsOnlyCaptionButtonCodesBackToSemanticTargets() {
        assertEquals(HitTarget.MINIMIZE_BUTTON, Win32HitTestCode.captionButtonFromNativeCode(8));
        assertEquals(HitTarget.MAXIMIZE_BUTTON, Win32HitTestCode.captionButtonFromNativeCode(9));
        assertEquals(HitTarget.CLOSE_BUTTON, Win32HitTestCode.captionButtonFromNativeCode(20));
        assertNull(Win32HitTestCode.captionButtonFromNativeCode(2));
        assertNull(Win32HitTestCode.captionButtonFromNativeCode(1));
    }
}
