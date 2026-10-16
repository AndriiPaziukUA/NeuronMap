package com.example.neuronmap.view.window;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Визначає платформні відмінності зовнішнього вигляду заголовка, не змінюючи механізм системної взаємодії JavaFX.
 *
 * <p>Усі підтримувані платформи використовують {@code StageStyle.EXTENDED} та {@code HeaderBar}. Цей тип
 * визначає тільки компонування й додаткові CSS-стилі, тому нову візуальну схему можна додати без зміни життєвого
 * циклу вікна.</p>
 */
enum WindowChromePlatformStyle {
    /** Стиль Windows із кнопками керування праворуч і спільними базовими стилями. */
    WINDOWS(false, "window-chrome-windows", null),
    /** Стиль macOS із кнопками керування ліворуч і центрованим заголовком. */
    MACOS(true, "window-chrome-macos", "/window-chrome-macos.css"),
    /** Стиль Linux із кнопками керування праворуч і налаштуваннями, близькими до настільного середовища. */
    LINUX(false, "window-chrome-linux", "/window-chrome-linux.css"),
    /** Стандартне компонування для платформ, для яких ще не визначено окремий стиль. */
    OTHER(false, "window-chrome-default", null);

    private final boolean controlsOnLeadingEdge;
    private final String rootStyleClass;
    private final String stylesheetResource;

    /**
     * Створює опис платформи для компонування та необов'язкової таблиці стилів.
     *
     * @param controlsOnLeadingEdge чи потрібно розміщувати системні кнопки на початку заголовка.
     * @param rootStyleClass CSS-клас кореневого вузла для цієї платформи.
     * @param stylesheetResource шлях до додаткової таблиці стилів або {@code null}, якщо достатньо базової.
     */
    WindowChromePlatformStyle(
            boolean controlsOnLeadingEdge,
            String rootStyleClass,
            String stylesheetResource
    ) {
        this.controlsOnLeadingEdge = controlsOnLeadingEdge;
        this.rootStyleClass = rootStyleClass;
        this.stylesheetResource = stylesheetResource;
    }

    /**
     * Визначає стиль поточної операційної системи за централізовано прочитаною системною властивістю.
     *
     * @return стиль поточної платформи або стандартний стиль, якщо система не розпізнана.
     */
    static WindowChromePlatformStyle current() {
        return forOperatingSystem(System.getProperty("os.name", ""));
    }

    /**
     * Вибирає платформний стиль за назвою ОС; метод не звертається до JavaFX і придатний для unit-тестів.
     *
     * @param operatingSystemName значення системної властивості {@code os.name}.
     * @return відповідний стиль або {@link #OTHER} для невідомої платформи.
     * @throws NullPointerException якщо назву операційної системи не задано.
     */
    static WindowChromePlatformStyle forOperatingSystem(String operatingSystemName) {
        String normalizedName = Objects.requireNonNull(operatingSystemName, "operatingSystemName")
                .toLowerCase(Locale.ROOT);
        if (normalizedName.contains("mac") || normalizedName.contains("darwin")) {
            return MACOS;
        }
        if (normalizedName.startsWith("windows")) {
            return WINDOWS;
        }
        if (normalizedName.contains("linux")) {
            return LINUX;
        }
        return OTHER;
    }

    /**
     * Повертає ознаку компонування кнопок на початку заголовка.
     *
     * @return {@code true} для компонування кнопок ліворуч; інакше {@code false}.
     */
    boolean controlsOnLeadingEdge() {
        return controlsOnLeadingEdge;
    }

    /**
     * Визначає ключ доступної назви кнопки відповідно до платформної семантики та фактичного стану вікна.
     *
     * @param maximized чи максимізоване вікно; на macOS цей стан не змінює типову дію зеленої кнопки.
     * @param fullScreen чи перебуває вікно в повноекранному режимі.
     * @return ключ локалізації для доступної назви кнопки.
     */
    String maximizeLabelKey(boolean maximized, boolean fullScreen) {
        if (this == MACOS) {
            return fullScreen ? "exitFullScreen" : "enterFullScreen";
        }
        return maximized || fullScreen ? "restore" : "maximize";
    }

    /**
     * Повертає CSS-клас, який ідентифікує вибрану платформну схему.
     *
     * @return клас для кореневого вузла оформлення.
     */
    String rootStyleClass() {
        return rootStyleClass;
    }

    /**
     * Повертає шлях до додаткових платформних стилів, якщо вони визначені.
     *
     * @return шлях до CSS-ресурсу або порожній результат для базового оформлення.
     */
    Optional<String> stylesheetResource() {
        return Optional.ofNullable(stylesheetResource);
    }
}
