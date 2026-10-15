package com.example.neuronmap.windowing;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє архітектурний контракт єдиного оголошення додаткової функції user32.dll. */
class WindowsUser32ExtensionsTest {

    /** Переконується, що ClientToScreen належить спільному інтерфейсу, а не дублюється в адаптері. */
    @Test
    void declaresClientToScreenOnlyInSharedUser32Extension() {
        assertTrue(Arrays.stream(WindowsUser32Extensions.class.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch("ClientToScreen"::equals));
        assertFalse(Arrays.stream(WindowsWindowPlatformAdapter.class.getDeclaredClasses())
                .flatMap(nestedType -> Arrays.stream(nestedType.getDeclaredMethods()))
                .map(Method::getName)
                .anyMatch("ClientToScreen"::equals));
    }
}
