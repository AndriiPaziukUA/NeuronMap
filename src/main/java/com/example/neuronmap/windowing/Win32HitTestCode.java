package com.example.neuronmap.windowing;

import com.example.neuronmap.window.WindowHitTestCalculator.HitTarget;

/** Перетворює семантичні області оформлення вікна на специфічні для Win32 коди hit-test. */
final class Win32HitTestCode {

    /** Створює допоміжний конвертер кодів Win32. */
    private Win32HitTestCode() {
    }

    /**
     * Перетворює семантичну область на код результату {@code WM_NCHITTEST}.
     *
     * @param target логічна область власного оформлення.
     * @return відповідний код Win32.
     */
    static int toNativeCode(HitTarget target) {
        return switch (target) {
            case CLIENT -> 1;
            case CAPTION -> 2;
            case MINIMIZE_BUTTON -> 8;
            case MAXIMIZE_BUTTON -> 9;
            case CLOSE_BUTTON -> 20;
            case RESIZE_LEFT -> 10;
            case RESIZE_RIGHT -> 11;
            case RESIZE_TOP -> 12;
            case RESIZE_TOP_LEFT -> 13;
            case RESIZE_TOP_RIGHT -> 14;
            case RESIZE_BOTTOM -> 15;
            case RESIZE_BOTTOM_LEFT -> 16;
            case RESIZE_BOTTOM_RIGHT -> 17;
        };
    }

    /**
     * Перетворює код non-client події на кнопку заголовка, якщо код належить такій кнопці.
     *
     * @param nativeCode код області з повідомлення Win32.
     * @return семантична кнопка або {@code null}, якщо код не відповідає кнопці заголовка.
     */
    static HitTarget captionButtonFromNativeCode(int nativeCode) {
        return switch (nativeCode) {
            case 8 -> HitTarget.MINIMIZE_BUTTON;
            case 9 -> HitTarget.MAXIMIZE_BUTTON;
            case 20 -> HitTarget.CLOSE_BUTTON;
            default -> null;
        };
    }
}
