package com.example.neuronmap.view;

import com.example.neuronmap.util.UiTiming;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;

/**
 * Створює графічні підказки для елементів інтерфейсу.
 */
public final class TooltipFactory {

    /**
     * Повертає результат операції «підказка».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private TooltipFactory() {
    }

    /**
     * Створює обʼєкт із переданих даних «потрібні дані».
     *
     * @param text текст, який потрібно показати або обробити.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public static Tooltip create(String text) {
        Tooltip tooltip = new Tooltip(text == null ? "" : text);
        tooltip.setShowDelay(UiTiming.TOOLTIP_SHOW_DELAY);
        tooltip.setShowDuration(UiTiming.TOOLTIP_VISIBLE_DURATION);
        tooltip.setHideDelay(UiTiming.TOOLTIP_HIDE_DELAY);
        return tooltip;
    }

    /**
     * Виконує операцію «відповідну операцію».
     *
     * @param control значення, що визначає відповідну операцію для цієї операції.
     *
     * @param text текст, який потрібно показати або обробити.
     */
    public static void install(Control control, String text) {
        if (control == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "control must not be null"
            );
        }

        control.setTooltip(create(text));
    }
}
