package com.example.neuronmap.util;

import javafx.util.Duration;

/** Central timing values for short-lived JavaFX UI feedback. */
public final class UiTiming {

    public static final Duration TOOLTIP_SHOW_DELAY =
            Duration.ZERO;
    public static final Duration TOOLTIP_VISIBLE_DURATION =
            Duration.seconds(3);
    public static final Duration TOOLTIP_HIDE_DELAY =
            Duration.ZERO;

    public static final Duration STATUS_MESSAGE_VISIBLE_DURATION =
            Duration.seconds(5);
    public static final Duration STATUS_MESSAGE_FADE_DURATION =
            Duration.seconds(1);

    private UiTiming() {
    }
}
