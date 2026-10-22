package com.example.neuronmap.persistence.settings;

import java.util.Objects;

/** Зберігає налаштування повноекранного режиму в спільному файлі глобальних параметрів. */
public final class GlobalSettingsFullScreenPreferenceStore {

    private static final String FULL_SCREEN_KEY = "fullscreen";

    private final GlobalSettingsStore settings;

    /**
     * Створює адаптер до наявного сховища глобальних налаштувань.
     *
     * @param settings спільне сховище глобальних властивостей застосунку.
     * @throws NullPointerException якщо сховище не задано.
     */
    public GlobalSettingsFullScreenPreferenceStore(GlobalSettingsStore settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Завантажує збережений стан повноекранного режиму.
     * Відсутнє або некоректне значення не вмикає режим, що зберігає віконну поведінку за замовчуванням.
     *
     * @return {@code true}, якщо режим було явно збережено увімкненим.
     */
    public boolean loadFullScreenEnabled() {
        String value = settings.load(FULL_SCREEN_KEY);
        return Boolean.parseBoolean(value);
    }

    /**
     * Зберігає стан повноекранного режиму через централізований механізм атомарного запису.
     *
     * @param enabled {@code true}, щоб вмикати повноекранний режим під час наступного запуску.
     */
    public void saveFullScreenEnabled(boolean enabled) {
        settings.save(FULL_SCREEN_KEY, Boolean.toString(enabled));
    }
}
