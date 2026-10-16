package com.example.neuronmap.view.support;

import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;

import java.util.Objects;

/**
 * Створює та налаштовує підказки для елементів керування JavaFX.
 */
public final class TooltipFactory {

    /**
     * Забороняє створення екземплярів класу, який централізує створення та налаштування підказок.
     */
    private TooltipFactory() {
    }

    /**
     * Створює підказку із заданим текстом і спільними часовими параметрами інтерфейсу.
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
     * Застосовує єдині часові параметри відображення до вже створеної підказки.
     * Цей метод потрібен, коли підказку створив інший компонент, але її показом керує менеджер інтерфейсу.
     *
     * @param tooltip підказка, яку потрібно налаштувати.
     * @throws NullPointerException якщо підказку не задано.
     */
    static void configure(Tooltip tooltip) {
        Objects.requireNonNull(tooltip, "tooltip");
        tooltip.setShowDelay(UiTiming.TOOLTIP_SHOW_DELAY);
        tooltip.setShowDuration(UiTiming.TOOLTIP_VISIBLE_DURATION);
        tooltip.setHideDelay(UiTiming.TOOLTIP_HIDE_DELAY);
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
