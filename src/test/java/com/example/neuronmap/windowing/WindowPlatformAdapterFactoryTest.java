package com.example.neuronmap.windowing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє вибір платформної реалізації без залежності від операційної системи, на якій запускається тест. */
final class WindowPlatformAdapterFactoryTest {

    /** Перевіряє створення адаптера та підтримку власного оформлення для різних назв Windows. */
    @Test
    void createsNativeAdapterForWindowsNames() {
        assertTrue(WindowPlatformAdapterFactory.supportsCustomChrome("Windows 11"));
        assertTrue(WindowPlatformAdapterFactory.supportsCustomChrome("Windows 10"));
        assertTrue(WindowPlatformAdapterFactory.createForOperatingSystem("Windows 11").isPresent());
        assertTrue(WindowPlatformAdapterFactory.createForOperatingSystem("Windows 10").isPresent());
        assertTrue(WindowPlatformAdapterFactory.supportsCustomChrome("WINDOWS Server"));
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
        assertFalse(WindowPlatformAdapterFactory.supportsCustomChrome("macOS"));
        assertFalse(WindowPlatformAdapterFactory.supportsCustomChrome("Linux"));
        assertFalse(WindowPlatformAdapterFactory.supportsCustomChrome(null));
        assertTrue(WindowPlatformAdapterFactory.createForOperatingSystem("Linux").isEmpty());
        assertTrue(WindowPlatformAdapterFactory.createForOperatingSystem(null).isEmpty());
    }

    /** Переконується, що створення адаптера й оголошення підтримки визначаються тією самою реєстрацією. */
    @Test
    void reportsCustomChromeSupportExactlyForRegisteredAdapters() {
        List<String> operatingSystems = List.of(
                "Windows 11",
                "Windows 10",
                "windows server",
                "Mac OS X",
                "macOS",
                "Linux",
                "FreeBSD",
                ""
        );

        for (String operatingSystem : operatingSystems) {
            assertEquals(
                    WindowPlatformAdapterFactory.createForOperatingSystem(operatingSystem).isPresent(),
                    WindowPlatformAdapterFactory.supportsCustomChrome(operatingSystem),
                    "Перевірка підтримки має відповідати реєстрації адаптера для ОС: " + operatingSystem
            );
        }
    }
}
