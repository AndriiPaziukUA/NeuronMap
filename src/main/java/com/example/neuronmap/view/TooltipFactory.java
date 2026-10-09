package com.example.neuronmap.view;

import com.example.neuronmap.util.UiTiming;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;

/**
 * Створює та налаштовує підказки для елементів керування JavaFX.
 */
public final class TooltipFactory {

    private TooltipFactory() {
    }

    /**
     * Створює підказку з заданим текстом і стандартними параметрами оформлення.
     *
     * @param text текст, який потрібно показати або розібрати.
     */
    public static Tooltip create(String text) {
        Tooltip tooltip = new Tooltip(text == null ? "" : text);
        tooltip.setShowDelay(UiTiming.TOOLTIP_SHOW_DELAY);
        tooltip.setShowDuration(UiTiming.TOOLTIP_VISIBLE_DURATION);
        tooltip.setHideDelay(UiTiming.TOOLTIP_HIDE_DELAY);
        return tooltip;
    }

    /**
     * Створює підказку й приєднує її до елемента керування JavaFX.
     *
     * @param control елемент керування JavaFX.
     * @param text текст, який потрібно показати або розібрати.
     */
    public static void install(Control control, String text) {
        if (control == null) {

            throw new IllegalArgumentException(
                    "control must not be null"
            );
        }

        control.setTooltip(create(text));
    }
}
