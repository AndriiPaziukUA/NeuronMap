package com.example.neuronmap.windowing;

import java.util.Locale;
import java.util.Optional;

/**
 * Визначає адаптер керування вікном для поточної операційної системи.
 * Реєстрація платформних реалізацій зосереджена в одному місці, щоб додавання macOS або Linux не вимагало
 * змін у контролері головного вікна.
 */
public final class WindowPlatformAdapterFactory {

    /** Створює фабрику адаптерів без зовнішніх залежностей. */
    private WindowPlatformAdapterFactory() {
    }

    /**
     * Створює адаптер для поточної операційної системи, якщо вона підтримується власним заголовком.
     *
     * @return Windows-адаптер для Windows або порожнє значення для інших систем.
     */
    public static Optional<WindowPlatformAdapter> createForCurrentOperatingSystem() {
        return createForOperatingSystem(System.getProperty("os.name"));
    }

    /**
     * Створює адаптер за назвою операційної системи; окремий параметр полегшує перевірку вибору платформи.
     *
     * @param operatingSystem назва операційної системи з властивості {@code os.name}.
     * @return Windows-адаптер для Windows або порожнє значення для непідтримуваної системи.
     */
    public static Optional<WindowPlatformAdapter> createForOperatingSystem(String operatingSystem) {
        if (operatingSystem == null) {
            return Optional.empty();
        }
        String normalized = operatingSystem.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("windows")) {
            return Optional.of(new WindowsWindowPlatformAdapter());
        }
        return Optional.empty();
    }

    /**
     * Перевіряє, чи має ця версія застосунку адаптер власного нативного заголовка для заданої ОС.
     *
     * @param operatingSystem назва операційної системи.
     * @return {@code true} лише для Windows.
     */
    public static boolean supportsCustomChrome(String operatingSystem) {
        return operatingSystem != null
                && operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
