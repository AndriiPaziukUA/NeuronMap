package com.example.neuronmap.view;

import com.example.neuronmap.util.UiTiming;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;

/** Creates consistently timed tooltips used by the application UI. */
public final class TooltipFactory {

    private TooltipFactory() {
    }

    public static Tooltip create(String text) {
        Tooltip tooltip = new Tooltip(text == null ? "" : text);
        tooltip.setShowDelay(UiTiming.TOOLTIP_SHOW_DELAY);
        tooltip.setShowDuration(UiTiming.TOOLTIP_VISIBLE_DURATION);
        tooltip.setHideDelay(UiTiming.TOOLTIP_HIDE_DELAY);
        return tooltip;
    }

    public static void install(Control control, String text) {
        if (control == null) {
            throw new IllegalArgumentException(
                    "control must not be null"
            );
        }

        control.setTooltip(create(text));
    }
}
