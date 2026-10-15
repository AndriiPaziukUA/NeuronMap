package com.example.neuronmap.windowing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє вибір платформної реалізації без залежності тесту від операційної системи, на якій він запускається. */
final class WindowPlatformAdapterFactoryTest {

    /** Перевіряє, що різні назви Windows спрямовуються до нативного Win32-адаптера. */
    @Test
    void createsNativeAdapterForWindowsNames() {
        assertTrue(WindowPlatformAdapterFactory.supportsCustomChrome("Windows 11"));
        assertTrue(WindowPlatformAdapterFactory.supportsCustomChrome("Windows 10"));
        assertInstanceOf(WindowsWindowPlatformAdapter.class,
                WindowPlatformAdapterFactory.createForOperatingSystem("Windows 11").orElseThrow());
    }

    /** Перевіряє, що на macOS, Linux та за відсутньої назви ОС системне оформлення поки не змінюється. */
    @Test
    void leavesUnsupportedOperatingSystemsOnTheirNativeDecorations() {
        assertFalse(WindowPlatformAdapterFactory.supportsCustomChrome("Mac OS X"));
        assertFalse(WindowPlatformAdapterFactory.supportsCustomChrome("Linux"));
        assertFalse(WindowPlatformAdapterFactory.supportsCustomChrome(null));
        assertTrue(WindowPlatformAdapterFactory.createForOperatingSystem("Linux").isEmpty());
        assertTrue(WindowPlatformAdapterFactory.createForOperatingSystem(null).isEmpty());
    }
}
