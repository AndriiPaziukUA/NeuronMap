package com.example.neuronmap.windowing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє вибір платформної реалізації без залежності від операційної системи, на якій запускається тест. */
final class WindowPlatformAdapterFactoryTest {

    /** Перевіряє, що різні назви Windows визначають підтримку власного оформлення вікна. */
    @Test
    void createsNativeAdapterForWindowsNames() {
        assertTrue(WindowPlatformAdapterFactory.supportsCustomChrome("Windows 11"));
        assertTrue(WindowPlatformAdapterFactory.supportsCustomChrome("Windows 10"));
    }

    /** Перевіряє, що вибір Windows-адаптера не завантажує user32 до встановлення адаптера у вікно. */
    @Test
    void createsWindowsAdapterWithoutLoadingNativeLibrary() {
        WindowPlatformAdapter adapter = assertDoesNotThrow(
                () -> WindowPlatformAdapterFactory.createForOperatingSystem("Windows 11").orElseThrow()
        );

        assertInstanceOf(WindowsWindowPlatformAdapter.class, adapter);
    }

    /** Перевіряє, що macOS, Linux і відсутня назва ОС не вмикають власне оформлення вікна. */
    @Test
    void leavesUnsupportedOperatingSystemsOnTheirNativeDecorations() {
        assertFalse(WindowPlatformAdapterFactory.supportsCustomChrome("Mac OS X"));
        assertFalse(WindowPlatformAdapterFactory.supportsCustomChrome("Linux"));
        assertFalse(WindowPlatformAdapterFactory.supportsCustomChrome(null));
        assertTrue(WindowPlatformAdapterFactory.createForOperatingSystem("Linux").isEmpty());
        assertTrue(WindowPlatformAdapterFactory.createForOperatingSystem(null).isEmpty());
    }
}
