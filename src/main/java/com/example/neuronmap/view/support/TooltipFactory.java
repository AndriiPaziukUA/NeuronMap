package com.example.neuronmap.view.support;

import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import javafx.util.Duration;

import java.util.Objects;

/**
 * Створює та налаштовує підказки JavaFX, централізуючи шрифт, часові параметри й поведінку клавіатури.
 */
public final class TooltipFactory {

    /** Спільний розмір тексту всіх підказок JavaFX у пунктах. */
    private static final double TOOLTIP_FONT_SIZE = 14.0;

    /**
     * Забороняє створення екземплярів класу, який централізує створення та налаштування підказок.
     */
    private TooltipFactory() {
    }

    /**
     * Створює підказку із заданим текстом, яка не зникає автоматично через сплив часу.
     * Підказка зберігає спільне оформлення та політику обробки клавіатурних подій.
     *
     * @param text текст, який відображатиме підказка; {@code null} перетворюється на порожній рядок.
     * @return налаштована підказка з необмеженою тривалістю показу.
     */
    public static Tooltip create(String text) {
        return create(text, Duration.INDEFINITE);
    }

    /**
     * Створює підказку зі стандартною тривалістю показу для елементів інтерфейсу, де потрібне автоматичне приховування.
     *
     * @param text текст підказки; {@code null} перетворюється на порожній рядок.
     * @return налаштована підказка зі стандартним часовим інтервалом.
     */
    public static Tooltip createTimed(String text) {
        return create(text, UiTiming.TOOLTIP_VISIBLE_DURATION);
    }

    /**
     * Застосовує спільний розмір шрифту, звичайні часові параметри та політику клавіатурних подій до підказки.
     * Підказка закривається клавішею Escape, але подія не поглинається та може дійти до вікна-власника.
     * Цей метод зберігає часову поведінку підказок, якими керує сцена.
     *
     * @param tooltip підказка, яку потрібно налаштувати.
     * @throws NullPointerException якщо підказку не задано.
     */
    static void configure(Tooltip tooltip) {
        configure(tooltip, UiTiming.TOOLTIP_VISIBLE_DURATION);
    }

    /**
     * Створює підказку й приєднує її до елемента керування зі стандартною тривалістю показу.
     *
     * @param control елемент керування JavaFX, для якого показуватиметься підказка.
     * @param text текст, який відображатиме підказка; {@code null} перетворюється на порожній рядок.
     * @throws IllegalArgumentException якщо елемент керування не задано.
     */
    public static void install(Control control, String text) {
        if (control == null) {
            throw new IllegalArgumentException("control must not be null");
        }

        control.setTooltip(createTimed(text));
    }

    /**
     * Створює підказку з обраною тривалістю показу та застосовує спільне оформлення.
     *
     * @param text текст підказки; {@code null} нормалізується до порожнього рядка.
     * @param visibleDuration тривалість показу підказки перед автоматичним приховуванням.
     * @return повністю налаштована підказка JavaFX.
     * @throws NullPointerException якщо тривалість показу не задано.
     */
    private static Tooltip create(String text, Duration visibleDuration) {
        Tooltip tooltip = new Tooltip(text == null ? "" : text);
        configure(tooltip, visibleDuration);
        return tooltip;
    }

    /**
     * Застосовує спільне оформлення, часову політику та поведінку клавіатури до заданої підказки.
     *
     * @param tooltip підказка, яку потрібно налаштувати.
     * @param visibleDuration тривалість показу перед автоматичним приховуванням.
     * @throws NullPointerException якщо підказку або тривалість показу не задано.
     */
    private static void configure(Tooltip tooltip, Duration visibleDuration) {
        Objects.requireNonNull(tooltip, "tooltip");
        Objects.requireNonNull(visibleDuration, "visibleDuration");
        Font currentFont = tooltip.getFont();
        String fontFamily = currentFont == null ? Font.getDefault().getFamily() : currentFont.getFamily();
        tooltip.setFont(Font.font(fontFamily, TOOLTIP_FONT_SIZE));
        tooltip.setShowDelay(UiTiming.TOOLTIP_SHOW_DELAY);
        tooltip.setShowDuration(visibleDuration);
        tooltip.setHideDelay(UiTiming.TOOLTIP_HIDE_DELAY);
        tooltip.setConsumeAutoHidingEvents(false);
    }
}
