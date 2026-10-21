package com.example.neuronmap.view.support;

import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;

import java.util.Objects;

/**
 * Створює та налаштовує підказки JavaFX, централізуючи розмір шрифту, часові параметри та поведінку клавіатури.
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
     * Створює підказку із заданим текстом і спільними параметрами шрифту, показу та обробки клавіатурних подій.
     *
     * @param text текст, який відображатиме підказка; {@code null} перетворюється на порожній рядок.
     * @return налаштована підказка JavaFX.
     */
    public static Tooltip create(String text) {
        Tooltip tooltip = new Tooltip(text == null ? "" : text);
        configure(tooltip);
        return tooltip;
    }

    /**
     * Застосовує спільний розмір шрифту, часові параметри та політику клавіатурних подій до наявної підказки.
     * Підказка закривається клавішею Escape, але подія не поглинається та може дійти до вікна-власника.
     *
     * @param tooltip підказка, яку потрібно налаштувати.
     * @throws NullPointerException якщо підказку не задано.
     */
    static void configure(Tooltip tooltip) {
        Objects.requireNonNull(tooltip, "tooltip");
        Font currentFont = tooltip.getFont();
        String fontFamily = currentFont == null ? Font.getDefault().getFamily() : currentFont.getFamily();
        tooltip.setFont(Font.font(fontFamily, TOOLTIP_FONT_SIZE));
        tooltip.setShowDelay(UiTiming.TOOLTIP_SHOW_DELAY);
        tooltip.setShowDuration(UiTiming.TOOLTIP_VISIBLE_DURATION);
        tooltip.setHideDelay(UiTiming.TOOLTIP_HIDE_DELAY);
        tooltip.setConsumeAutoHidingEvents(false);
    }

    /**
     * Створює підказку й приєднує її до елемента керування JavaFX.
     *
     * @param control елемент керування JavaFX, для якого показуватиметься підказка.
     * @param text текст, який відображатиме підказка; {@code null} перетворюється на порожній рядок.
     * @throws IllegalArgumentException якщо елемент керування не задано.
     */
    public static void install(Control control, String text) {
        if (control == null) {
            throw new IllegalArgumentException("control must not be null");
        }

        control.setTooltip(create(text));
    }
}
